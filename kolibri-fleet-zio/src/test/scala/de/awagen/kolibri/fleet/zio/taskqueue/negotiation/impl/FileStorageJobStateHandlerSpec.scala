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
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, jobStateHandler}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import zio.Scope
import zio.test._
import zio.test.junit.JUnitRunnableSpec

class FileStorageJobStateHandlerSpec extends JUnitRunnableSpec {

  import TestObjects._

  object TestObjects {

    // NOTE that this way of resolving resource files does not
    // point to resources folder in source code but in the respective
    // one in the target folder in the compiled sources
    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

    val testJobDefinitionJson: String =
      """
        |{
        |  "type": "JUST_WAIT",
        |  "jobName": "waitingJob",
        |  "nrBatches": 10,
        |  "durationInMillis": 1000
        |}
        |""".stripMargin
  }

  def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageJobStateHandlerSpec")(

    test("fetchState") {
      val writerMock = fileWriterMock
      val jobHandler = jobStateHandler(writerMock, baseResourceFolder)
      for {
        fetchedState <- jobHandler.fetchOpenJobState
      } yield assert(fetchedState.allJobsSortedByPriority.size)(Assertion.equalTo(1)) &&
      assert(fetchedState.allJobsSortedByPriority.head.jobId)(Assertion.equalTo("testJob1")) &&
      assert(fetchedState.allJobsSortedByPriority.head.batchesToState.keys.size)(Assertion.equalTo(10)) &&
      assert(fetchedState.allJobsSortedByPriority.head.jobLevelDirectives.toSeq)(Assertion.equalTo(Seq(JobDirectives.Process)))
    },

    test("store job definition and batches") {
      val writerMock = fileWriterMock
      val jobHandler = jobStateHandler(writerMock, baseResourceFolder)
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
        batchCaptor.getAllValues.toArray.toSeq.asInstanceOf[Seq[String]]
      })(Assertion.assertion("all true")(seq => {
        seq.map(x => x.split("/").last.toInt) == Range(0, 10, 1)
      }))
    },

    test("move folder to done") {
      val writerMock = fileWriterMock
      val jobHandler = jobStateHandler(writerMock, baseResourceFolder)
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
