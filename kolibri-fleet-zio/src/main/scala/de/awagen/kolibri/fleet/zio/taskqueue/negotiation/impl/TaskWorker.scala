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
import de.awagen.kolibri.datatypes.types.{ClassTyped, NamedClassTyped}
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators._
import de.awagen.kolibri.definitions.processing.ProcessingMessages._
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.FailedByException
import de.awagen.kolibri.fleet.zio.execution.{Failed, ZIOSimpleTaskExecution}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.WorkStatus.WorkStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.Worker
import zio.stream.ZStream
import zio.{Task, ZIO}
import scala.reflect.runtime.universe._

object TaskWorker {

  val INITIAL_DATA_KEY = "INIT_DATA"

  def inactiveAggregator[W:TypeTag]: Aggregator[ProcessingMessage[Any], W] = new Aggregator[ProcessingMessage[Any], W]{
    override def add(sample: ProcessingMessage[Any]): Unit = ()

    override def aggregation: W = null.asInstanceOf[W]

    override def addAggregate(aggregatedValue: W): Unit = ()
  }

}

case class TaskWorker() extends Worker {

  import TaskWorker._

  /**
   * Processing a single batch and consuming the results in consumers as defined in the jobBatch
   * TODO: replace aggregator function with a supplier of an aggregator that takes ProcessingMessage
   * of given type. Exceptions can be wrapped in instance of Corn before aggregation.
   * This way we can initialize aggregator every time a batch result is computed
   *
   * TODO: consider using a ref here for the aggregation, so that we could also make use of parallel execution.
   * Note that right now the aggregators are mutable though, which is not the right fit for a Ref
   */
  override def work[T, W](jobBatch: JobBatch[T, W]): ZIO[Any, Nothing, Option[Aggregator[ProcessingMessage[Any], W]]] = {
    val aggregationInfoOpt: Option[Aggregator[ProcessingMessage[Any], W]] = jobBatch.job.aggregationInfo
      .map(x => x.batchAggregatorSupplier())
    val successKey: Option[ClassTyped[ProcessingMessage[Any]]] = jobBatch.job.aggregationInfo.map(x => x.successKey)
    ZStream.fromIterable(jobBatch.job.batches.get(jobBatch.batchNr).get.data)
      .mapZIO(dataPoint =>
        ZIOSimpleTaskExecution(
          TypedMapStore(Map(NamedClassTyped[Any](INITIAL_DATA_KEY) -> dataPoint)),
          jobBatch.job.taskSequence)
          // we explicitly lift possible errors to an Either, to avoid the
          // stream from stopping. The distinct cases can then separately be
          // aggregated
          .processAllTasks.either
      )
      // we only aggregate results if the aggregation info is set
      .mapZIO(result => ZIO.ifZIO(ZIO.succeed(aggregationInfoOpt.nonEmpty))(
        onTrue = result match {
          case Left(e) => ZIO.succeed({
            val failType = FailedByException(e)
            aggregationInfoOpt.get.add(BadCorn(failType))
          })
          case Right(v) =>
            ZIO.succeed({
              val failedTask: Option[Failed] = v._2.find(x => x.isInstanceOf[Failed]).map(x => x.asInstanceOf[Failed])
              // if any of the tasks failed, we aggregate it is part of the failure aggregation
              if (failedTask.nonEmpty) {
                aggregationInfoOpt.get.add(BadCorn(failedTask.get.taskFailType))
              }
              // if nothing failed, we just normally consume the result
              else {
                aggregationInfoOpt.get.add(v._1.get(successKey.get).get)
              }
            })
        },
        onFalse = ZIO.succeed(())
      ))
      .runDrain
      .map(_ => aggregationInfoOpt)

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
