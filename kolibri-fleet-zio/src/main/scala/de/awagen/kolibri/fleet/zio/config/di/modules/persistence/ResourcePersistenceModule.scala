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


package de.awagen.kolibri.fleet.zio.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.di.modules.Modules.{PersistenceDIModule, RESOURCE_MODULE}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalResourceDirectoryReader, LocalResourceFileReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter

import java.io.IOException

class ResourcePersistenceModule extends PersistenceDIModule with tagging.Tag[RESOURCE_MODULE] {

  override def reader: Reader[String, Seq[String]] = LocalResourceFileReader(
    basePath = AppProperties.config.localResourceReadBasePath.get,
    delimiterAndPosition = None,
    fromClassPath = true)

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = LocalResourceDirectoryReader(
    baseDir = AppProperties.config.localResourceReadBasePath.get,
    baseFilenameFilter = fileFilter)

  override def writer: Writers.FileWriter[String, _] = new FileWriter[String, Unit] {
    override def write(data: String, targetIdentifier: String): Either[Exception, Unit] = {
      Left(new IOException("not writing to local resource"))
    }

    // TODO: implement
    override def delete(targetIdentifier: String): Either[Exception, Unit] = ???

    // TODO: implement
    override def copyDirectory(dirPath: String, toDirPath: String): Unit = ???

    // TODO: implement
    override def moveDirectory(dirPath: String, toDirPath: String): Unit = ???

    // TODO: implement
    override def deleteDirectory(dirPath: String): Unit = ???
  }

  override def metricAggregationWriter(subFolder: String,
                                       tagToDataIdentifierFunc: Tags.Tag => String): Writers.Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] = new Writers.Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] {
    override def write(data: MetricAggregation[Tags.Tag], targetIdentifier: Tags.Tag): Either[Exception, Any] = Left(new IOException("not writing to local resources"))

    // TODO: implement
    override def delete(targetIdentifier: Tags.Tag): Either[Exception, Unit] = ???

    // TODO: implement
    override def copyDirectory(dirPath: String, toDirPath: String): Unit = ???

    // TODO: implement
    override def moveDirectory(dirPath: String, toDirPath: String): Unit = ???

    // TODO: implement
    override def deleteDirectory(dirPath: String): Unit = ???
  }

}
