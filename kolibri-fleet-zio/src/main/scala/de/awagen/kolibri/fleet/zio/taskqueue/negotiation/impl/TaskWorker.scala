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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl

import de.awagen.kolibri.datatypes.immutable.stores.TypedMapStore
import de.awagen.kolibri.datatypes.tagging.{TaggedWithType, Tags}
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.types.{ClassTyped, NamedClassTyped}
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators._
import de.awagen.kolibri.definitions.processing.ProcessingMessages._
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.FailedByException
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.maxParallelItemsPerBatch
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.execution.{Failed, JobDefinitions, ZIOSimpleTaskExecution}
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.Worker
import de.awagen.kolibri.storage.io.writer.Writers
import zio.stream.ZStream
import zio.{Fiber, Ref, ZIO}
import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._

object TaskWorker extends Worker {

  val INITIAL_DATA_KEY = "INIT_DATA"

  def inactiveAggregator[V: TypeTag, W: TypeTag]: Aggregator[TaggedWithType with DataPoint[V], W] = new Aggregator[TaggedWithType with DataPoint[V], W] {
    override def add(sample: TaggedWithType with DataPoint[V]): Aggregator[TaggedWithType with DataPoint[V], W] = inactiveAggregator

    override def aggregation: W = null.asInstanceOf[W]

    override def addAggregate(aggregatedValue: W): Aggregator[TaggedWithType with DataPoint[V], W] = inactiveAggregator
  }

  override def work[T: TypeTag, V: TypeTag, W: TypeTag](jobBatch: JobBatch[T, V, W]): ZIO[Any, Nothing, (Ref[Aggregator[TaggedWithType with DataPoint[V], W]], Fiber.Runtime[Nothing, Unit])] = {
    val aggregator: Aggregator[TaggedWithType with DataPoint[V], W] = jobBatch.job.aggregationInfo
      .map(x => x.batchAggregatorSupplier()).getOrElse(inactiveAggregator[V, W])
    val batchResultWriter: Writers.Writer[W, Tags.Tag, Any] = jobBatch.job.aggregationInfo
      .map(x => x.writer).getOrElse(JobDefinitions.doNothingWriter[W])
    val successKey: Option[ClassTyped[Any]] = jobBatch.job.aggregationInfo.map(x => x.successKey match {
      case Left(value) => value
      case Right(value) => value
    })
    for {
      executor <- ZIO.executor
      // TODO: handle failed resource loading better
      _ <- ZStream.fromIterable(jobBatch.job.resourceSetup)
        .mapZIO(directive => {
          implicit val exc: ExecutionContext = executor.asExecutionContext
          ZIO.fromPromiseScala(NodeResourceProvider.createResource(directive))
        })
        .runDrain.either
      aggregatorRef <- Ref.make(aggregator)
      computeResultFiber <- ZStream.fromIterable(jobBatch.job.batches.get(jobBatch.batchNr).get.data)
        .mapZIO(dataPoint =>
          ZIOSimpleTaskExecution(
            TypedMapStore(Map(NamedClassTyped[T](INITIAL_DATA_KEY) -> dataPoint)),
            jobBatch.job.taskSequence)
            // we explicitly lift possible errors to an Either, to avoid the
            // stream from stopping. The distinct cases can then separately be
            // aggregated
            .processAllTasks.either
        )
        .mapZIOParUnordered(maxParallelItemsPerBatch) {
          case Left(e) => ZIO.succeed({
            val failType = FailedByException(e)
            aggregatorRef.update(x => x.add(BadCorn(failType)))
          })
          case Right(v) =>
            ZIO.log(s"task processing succeeded, map: ${v._1}") *>
              // actually perform the aggregation
              ZIO.ifZIO(ZIO.succeed(successKey.nonEmpty))(
                onTrue = {
                  val failedTask: Option[Failed] = v._2.find(x => x.isInstanceOf[Failed]).map(x => x.asInstanceOf[Failed])
                  // if any of the tasks failed, we aggregate it is part of the failure aggregation
                  if (failedTask.nonEmpty) {
                    aggregatorRef.update(x => x.add(BadCorn(failedTask.get.taskFailType)))
                  }
                  // if nothing failed, we just normally consume the result
                  else {
                    v._1.get(successKey.get).get match {
                      case sample: ProcessingMessage[V] =>
                        aggregatorRef.update(x => x.add(sample))
                      case value =>
                        aggregatorRef.update(x => x.add(Corn(value.asInstanceOf[V])))
                    }
                  }
                },
                onFalse = ZIO.logDebug("Not aggregating")
              )
        }
        .runDrain
        // when we are done, write the result
        .onExit(
          _ => {
            aggregatorRef.get.flatMap(aggregator => {
              ZIO.attemptBlockingIO({
                batchResultWriter.write(aggregator.aggregation, StringTag(jobBatch.job.jobName))
              }).either
            })
          }
        )
        .fork
    } yield (aggregatorRef, computeResultFiber)
  }

}
