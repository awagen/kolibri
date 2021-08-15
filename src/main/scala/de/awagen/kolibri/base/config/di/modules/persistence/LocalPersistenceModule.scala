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


package de.awagen.kolibri.base.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.config.di.modules.Modules.{LOCAL_MODULE, PersistenceDIModule}
import de.awagen.kolibri.base.io.reader.{DirectoryReader, FileReader, LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.base.io.writer.base.LocalDirectoryFileWriter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Writer
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags


class LocalPersistenceModule extends PersistenceDIModule with tagging.Tag[LOCAL_MODULE] {

  assert(AppConfig.config.localPersistenceDir.isDefined, "no local persistence dir defined")

  lazy val fileWriter: FileWriter[String, _] =
    LocalDirectoryFileWriter(directory = AppConfig.config.localPersistenceDir.get)

  lazy val fileReader: FileReader =
    LocalResourceFileReader(None, fromClassPath = false)

  lazy val directoryPathSeparator: String = "/"
  val csvColumnSeparator: String = "\t"

  override def directoryReader(fileFilter: String => Boolean): DirectoryReader = LocalDirectoryReader(
    baseDir = AppConfig.config.localPersistenceDir.get,
    baseFilenameFilter = fileFilter)


  def csvMetricAggregationWriter(subFolder: String, tagToFilenameFunc: Tags.Tag => String): Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] = Writer.localMetricAggregationWriter(
    AppConfig.config.localPersistenceDir.get,
    csvColumnSeparator,
    subFolder,
    tagToFilenameFunc
  )

}
