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


package de.awagen.kolibri.fleet.zio.testutils

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{FileStorageJobStateReader, JobStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{FileStorageJobStateWriter, JobStateWriter}
import de.awagen.kolibri.storage.io.reader.{LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{doNothing, doReturn}
import org.scalatestplus.mockito.MockitoSugar.mock

object TestObjects {

  def fileWriterMock: FileWriter[String, Unit] = {
    val mocked = mock[FileWriter[String, Unit]]
    doNothing().when(mocked).moveDirectory(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
    doReturn(Right(())).when(mocked).write(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
    doNothing().when(mocked).copyDirectory(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
    doReturn(Right()).when(mocked).delete(ArgumentMatchers.any[String])
    mocked
  }

  def jobStateHandler(writer: FileWriter[String, Unit], baseFolder: String): JobStateReader = FileStorageJobStateReader(
    LocalDirectoryReader(
      baseDir = baseFolder,
      baseFilenameFilter = _ => true),
    LocalResourceFileReader(
      basePath = baseFolder,
      delimiterAndPosition = None,
      fromClassPath = false
    )
  )

  def jobStateUpdater(writer: FileWriter[String, Unit]): JobStateWriter = FileStorageJobStateWriter(
    writer
  )

}
