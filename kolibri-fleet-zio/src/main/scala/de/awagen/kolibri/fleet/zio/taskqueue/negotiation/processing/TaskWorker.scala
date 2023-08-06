/**
 * Copyright 2023 Andreas Wagenmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing

import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.tagging.{TaggedWithType, Tags}
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators._
import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages._
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.FailedByException
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.maxParallelItemsPerBatch
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.execution.{ExecutionState, Failed, ZIOSimpleTaskExecution}
import de.awagen.kolibri.fleet.zio.metrics.Metrics
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.storage.io.writer.Writers
import zio.stream.ZStream
import zio.{Fiber, Ref, Task, UIO, ZIO}

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._


/**
 * Worker object providing methods to provide tuple of aggregator reflecting the aggregation / job execution
 * state so far and the Fiber executing it. This allows picking intermediate states / info about processing
 * state from aggregator and interruption of the processing, e.g in case the job was marked to be stopped
 * on the current instance (or all).
 */
object TaskWorker extends Worker {

  val INITIAL_DATA_KEY = "INIT_DATA"

  /**
   * Load the resources needed by the batch into global node state. Note that multiple requests for the same
   * resource will only lead to a single call to actually load the data.
   */
  private[processing] def prepareGlobalResources(directives: Seq[ResourceDirective[_]]): Task[Unit] = {
    for {
      executor <- ZIO.executor
      _ <- ZStream.fromIterable(directives)
        .mapZIO(directive => {
          implicit val exc: ExecutionContext = executor.asExecutionContext
          ZIO.fromPromiseScala(NodeResourceProvider.createResource(directive))
        })
        .runDrain
    } yield ()
  }

  override def work[T: TypeTag, V: TypeTag, W <: WithCount](jobBatch: JobBatch[T, V, W])(implicit tag: TypeTag[W]): Task[(Ref[Aggregator[TaggedWithType with DataPoint[V], W]], Fiber.Runtime[Throwable, Unit])] = {
    val aggregator: Aggregator[TaggedWithType with DataPoint[V], W] = jobBatch.job.aggregationInfo.batchAggregatorSupplier()
    val batchResultWriter: Writers.Writer[W, Tags.Tag, Any] = jobBatch.job.aggregationInfo.writer
    val successKey: String = jobBatch.job.aggregationInfo.successKey

    val resultComputeEffect: ZStream[Any, Throwable, Either[Throwable, (WeaklyTypedMap[String], Seq[ExecutionState])]] = for {
      computeResult <- ZStream.fromIterable(jobBatch.job.batches.get(jobBatch.batchNr).get.data)
        .mapZIOParUnordered(maxParallelItemsPerBatch)(dataPoint =>
          for {
            _ <- ZIO.logDebug(s"trying to process data point: $dataPoint")
            mapStore <- ZIO.attempt({
              val map = BaseWeaklyTypedMap.empty
              map.put(INITIAL_DATA_KEY, dataPoint)
              map
            })
            _ <- ZIO.logDebug(s"value map: $mapStore")
            _ <- ZIO.logDebug(s"value under data key: ${mapStore.get[T](INITIAL_DATA_KEY)}")
            _ <- ZIO.when(mapStore.get[T](INITIAL_DATA_KEY).isEmpty)(
              ZIO.logWarning(s"There is a type mismatch of data point and data key, processing the element '$dataPoint' will not work")
            )
            result <- ZIOSimpleTaskExecution(
              mapStore,
              jobBatch.job.taskSequence
            )
              // we explicitly lift possible errors to an Either, to avoid the
              // stream from stopping. The distinct cases can then separately be
              // aggregated
              .processAllTasks.either
          } yield result
        )
    } yield computeResult

    val computeAndAggregateEffect = for {
      aggregatorRef <- Ref.make(aggregator)
      // effect for actual processing of items
      computeResultFiber <- resultComputeEffect
        .mapZIO(element => {
          ZIO.succeed(element) @@ Metrics.CalculationsWithMetrics.countFlowElements("requestResultsQueue", in = false)
        })
        // aggregation step
        .mapZIOParUnordered(8)(element =>
          for {
            // aggregate update step
            _ <- element match {
              case Left(e) => ZIO.succeed({
                val failType = FailedByException(e)
                aggregatorRef.update(x => x.add(BadCorn(failType)))
              })
              case Right(v) =>
                ZIO.logDebug(s"task processing succeeded, map: ${v._1}") *>
                  // actually perform the aggregation
                  (for {
                    failedTask <- ZIO.attempt(v._2.find(x => x.isInstanceOf[Failed]).map(x => x.asInstanceOf[Failed]))
                    _ <- ZIO.ifZIO(ZIO.succeed(failedTask.nonEmpty))(
                      // if any of the tasks failed, we aggregate it is part of the failure aggregation
                      onTrue = {
                        ZIO.logDebug(s"Aggregating fail item: ${failedTask.get.taskFailType}") *>
                          aggregatorRef.update(x => x.add(BadCorn(failedTask.get.taskFailType)))
                      },
                      // if nothing failed, we just normally consume the result
                      onFalse = {
                        val computedValueOpt: Option[ProcessingMessage[V]] = v._1.get[ProcessingMessage[V]](successKey)
                        val aggregationEffect: UIO[Unit] = computedValueOpt match {
                          case Some(value) => aggregatorRef.update(x => x.add(value)) *>
                            ZIO.logDebug(s"Aggregating success: ${v._1.get(successKey)}")
                          case None =>
                            ZIO.logWarning(s"no fail key, but missing success key '$successKey'") *>
                              aggregatorRef.update(x => x.add(ProcessingMessages.BadCorn(TaskFailType.FailedByException(new RuntimeException(s"Missing result key: $successKey")))))
                        }
                        aggregationEffect
                      }
                    )
                  } yield ())
                    .onError(throwable => ZIO.logWarning(s"aggregation failed: $throwable"))
            }
            updatedAggregator <- aggregatorRef.get
            _ <- ZIO.logDebug(s"updated aggregator state: ${updatedAggregator.aggregation}")

          } yield ())
        .runDrain
        // when we are done, write the result
        .onExit(
          _ => for {
            agg <- aggregatorRef.get
            _ <- ZIO.logDebug(s"final aggregation state: ${agg.aggregation}")
            _ <- ZIO.attemptBlockingIO({
              batchResultWriter.write(agg.aggregation, StringTag(jobBatch.job.jobName))
            }).either
          } yield ()
        )
        .fork
    } yield (aggregatorRef, computeResultFiber)

    // if this fails, we cannot continue properly, thus we can return the aggregator with the proper fail type and a finished Fiber.Runtime
    val resourceSetupEffect = prepareGlobalResources(jobBatch.job.resourceSetup).either

    // two-step effect: setting up global resources, if successful compute and aggregate results
    for {
      resourceSetupResult <- resourceSetupEffect
      result <- resourceSetupResult match {
        case Left(throwable) =>
          ZIO.logWarning(s"""Loading global resources for batch failed, terminating further batch execution for job '${jobBatch.job.jobName}' and batch '${jobBatch.batchNr}'\nMsg: ${throwable.getMessage}\nTrace:${throwable.getStackTrace.mkString("\n")}""") *>
            (for {
              aggregatorRef <- Ref.make(aggregator)
              // create a fiber and terminate it so we can pass it within the expected return type that will
              // indicate finish state for the batch
              fiber <- ZIO.never.fork
              _ <- fiber.interrupt
            } yield (aggregatorRef, fiber))
        case Right(()) =>
          computeAndAggregateEffect
      }
    } yield result

  }

}
