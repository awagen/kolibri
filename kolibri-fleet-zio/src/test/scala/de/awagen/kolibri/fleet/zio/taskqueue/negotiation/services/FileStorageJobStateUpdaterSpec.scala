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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.FileStorageJobStateHandlerSpec.TestObjects.testJobDefinitionJson
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, jobStateUpdater}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import zio.Scope
import zio.test.{Assertion, Spec, TestEnvironment, ZIOSpecDefault, assert}

class FileStorageJobStateUpdaterSpec extends ZIOSpecDefault {

  object TestObjects {

    // NOTE that this way of resolving resource files does not
    // point to resources folder in source code but in the respective
    // one in the target folder in the compiled sources
    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageJobStateUpdaterSpec") (

    test("store job definition and batches") {
      val writerMock = fileWriterMock
      val jobUpdater = jobStateUpdater(writerMock)
      for {
        _ <- jobUpdater.storeJobDefinitionAndBatches(testJobDefinitionJson)
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
    },

    test("move folder to done") {
      val writerMock = fileWriterMock
      val jobUpdater = jobStateUpdater(writerMock)
      for {
        _ <- jobUpdater.moveToDone("testJob1_3434839787")
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
