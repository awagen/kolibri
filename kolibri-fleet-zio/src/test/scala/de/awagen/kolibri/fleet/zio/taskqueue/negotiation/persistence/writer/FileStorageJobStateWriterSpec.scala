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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.FileStorageJobStateWriterSpec.TestObjects.testJobDefinitionJson
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, jobStateWriter}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.{Scope, ZLayer}
import zio.http.Client
import zio.test._

object FileStorageJobStateWriterSpec extends ZIOSpecDefault {

  object TestObjects {

    val testJobDefinitionJson: String =
      """
        |{
        |  "type": "JUST_WAIT",
        |  "def": {
        |    "jobName": "waitingJob",
        |    "nrBatches": 10,
        |    "durationInMillis": 1000
        |  }
        |}
        |""".stripMargin

  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageJobStateWriterSpec")(

    test("moves job directory to done") {
      // given
      val writerMock = fileWriterMock
      val writer = jobStateWriter(writerMock)
      // when, then
      for {
        _ <- writer.moveToDone("testJob1_3434839787")
      } yield assert({
        verify(writerMock, times(1))
          .moveDirectory(
            ArgumentMatchers.eq("jobs/open/testJob1_3434839787"),
            ArgumentMatchers.eq("jobs/done")
          )
      })(Assertion.assertion("dummy true")(_ => true))
    },

    test("store job definition and batches") {
      val clientMock = mock[Client]
      val writerMock = fileWriterMock
      val stateWriter = jobStateWriter(writerMock)
      (for {
        _ <- stateWriter.storeJobDefinitionAndBatches(testJobDefinitionJson, s"waitingJob_${System.currentTimeMillis()}")
      } yield assert({
        // verify the writing of the job definition
        verify(writerMock, times(1))
          .write(
            ArgumentMatchers.eq(testJobDefinitionJson),
            ArgumentMatchers.startsWith("jobs/open/waitingJob_")
          )
        // verify writing of all batch files
        val batchCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        verify(writerMock, times(10))
          .write(
            ArgumentMatchers.eq(""),
            batchCaptor.capture()
          )
        batchCaptor.getAllValues.toArray.toSeq.asInstanceOf[Seq[String]]
      })(Assertion.assertion("all true")(seq => {
        seq.map(x => x.split("/").last.toInt) == Range(0, 10, 1)
      }))
        ).provide(ZLayer.succeed(clientMock))
    }
  )
}
