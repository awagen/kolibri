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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{FileStorageClaimReader, FileStorageWorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.FileStorageClaimWriter
import de.awagen.kolibri.storage.io.reader.{LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.BaseClaimService

object TestObjects {

  val baseResourceFolder: String = getClass.getResource("/testdata").getPath

  def claimReader: FileStorageClaimReader = FileStorageClaimReader(
    filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
    LocalResourceFileReader(
      basePath = baseResourceFolder,
      delimiterAndPosition = None,
      fromClassPath = false
    )
  )

  def claimWriter(writer: FileWriter[String, Unit]) = FileStorageClaimWriter(writer)

  def workStateReader = FileStorageWorkStateReader(
    filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
    LocalResourceFileReader(
      basePath = baseResourceFolder,
      delimiterAndPosition = None,
      fromClassPath = false
    )
  )

  def claimService(writer: FileWriter[String, Unit]) = BaseClaimService(
    claimReader,
    claimWriter(writer),
    workStateReader
  )

}
