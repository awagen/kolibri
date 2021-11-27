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

import scala.io.Source

case class LocalResourceDirectoryReader(baseDir: String,
                                        baseFilenameFilter: String => Boolean = _ => true,
                                        encoding: String = "UTF-8") extends DataOverviewReader {
  val normedBaseDir: String = baseDir.stripSuffix("/")

  /**
    * List files in subDir of baseDir that pass the given filenameFilter. Returns full paths of matching files
    * @param subDir - the sub-directory within the baseDir to look for files
    * @param filenameFilter - the filter for the filenames
    * @return Seq of full paths of files within the sub-directory within the baseDir matching the given filter
    */
  override def listResources(subDir: String, filenameFilter: String => Boolean = _ => true): Seq[String] = {
    val fullDir = s"$normedBaseDir/$subDir".stripSuffix("/").stripPrefix("/")
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(fullDir), encoding)
      .getLines()
      .map(x => s"$fullDir/$x".stripSuffix("/"))
      .filter(baseFilenameFilter.apply)
      .filter(filenameFilter.apply)
      .toSeq
  }

}
