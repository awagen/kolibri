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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessingState, ProcessingStateUtils, ProcessingStatus}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, jobStateHandler}
import de.awagen.kolibri.storage.io.reader.{LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import spray.json._
import zio.test._
import zio.{Queue, Scope}

object FileStorageClaimHandlerSpec extends ZIOSpecDefault {

  object TestObjects {

    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

    def claimHandler(writer: FileWriter[String, Unit], baseFolder: String): FileStorageClaimHandler = FileStorageClaimHandler(
      filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
      writer,
      LocalResourceFileReader(
        basePath = baseResourceFolder,
        delimiterAndPosition = None,
        fromClassPath = false
      ),
      jobStateHandler(writer, baseFolder)
    )

  }

  import TestObjects._

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageClaimHandlerSpec")(

    test("fileBatchClaim") {
      val writerMock = fileWriterMock
      val claimH: FileStorageClaimHandler = claimHandler(writerMock, baseResourceFolder)
      for {
        _ <- claimH.fileBatchClaim("testJob1_3434839787", 1, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
        _ <- claimH.fileBatchClaim("testJob1_3434839787", 2, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
      } yield assert({
        verify(writerMock, times(1)).write(
          ArgumentMatchers.eq(""),
          ArgumentMatchers.startsWith(s"jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__1__"),
        )
        verify(writerMock, times(0)).write(
          ArgumentMatchers.eq(""),
          ArgumentMatchers.startsWith(s"jobs/open/testJob1_3434839787/tasks/claims/JOB_TASK_PROCESSING_CLAIM__testJob1_3434839787__2__"),
        )
      })(Assertion.assertion("all true")(_ => true))
    },

    test("verifyBatchClaim") {
      val writerMock = fileWriterMock
      val claimH = claimHandler(writerMock, baseResourceFolder)
      for {
        claimResult1 <- claimH.verifyBatchClaim("testJob1_3434839787", 2, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
        claimResult2 <- claimH.verifyBatchClaim("testJob1_3434839787", 3, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
      } yield assert(claimResult1)(Assertion.equalTo(ClaimVerifyStatus.CLAIM_ACCEPTED)) &&
        assert(claimResult2)(Assertion.equalTo(ClaimVerifyStatus.NODE_CLAIM_DOES_NOT_EXIST))
    },

    test("exerciseBatchClaim") {
      val writerMock = fileWriterMock
      val claimH = claimHandler(writerMock, baseResourceFolder)
      val expectedProcessingState = ProcessingState(
        "testJob1_3434839787",
        2,
        ProcessingStatus.PLANNED,
        0,
        0,
        "abc234",
        ProcessingStateUtils.timeInMillisToFormattedTime(1703845333850L)
      )
      for {
        _ <- claimH.exerciseBatchClaim("testJob1_3434839787", 2, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
      } yield assert({
        val deleteCmdFileCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        val processingStateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        verify(writerMock, times(1))
          .write(
            processingStateCaptor.capture(),
            ArgumentMatchers.eq("jobs/open/testJob1_3434839787/tasks/inprogress_state/abc234/2__PLANNED")
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
          processingStateContent == expectedProcessingState.copy(lastUpdate = processingStateContent.lastUpdate)
      }))
    },

    test("manageClaims") {
      val writerMock = fileWriterMock
      val claimH = claimHandler(writerMock, baseResourceFolder)
      for {
        queue <- Queue.bounded[JobBatch[_, _, _]](2)
        _ <- claimH.manageClaims(ClaimTopic.JOB_TASK_PROCESSING_CLAIM, queue)
      } yield assert(true)(Assertion.assertion("true")(_ => true))
    }

  )
}
