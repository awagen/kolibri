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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobStateHandler
import de.awagen.kolibri.storage.io.reader.{LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.mockito.Mockito.{doNothing, doReturn, times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.Scope
import zio.test._
import zio.test.junit.JUnitRunnableSpec

class FileStorageJobStateHandlerSpec extends JUnitRunnableSpec {

  import TestObjects._

  object TestObjects {
    def fileWriterMock: FileWriter[String, Unit] = {
      val mocked = mock[FileWriter[String, Unit]]
      doNothing().when(mocked).moveDirectory(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
      doReturn(Right(())).when(mocked).write(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
      doNothing().when(mocked).copyDirectory(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
      doReturn(Right()).when(mocked).delete(ArgumentMatchers.any[String])
      mocked
    }

    // NOTE that this way of resolving resource files does not
    // point to resources folder in source code but in the respective
    // one in the target folder in the compiled sources
    val baseResourceFolder: String = getClass.getResource("/testdata").getPath
    def jobStateHandler(writer: FileWriter[String, Unit]): JobStateHandler = FileStorageJobStateHandler(
      LocalDirectoryReader(
        baseDir = baseResourceFolder,
        baseFilenameFilter = _ => true),
      LocalResourceFileReader(
        basePath = baseResourceFolder,
        delimiterAndPosition = None,
        fromClassPath = false
      ),
      writer
    )
  }

  val testJobDefinitionJson: String =
    """
      |{
      |  "type": "JUST_WAIT",
      |  "jobName": "waitingJob",
      |  "nrBatches": 10,
      |  "durationInMillis": 1000
      |}
      |""".stripMargin

  def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageJobStateHandlerSpec")(

    test("fetchState") {
      val writerMock = fileWriterMock
      val jobHandler = jobStateHandler(writerMock)
      for {
        fetchedState <- jobHandler.fetchState
      } yield assert(fetchedState.jobsSortedByPriority.size)(Assertion.equalTo(1)) &&
      assert(fetchedState.jobsSortedByPriority.head.jobId)(Assertion.equalTo("testJob1")) &&
      assert(fetchedState.jobsSortedByPriority.head.batchesWithState.keys.size)(Assertion.equalTo(10)) &&
      assert(fetchedState.jobsSortedByPriority.head.jobLevelDirectives.toSeq)(Assertion.equalTo(Seq(JobDirectives.Process)))
    },

    test("store job definition and batches") {
      val writerMock = fileWriterMock
      val jobHandler = jobStateHandler(writerMock)
      for {
        _ <- jobHandler.storeJobDefinitionAndBatches(testJobDefinitionJson)
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
        val capturedValues: Seq[String] = batchCaptor.getAllValues.toArray.toSeq.asInstanceOf[Seq[String]]
        assert(capturedValues.map(x => x.toInt))(Assertion.equalTo(Range(0, 10, 1)))
      })(Assertion.assertion("all true")(_ => true))
    },

    test("move folder to done") {
      val writerMock = fileWriterMock
      val jobHandler = jobStateHandler(writerMock)
      for {
        _ <- jobHandler.moveToDone("testJob1_3434839787")
      } yield assert(
        verify(writerMock, times(1))
          .moveDirectory(
            ArgumentMatchers.eq("jobs/open/testJob1_3434839787"),
            ArgumentMatchers.eq("jobs/done")
          )
      )(Assertion.assertion("all true")(_ => true))
    }
  )


}
