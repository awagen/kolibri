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

package de.awagen.kolibri.storage.io.writer.base

import java.io.{BufferedWriter, File, IOException}
import java.util.Objects
import java.nio.file.Files
import java.nio.file.Paths

import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.slf4j.{Logger, LoggerFactory}


case class LocalDirectoryFileWriter(directory: String) extends FileWriter[String, Unit] {
  private val logger: Logger = LoggerFactory.getLogger(LocalDirectoryFileWriter.getClass)
  assert(Objects.nonNull(directory) && directory.startsWith("/"), "directory must start with '/' (must start from root)")
  val normedDirectory: String = directory.stripSuffix("/")

  override def write(data: String, targetIdentifier: String): Either[Exception, Unit] = {
    logger.info(s"writing data for identifier: $targetIdentifier")
    val fullPath = s"$normedDirectory/$targetIdentifier"
    val fileName = fullPath.split("/").last
    val fullPathWithoutFile = fullPath.stripSuffix(fileName).stripSuffix("/")
    try {
      Files.createDirectories(Paths.get(fullPathWithoutFile))
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

  override def delete(targetIdentifier: String): Either[Exception, Unit] = {
    logger.info(s"deleting data for identifier: $targetIdentifier")
    val fullPath = s"$normedDirectory/$targetIdentifier"
    try {
      val file = new File(fullPath)
      file.delete()
      Right(())
    }
    catch {
      case e: IOException =>
        logger.error(s"failed deleting file: $fullPath", e)
        Left(e)
      case e: NullPointerException =>
        logger.error(s"Could not get file handle for deleting file: $fullPath")
        Left(e)
    }
  }
}
