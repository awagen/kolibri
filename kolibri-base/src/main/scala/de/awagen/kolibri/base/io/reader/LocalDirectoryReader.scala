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


package de.awagen.kolibri.base.io.reader

import de.awagen.kolibri.base.io.reader.LocalDirectoryReader.logger
import org.slf4j.{Logger, LoggerFactory}

import java.io.File

object LocalDirectoryReader {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

}

case class LocalDirectoryReader(baseDir: String,
                                baseFilenameFilter: String => Boolean = _ => true,
                                encoding: String = "UTF-8") extends DataOverviewReader {
  val normedBaseDir: String = baseDir.stripSuffix("/")

  override def listResources(subDir: String, additionalFilenameFilter: String => Boolean): Seq[String] = {
    val normedSubDir = subDir.stripPrefix("/")
    val fullDir = s"$normedBaseDir/$normedSubDir".stripSuffix("/")
    logger.debug(s"scanning files in directory $fullDir")
    val directory = new File(fullDir)
    logger.debug(s"found files: ${directory.listFiles().mkString(",")}")
    directory.listFiles()
      .map(file => file.getAbsolutePath)
      .filter(filePath => baseFilenameFilter.apply(filePath))
      .filter(filePath => additionalFilenameFilter.apply(filePath))
      .toSeq
  }
}
