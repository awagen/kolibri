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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{fileWriterMock, jobStateHandler}
import de.awagen.kolibri.storage.io.reader.{LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{times, verify}
import zio.Scope
import zio.test._
import zio.test.junit.JUnitRunnableSpec

class FileStorageClaimHandlerSpec extends JUnitRunnableSpec {

  object TestObjects {

    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

    def claimHandler(writer: FileWriter[String, Unit], baseFolder: String): ClaimHandler = FileStorageClaimHandler(
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
      val claimH = claimHandler(writerMock, baseResourceFolder)
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
      assertTrue(true)
    },

    test("exerciseBatchClaim") {
      assertTrue(true)
    }


  )
}
