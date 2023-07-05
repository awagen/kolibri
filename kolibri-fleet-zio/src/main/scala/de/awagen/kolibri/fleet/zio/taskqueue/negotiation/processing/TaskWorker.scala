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

import de.awagen.kolibri.datatypes.immutable.stores.TypedMapStore
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.tagging.{TaggedWithType, Tags}
import de.awagen.kolibri.datatypes.types.{ClassTyped, NamedClassTyped}
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
import de.awagen.kolibri.fleet.zio.execution.{Failed, ZIOSimpleTaskExecution}
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
 *
 * TODO: might wanna make it a Cancellable, e.g include some atomic reference to some "processing"-flag,
 * which can be used as killswitch (e.g setting to false)
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
    val successKey: ClassTyped[ProcessingMessage[V]] = jobBatch.job.aggregationInfo.successKey
    for {
      _ <- prepareGlobalResources(jobBatch.job.resourceSetup)
      aggregatorRef <- Ref.make(aggregator)
      computeResultFiber <- ZStream.fromIterable(jobBatch.job.batches.get(jobBatch.batchNr).get.data)
        .mapZIO(dataPoint =>
          for {
            _ <- ZIO.logDebug(s"trying to process data point: $dataPoint")
            dataKey <- ZIO.succeed(NamedClassTyped[T](INITIAL_DATA_KEY))
            mapStore <- ZIO.attempt({
              TypedMapStore(Map(dataKey -> dataPoint))
            })
            _ <- ZIO.logDebug(s"value map: $mapStore")
            _ <- ZIO.logDebug(s"value under data key: ${mapStore.get(dataKey)}")
            _ <- ZIO.when(mapStore.get(dataKey).isEmpty)(
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
        .mapZIOParUnordered(maxParallelItemsPerBatch)(element =>
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
                        ZIO.logWarning(s"Aggregating fail item: ${failedTask.get.taskFailType}") *>
                          aggregatorRef.update(x => x.add(BadCorn(failedTask.get.taskFailType)))
                      },
                      // if nothing failed, we just normally consume the result
                      onFalse = {
                        val computedValueOpt: Option[ProcessingMessage[V]] = v._1.get(successKey)
                        val aggregationEffect: UIO[Unit] = computedValueOpt match {
                          case Some(value) => aggregatorRef.update(x => x.add(value)) *>
                            ZIO.logDebug(s"Aggregating success: ${v._1.get(successKey)}")
                          case None =>
                            ZIO.logWarning(s"no fail key, but missing success key '$successKey'") *>
                              aggregatorRef.update(x => x.add(ProcessingMessages.BadCorn(TaskFailType.MissingResultKey(successKey))))
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
  }

}
