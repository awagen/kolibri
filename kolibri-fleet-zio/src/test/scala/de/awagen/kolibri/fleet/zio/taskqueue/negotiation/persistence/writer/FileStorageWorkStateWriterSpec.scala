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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingInfo, ProcessingState, ProcessingStatus}
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, workStateWriter}
import spray.json._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.Mockito.{times, verify}
import zio.Scope
import zio.test._

object FileStorageWorkStateWriterSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageWorkStateWriterSpec")(

    test("delete persisted in-progress state for batch") {
      // given
      val writer = fileWriterMock
      val stateWriter = workStateWriter(writer)
      val jobName = "testJob1"
      val batchNr = 1
      val nodeHash = AppProperties.config.node_hash
      // when, then
      for {
        _ <- stateWriter.deleteInProgressState(ProcessId(jobName, batchNr))
      } yield assert({
        verify(writer, times(1)).delete(
          ArgumentMatchers.eq(s"jobs/open/$jobName/tasks/inprogress_state/$nodeHash/$batchNr")
        )
      })(Assertion.assertion("true")(_ => true))
    },

    test("write batch to done") {
      // given
      val writer = fileWriterMock
      val stateWriter = workStateWriter(writer)
      val jobName = "testJob1"
      val batchNr = 1
      // when, then
      for {
        _ <- stateWriter.writeToDone(ProcessId(jobName, batchNr))
      } yield assert({
        verify(writer, times(1)).write(
          ArgumentMatchers.eq(""),
          ArgumentMatchers.eq(s"jobs/open/$jobName/tasks/done/$batchNr")
        )
      })(Assertion.assertion("true")(_ => true))
    },

    test("update in-progress state") {
      // given
      val writer = fileWriterMock
      val stateWriter = workStateWriter(writer)
      val jobName = "testJob1"
      val batchNr = 1
      val nodeHash = AppProperties.config.node_hash
      val state = ProcessingState(
        ProcessId(jobName, batchNr),
        ProcessingInfo(
          ProcessingStatus.QUEUED,
          100,
          0,
          AppProperties.config.node_hash,
          "2023-01-01 01:02:03"
        )
      )
      val writeContentCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      // when, then
      for {
        _ <- stateWriter.updateInProgressState(state)
      } yield assert({
        verify(writer, times(1)).write(
          writeContentCaptor.capture(),
          ArgumentMatchers.eq(s"jobs/open/$jobName/tasks/inprogress_state/$nodeHash/$batchNr")
        )
        writeContentCaptor.getValue
      })(Assertion.assertion("written state corresponds to state object")(writeContent => {
        val writtenState = writeContent.parseJson.convertTo[ProcessingState]
        writtenState == state
      }))
    }

  )

}
