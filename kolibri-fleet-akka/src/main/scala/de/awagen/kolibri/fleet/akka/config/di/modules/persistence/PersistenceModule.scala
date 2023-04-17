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

import com.softwaremill.macwire.wire
import de.awagen.kolibri.fleet.akka.config.AppProperties
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.directoryPathSeparator
import de.awagen.kolibri.fleet.akka.config.di.modules.Modules.PersistenceDIModule
import de.awagen.kolibri.fleet.akka.config.di.modules.persistence.PersistenceModule.logger
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import de.awagen.kolibri.storage.io.writer.aggregation.BaseMetricDocumentWriter
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Random

object PersistenceModule {

  lazy private val logger: Logger = LoggerFactory.getLogger(this.getClass)

}

class PersistenceModule {

  lazy val persistenceDIModule: PersistenceDIModule = AppProperties.config.persistenceMode match {
    case "AWS" => wire[AwsPersistenceModule]
    case "GCP" => wire[GCPPersistenceModule]
    case "LOCAL" => wire[LocalPersistenceModule]
    case "RESOURCE" => wire[ResourcePersistenceModule]
    case "CLASS" =>
      val module: String = AppProperties.config.persistenceModuleClass.get
      logger.info(s"using classloader to load persistence module: $module")
      this.getClass.getClassLoader.loadClass(module).getDeclaredConstructor().newInstance().asInstanceOf[PersistenceDIModule]
    case _ => wire[LocalPersistenceModule]
  }

  import persistenceDIModule._

  lazy val keyToFilenameFunc: SerializableFunction1[Tags.Tag, String] = new SerializableFunction1[Tags.Tag, String] {
    val randomAdd: String = Random.alphanumeric.take(5).mkString
    override def apply(v1: Tags.Tag): String = s"${v1.toString}-$randomAdd"
  }

  // apply qualifiers by specifying tagging value
  def metricDocumentWriter(subfolder: String): Writer[MetricDocument[Tags.Tag], Tags.Tag, Any] =
    BaseMetricDocumentWriter(writer, AppProperties.config.metricDocumentFormats, subfolder, directoryPathSeparator,
      keyToFilenameFunc)


}
