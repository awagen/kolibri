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

import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.Aggregator
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{BatchAggregationInfo, JobBatch}
import zio.Scope
import zio.test._
import zio.test.junit.JUnitRunnableSpec

class TaskWorkerSpec extends JUnitRunnableSpec {

  object TestObjects {

    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

    def countingAggregator(count: Int): Aggregator[ProcessingMessage[Unit], Int] = new Aggregator[ProcessingMessage[Unit], Int] {
      override def add(sample: ProcessingMessage[Unit]): Aggregator[ProcessingMessage[Unit], Int] = {
        countingAggregator(count + 1)
      }

      override def aggregation: Int = count

      override def addAggregate(aggregatedValue: Int): Aggregator[ProcessingMessage[Unit], Int] = {
        countingAggregator(count + aggregatedValue)
      }
    }

    // aggregation info assuming plain result
    val batchAggregationInfo = BatchAggregationInfo(
      Left(NamedClassTyped[Unit]("DONE_WAITING")),
      () => TestObjects.countingAggregator(0)
    )
    // aggregation info assuming result wrapped in ProcessingMessage
    val batchAggregationInfoResultAsProcessingMessage = BatchAggregationInfo(
      Right(NamedClassTyped[ProcessingMessage[Unit]]("DONE_WAITING")),
      () => TestObjects.countingAggregator(0)
    )
    // job definition where the result is plain Unit
    val jobBatch: JobBatch[Int, Unit, Int] = JobBatch(
      JobDefinitions.simpleWaitJob("testJob1", 10, 100, elementsPerBatch = 10,
        aggregationInfoOpt = Some(batchAggregationInfo)),
      1
    )
    // job definition where result is ProcessingMessage[Unit]
    val jobBatchResultAsProcessingMessage: JobBatch[Int, Unit, Int] = JobBatch(
      JobDefinitions.simpleWaitJobResultAsProcessingMessage("testJob1", 10, 100, elementsPerBatch = 10,
        aggregationInfoOpt = Some(batchAggregationInfoResultAsProcessingMessage)),
      1
    )

    val jobBatchWithoutAggregation: JobBatch[Int, Unit, Int] = JobBatch(
      JobDefinitions.simpleWaitJob("testJob1", 10, 100, elementsPerBatch = 10,
        aggregationInfoOpt = None),
      1
    )

  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TaskWorkerSpec")(

    test("run method executes batch and aggregates results") {
      // given, when, then
      for {
        workRawResult <- TaskWorker.work(TestObjects.jobBatch)
        workProcessingMessageResult <- TaskWorker.work(TestObjects.jobBatchResultAsProcessingMessage)
        // join is needed here to make sure the fiber completed the execution
        _ <- workRawResult._2.join
        _ <- workProcessingMessageResult._2.join
        aggregator <- workRawResult._1.get
        aggregatorForResultAsProcessingMessage <- workProcessingMessageResult._1.get
      } yield assert((aggregator.aggregation, aggregatorForResultAsProcessingMessage.aggregation))(
        Assertion.assertion("both aggregations must contain all elements")(x => x._1 == 10 && x._2 == 10)
      )
    },

    test("must also execute if no aggregator defined") {
      // given, when, then
      for {
        work <- TaskWorker.work(TestObjects.jobBatchWithoutAggregation)
        _ <- work._2.join
        aggregator <- work._1.get
      } yield assert(aggregator.aggregation)(Assertion.equalTo(0))
    }
  )
}
