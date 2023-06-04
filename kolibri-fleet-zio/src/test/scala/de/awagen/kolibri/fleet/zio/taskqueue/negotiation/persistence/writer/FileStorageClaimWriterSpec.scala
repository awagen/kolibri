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

import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{claimWriter, fileWriterMock}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import spray.json._
import zio.Scope
import zio.test._

object FileStorageClaimWriterSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageClaimWriterSpec")(

    test("fileBatchClaim") {
      val writerMock = fileWriterMock
      val writer = claimWriter(writerMock)
      for {
        _ <- writer.fileBatchClaim("testJob1_3434839787", 1, ClaimTopic.JOB_TASK_PROCESSING_CLAIM, Set.empty)
        _ <- writer.fileBatchClaim("testJob1_3434839787", 2, ClaimTopic.JOB_TASK_PROCESSING_CLAIM, Set.empty)
        _ <- writer.fileBatchClaim("testJob1_3434839787", 3, ClaimTopic.JOB_TASK_PROCESSING_CLAIM,
          Set(ClaimStates.Claim("testJob1_3434839787", 3, "uuu1", 1683845333850L, ClaimTopic.JOB_TASK_PROCESSING_CLAIM))
        )
      } yield assert({
        verify(writerMock, times(1)).write(
          ArgumentMatchers.eq(""),
          ArgumentMatchers.startsWith(s"jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__1__"),
        )
        verify(writerMock, times(1)).write(
          ArgumentMatchers.eq(""),
          ArgumentMatchers.startsWith(s"jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__2__"),
        )
        verify(writerMock, times(0)).write(
          ArgumentMatchers.eq(""),
          ArgumentMatchers.startsWith(s"jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__3__"),
        )
      })(Assertion.assertion("all true")(_ => true))
    },

    test("exerciseBatchClaim") {
      val writerMock = fileWriterMock
      val writer = claimWriter(writerMock)
      val expectedProcessingState = ProcessingState(
        ProcessId(
          "testJob1_3434839787",
          2
        ),
        ProcessingInfo(
          ProcessingStatus.PLANNED,
          0,
          0,
          "abc234",
          ProcessingStateUtils.timeInMillisToFormattedTime(1703845333850L)
        )
      )
      for {
        _ <- writer.exerciseBatchClaim("testJob1_3434839787", 2, Set(
          Claim("testJob1_3434839787", 2, "abc234", 1683845333850L, ClaimTopic.JOB_TASK_PROCESSING_CLAIM),
          Claim("testJob1_3434839787", 2, "other1", 1703845333850L, ClaimTopic.JOB_TASK_PROCESSING_CLAIM),
          Claim("testJob1_3434839787", 2, "other2", 1713845333850L, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
        ))
      } yield assert({
        val deleteCmdFileCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        val processingStateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        verify(writerMock, times(1))
          .write(
            processingStateCaptor.capture(),
            ArgumentMatchers.eq("jobs/open/testJob1_3434839787/tasks/inprogress_state/abc234/2")
          )
        verify(writerMock, times(4))
          .delete(
            deleteCmdFileCaptor.capture()
          )
        (deleteCmdFileCaptor.getAllValues.toArray.toSeq.asInstanceOf[Seq[String]],
          processingStateCaptor.getValue)
      })(Assertion.assertion("deletion called on correct paths")(x => {
        val fileSeq = x._1
        val processingStateContent = x._2.parseJson.convertTo[ProcessingState]
        val slice = fileSeq.slice(1, 3)
        fileSeq.head == "jobs/open/testJob1_3434839787/tasks/open/2" &&
          slice.toSet == Set(
            "jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__2__1703845333850__other1",
            "jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__2__1713845333850__other2"
          ) &&
          fileSeq.last == "jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__2__1683845333850__abc234" &&
          processingStateContent == expectedProcessingState.copy(processingInfo = expectedProcessingState.processingInfo.copy(lastUpdate = processingStateContent.processingInfo.lastUpdate))
      }))
    },

  )

}
