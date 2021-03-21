/**
  * Copyright 2021 Andreas Wagenmann
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

package de.awagen.kolibri.base.io.writer.base

import java.io.{BufferedWriter, File, IOException}
import java.util.Objects

import de.awagen.kolibri.base.io.writer.Writers.FileWriter
import org.slf4j.{Logger, LoggerFactory}


case class LocalDirectoryFileFileWriter(directory: String) extends FileWriter[String, Unit] {
  private val logger: Logger = LoggerFactory.getLogger(LocalDirectoryFileFileWriter.getClass)
  assert(Objects.nonNull(directory) && directory.startsWith("/"), "directory must start with '/' (must start from root)")
  val normedDirectory: String = directory.stripSuffix("/")

  override def write(data: String, targetIdentifier: String): Either[Exception, Unit] = {
    val fullPath = s"$normedDirectory/$targetIdentifier"
    try {
      val file = new File(fullPath)
      val bufferedWriter = new BufferedWriter(new java.io.FileWriter(file))
      bufferedWriter.write(data)
      bufferedWriter.close()
      Right(())
    }
    catch {
      case e: IOException =>
        logger.error(s"failed writing file: $fullPath", e)
        Left(e)
      case e: NullPointerException =>
        logger.error(s"Could not create file handle for file: $fullPath")
        Left(e)
    }
  }

}
