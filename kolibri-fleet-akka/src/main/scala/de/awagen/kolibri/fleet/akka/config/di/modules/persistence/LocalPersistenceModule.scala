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


package de.awagen.kolibri.fleet.akka.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.kolibri.fleet.akka.config.AppProperties
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.localPersistenceWriteResultsSubPath
import de.awagen.kolibri.fleet.akka.config.di.modules.Modules.{LOCAL_MODULE, PersistenceDIModule}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalDirectoryReader, LocalResourceFileReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.storage.io.writer.base.LocalDirectoryFileWriter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Writer
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags


class LocalPersistenceModule extends PersistenceDIModule with tagging.Tag[LOCAL_MODULE] {

  assert(AppProperties.config.localPersistenceWriteBasePath.isDefined, "no local persistence dir defined")

  lazy val writer: FileWriter[String, _] =
    LocalDirectoryFileWriter(directory = AppProperties.config.localPersistenceWriteBasePath.get)

  lazy val reader: Reader[String, Seq[String]] =
    LocalResourceFileReader(
      basePath = AppProperties.config.localPersistenceReadBasePath.get,
      delimiterAndPosition = None,
      fromClassPath = false
    )

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = LocalDirectoryReader(
    baseDir = AppProperties.config.localPersistenceReadBasePath.get,
    baseFilenameFilter = fileFilter)


  def metricAggregationWriter(subFolder: String,
                              tagToDataIdentifierFunc: Tags.Tag => String): Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] = Writer.localMetricAggregationWriter(
    AppProperties.config.metricDocumentFormats,
    s"${AppProperties.config.localPersistenceWriteBasePath.get.trim.stripSuffix("/")}/${localPersistenceWriteResultsSubPath.get.stripPrefix("/")}",
    subFolder,
    tagToDataIdentifierFunc
  )

}
