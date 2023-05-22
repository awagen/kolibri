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
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators._
import de.awagen.kolibri.definitions.processing.ProcessingMessages._
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.FailedByException
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.maxParallelItemsPerBatch
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.execution.{Failed, ZIOSimpleTaskExecution}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.WorkStatus.WorkStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.Worker
import zio.stream.ZStream
import zio.{Ref, Task, ZIO}

import scala.reflect.runtime.universe._

object TaskWorker {

  val INITIAL_DATA_KEY = "INIT_DATA"

  def inactiveAggregator[V: TypeTag, W: TypeTag]: Aggregator[ProcessingMessage[V], W] = new Aggregator[ProcessingMessage[V], W] {
    override def add(sample: ProcessingMessage[V]): Aggregator[ProcessingMessage[V], W] = this

    override def aggregation: W = null.asInstanceOf[W]

    override def addAggregate(aggregatedValue: W): Aggregator[ProcessingMessage[V], W] = this
  }

}

case class TaskWorker() extends Worker {

  import TaskWorker._

  /**
   * Processing a single batch and consuming the results in consumers as defined in the jobBatch
   */
  override def work[T: TypeTag, V: TypeTag, W: TypeTag](jobBatch: JobBatch[T, V, W]): ZIO[Any, Nothing, Aggregator[ProcessingMessage[V], W]] = {
    val aggregator: Aggregator[ProcessingMessage[V], W] = jobBatch.job.aggregationInfo
      .map(x => x.batchAggregatorSupplier()).getOrElse(inactiveAggregator[V, W])
    val successKey = jobBatch.job.aggregationInfo.map(x => x.successKey match {
      case Left(value) => value
      case Right(value) => value
    })
    for {
      aggregatorRef <- Ref.make(aggregator)
      _ <- ZStream.fromIterable(jobBatch.job.batches.get(jobBatch.batchNr).get.data)
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
              {
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
              }
        }
        .runDrain
      aggregationResult <- aggregatorRef.get
    } yield aggregationResult

  }

  /**
   * Returns current work status for the given task
   * TODO: here would be good if we have some counter that identifies how many messages were already
   * aggregated by the aggregator and how many of those were failures vs successes so we can update the
   * status
   *
   * @return
   */
  override def status(): Task[WorkStatus] = ???
}
