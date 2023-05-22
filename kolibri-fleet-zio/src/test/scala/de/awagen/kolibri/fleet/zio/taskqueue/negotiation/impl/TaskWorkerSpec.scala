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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.Worker
import zio.Scope
import zio.test.junit.JUnitRunnableSpec
import zio.test._

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

  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TaskWorkerSpec")(

    test("run method executes batch and aggregates results") {
      // given
      val worker: Worker = TaskWorker()
      val batchAggregationInfo = BatchAggregationInfo(
        Left(NamedClassTyped[Unit]("DONE_WAITING")),
        () => TestObjects.countingAggregator(0)
      )
      val batchAggregationInfoResultAsProcessingMessage = BatchAggregationInfo(
        Right(NamedClassTyped[ProcessingMessage[Unit]]("DONE_WAITING")),
        () => TestObjects.countingAggregator(0)
      )
      val jobBatch: JobBatch[Int, Unit, Int] = JobBatch(
        JobDefinitions.simpleWaitJob("testJob1", 10, 100, elementsPerBatch = 10,
          aggregationInfoOpt = Some(batchAggregationInfo)),
        1
      )
      val jobBatchResultAsProcessingMessage: JobBatch[Int, Unit, Int] = JobBatch(
        JobDefinitions.simpleWaitJobResultAsProcessingMessage("testJob1", 10, 100, elementsPerBatch = 10,
          aggregationInfoOpt = Some(batchAggregationInfoResultAsProcessingMessage)),
        1
      )
      // when, then
      for {
        executionResult <- worker.work(jobBatch)
        executionResultAsProcessingMessage <- worker.work(jobBatchResultAsProcessingMessage)
      } yield assert((executionResult.aggregation, executionResultAsProcessingMessage.aggregation))(
        Assertion.assertion("both aggregations must contain all elements")(x => x._1 == 10 && x._2 == 10)
      )}
  )
}
