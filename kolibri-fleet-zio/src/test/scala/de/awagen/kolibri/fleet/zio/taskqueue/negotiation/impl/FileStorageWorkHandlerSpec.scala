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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.WorkHandler
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, jobStateHandler}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{Queue, Scope, ZIO}

class FileStorageWorkHandlerSpec extends JUnitRunnableSpec {

  object TestObjects {

    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

    def workHandler(queue: Queue[JobBatch[_, _, _]], writer: FileWriter[String, Unit], baseFolder: String): WorkHandler = FileStorageWorkHandler(
      queue,
      jobStateHandler(writer, baseFolder)
    )

  }

  def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageWorkHandlerSpec")(

    test("correctly offers multiple items") {
      // given
      val batches: Seq[JobBatch[Int, Unit, Int]] = Range(0, 10, 1).map(batchNr => {
        JobBatch(JobDefinitions.simpleWaitJob("test", 1, 10), batchNr)
      })
      val writerMock = fileWriterMock
      for {
        queue <- Queue.bounded[JobBatch[_, _, _]](5)
        workHandler <- ZIO.succeed(TestObjects.workHandler(queue, writerMock, TestObjects.baseResourceFolder))
        offerResults <- workHandler.addBatches(batches)
      } yield assertTrue(offerResults.size == 6 && !offerResults.last && offerResults.count(e => !e) == 1)
    }

  )
}
