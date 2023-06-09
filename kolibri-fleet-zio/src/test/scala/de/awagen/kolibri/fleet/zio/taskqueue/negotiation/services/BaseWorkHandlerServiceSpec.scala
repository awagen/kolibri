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
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{JobBatch, ValueWithCount}
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.countingAggregator
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import de.awagen.kolibri.fleet.zio.testutils.TestObjects
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{SnapshotSample1, fileWriterMock, workHandler}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import spray.json._
import zio.test._
import zio.{Ref, Scope, ZIO}

object BaseWorkHandlerServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("BaseWorkHandlerServiceSpec")(

    test("manage batches") {
      val writer = fileWriterMock
      val jobId = "testJob1_3434839787"
      val processId1 = ProcessId(jobId, 0)
      val processId2 = ProcessId(jobId, 1)
      val processId3 = ProcessId(jobId, 2)
      // only add processId1 and processId3 to history state, since processId2 refers to batch still in
      // planned state and thus still needs to be added to the queue for processing
      val addedBatchesHistoryInitState = Seq(processId1, processId3)
      val aggregatorState1: Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = countingAggregator(0, 0)
      val aggregatorState2: Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = countingAggregator(0, 0)
      val aggregatorState3: Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = countingAggregator(0, 0)
      for {
        aggregatorRef1 <- Ref.make(aggregatorState1)
        aggregatorRef2 <- Ref.make(aggregatorState2)
        aggregatorRef3 <- Ref.make(aggregatorState3)
        fiber1 <- ZIO.attemptBlockingInterrupt({
          Thread.sleep(10000)
        })
          .onInterrupt(ZIO.logInfo("fiber1 interrupted"))
          .fork
        fiber2 <- ZIO.attemptBlockingInterrupt({
          Thread.sleep(10000)
        })
          .onInterrupt(ZIO.logInfo("fiber2 interrupted"))
          .fork
        fiber3 <- ZIO.attemptBlockingInterrupt({
          Thread.sleep(10000)
        })
          .onInterrupt(ZIO.logInfo("fiber3 interrupted"))
          .fork
        processToFiberMapping <- ZIO.succeed(Map(processId1 -> fiber1, processId2 -> fiber2, processId3 -> fiber3))
        handler <- workHandler[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]](
          writer,
          jobBatchQueueSize = 5,
          initialQueueContent = Seq(
            JobBatch(
              TestObjects.SnapshotSample1.jobDef, 0
            )
          ),
          addedBatchesHistoryInitState = addedBatchesHistoryInitState,
          processIdToAggregatorMappingInitState = Map(processId1 -> aggregatorRef1, processId2 -> aggregatorRef2, processId3 -> aggregatorRef3),
          processIdToFiberMappingInitState = processToFiberMapping
        )
        _ <- handler.manageBatches(SnapshotSample1.openJobsSnapshot)
        updatedProcessIdToFiberMapping <- handler.processIdToFiberRef.get
        nextQueueElement <- handler.queue.take
      } yield assert({
        val updateFileContentCaptor1: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        verify(writer, times(2))
          .write(
            updateFileContentCaptor1.capture(),
            ArgumentMatchers.eq(s"jobs/open/$jobId/tasks/inprogress_state/${AppProperties.config.node_hash}/0")
          )
        val updateFileContentCaptor2: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        verify(writer, times(2))
          .write(
            updateFileContentCaptor2.capture(),
            ArgumentMatchers.eq(s"jobs/open/$jobId/tasks/inprogress_state/${AppProperties.config.node_hash}/1")
          )
        (updateFileContentCaptor1.getAllValues, updateFileContentCaptor2.getAllValues)
      })(Assertion.assertion("true")(writeValues => {
        val batch0StateChange1 = writeValues._1.get(0).parseJson.convertTo[ProcessingState]
        val batch0StateChange2 = writeValues._1.get(1).parseJson.convertTo[ProcessingState]
        val batch1StateChange1 = writeValues._2.get(0).parseJson.convertTo[ProcessingState]
        val batch1StateChange2 = writeValues._2.get(1).parseJson.convertTo[ProcessingState]
        batch0StateChange1 == TestObjects.copyProcessingStateWithAdjustedTimeAndState(
          TestObjects.processingState1, batch0StateChange1.processingInfo.lastUpdate, ProcessingStatus.QUEUED
        ) &&
          batch0StateChange2 == TestObjects.copyProcessingStateWithAdjustedTimeAndState(
            TestObjects.processingState1, batch0StateChange2.processingInfo.lastUpdate, ProcessingStatus.IN_PROGRESS
          ) &&
          batch1StateChange1 == TestObjects.copyProcessingStateWithAdjustedTimeAndState(
            TestObjects.processingState2, batch1StateChange1.processingInfo.lastUpdate, ProcessingStatus.PLANNED
          ) &&
          batch1StateChange2 == TestObjects.copyProcessingStateWithAdjustedTimeAndState(
            TestObjects.processingState2, batch1StateChange2.processingInfo.lastUpdate, ProcessingStatus.QUEUED
          )
      })) &&
        assert(nextQueueElement)(Assertion.assertion("correct next queued element")(element => {
          element.batchNr == 1 && element.job.jobName == jobId
        })) &&
        assert(updatedProcessIdToFiberMapping)(Assertion.assertion("fiber mapping must be updated")(fiberMapping => {
          fiberMapping.keySet == Set(processId1, processId2)
        }))
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
      val expectedProcessingState = TestObjects.processingState1
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
