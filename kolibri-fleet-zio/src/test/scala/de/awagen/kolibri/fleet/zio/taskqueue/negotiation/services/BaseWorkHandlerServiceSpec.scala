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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services

import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.ValueWithCount
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.countingAggregator
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{SnapshotSample1, fileWriterMock, workHandler}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import spray.json._
import zio.test._
import zio.{Ref, Scope, ZIO}

object BaseWorkHandlerServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("BaseWorkHandlerServiceSpec")(

    // TODO: test criteria besides pure execution
    test("manage batches") {
      val writer = fileWriterMock
      for {
        handler <- workHandler[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]](writer)
        _ <- handler.manageBatches(SnapshotSample1.openJobsSnapshot)
      } yield ()
      assertTrue(true)
    },

    test("update process states") {
      // given
      val writer = fileWriterMock
      val jobId = "testJob1_3434839787"
      val nodeHash = AppProperties.config.node_hash
      val batchNr = 0
      val processId = ProcessId(jobId, batchNr)
      val addedBatchesHistoryInitState = Seq(processId)
      val aggregatorState: Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = countingAggregator(0, 0)
      val expectedProcessingState = ProcessingState(
        ProcessId(
          "testJob1_3434839787",
          0
        ),
        ProcessingInfo(
          ProcessingStatus.DONE,
          100,
          0,
          "abc234",
          ProcessingStateUtils.timeInMillisToFormattedTime(1703845333850L)
        )
      )
      // when, then
      for {
        aggregatorRef <- Ref.make(aggregatorState)
        fiber <- ZIO.attemptBlocking({
          Thread.sleep(1)
        }).fork
        // putting fiber state to DONE
        _ <- fiber.join
        handler <- workHandler(
          writer,
          addedBatchesHistoryInitState = addedBatchesHistoryInitState,
          processIdToAggregatorMappingInitState = Map(processId -> aggregatorRef),
          processIdToFiberMappingInitState = Map(processId -> fiber)
        )
        _ <- handler.updateProcessStates
      } yield assert({
        val processingStateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        verify(writer, times(1)).write(
          processingStateCaptor.capture(),
          ArgumentMatchers.eq(s"jobs/open/$jobId/tasks/inprogress_state/$nodeHash/$batchNr")
        )
        processingStateCaptor.getValue
      })(Assertion.assertion("processing state must be correct")(stateStr => {
        val processingState = stateStr.parseJson.convertTo[ProcessingState]
        processingState == expectedProcessingState.copy(processingInfo = expectedProcessingState.processingInfo.copy(lastUpdate = processingState.processingInfo.lastUpdate))
      }))
    }

  )

}
