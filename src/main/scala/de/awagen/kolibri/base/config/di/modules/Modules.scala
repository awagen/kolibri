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


package de.awagen.kolibri.base.config.di.modules

import de.awagen.kolibri.base.io.reader.{DirectoryReader, FileReader}
import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags

object Modules {

  trait ENV_MODULE

  trait AWS_MODULE extends ENV_MODULE

  trait GCP_MODULE extends ENV_MODULE

  trait LOCAL_MODULE extends ENV_MODULE

  trait PersistenceDIModule {

    def fileWriter: FileWriter[String, _]

    def fileReader: FileReader

    def directoryPathSeparator: String

    def directoryReader(fileFilter: String => Boolean): DirectoryReader

    def csvMetricAggregationWriter(subFolder: String, tagToFilenameFunc: Tags.Tag => String): Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any]

  }

}
