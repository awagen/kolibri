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
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.config.AppProperties.config.directoryPathSeparator
import de.awagen.kolibri.base.config.di.modules.Modules.{GCP_MODULE, PersistenceDIModule}
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, GcpGSDirectoryReader, GcpGSFileReader, Reader}
import de.awagen.kolibri.base.io.writer.Writers
import de.awagen.kolibri.base.io.writer.base.GcpGSFileWriter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Writer
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

class GCPPersistenceModule extends PersistenceDIModule with tagging.Tag[GCP_MODULE] {

  assert(AppProperties.config.gcpGSBucket.isDefined, "no gcp gs bucket defined")
  assert(AppProperties.config.gcpGSBucketPath.isDefined, "no gcp gs bucket path defined")
  assert(AppProperties.config.gcpGSProjectID.isDefined, "no gcp projectID defined")

  lazy val writer: Writers.FileWriter[String, _] = GcpGSFileWriter(
    AppProperties.config.gcpGSBucket.get,
    AppProperties.config.gcpGSBucketPath.get,
    AppProperties.config.gcpGSProjectID.get
  )

  lazy val reader: Reader[String, Seq[String]] = GcpGSFileReader(
    AppProperties.config.gcpGSBucket.get,
    AppProperties.config.gcpGSBucketPath.get,
    AppProperties.config.gcpGSProjectID.get
  )

  val filterNone: String => Boolean = new SerializableFunction1[String, Boolean](){
    override def apply(v1: String): Boolean = true
  }

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = GcpGSDirectoryReader(
    AppProperties.config.gcpGSBucket.get,
    AppProperties.config.gcpGSBucketPath.get,
    AppProperties.config.gcpGSProjectID.get,
    directoryPathSeparator,
    fileFilter)

  override def metricAggregationWriter(subFolder: String,
                                       tagToDataIdentifierFunc: Tags.Tag => String): Writers.Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] = Writer.gcpCsvMetricAggregationWriter(
    bucketName = AppProperties.config.gcpGSBucket.get,
    directory = AppProperties.config.gcpGSBucketPath.get,
    projectId = AppProperties.config.gcpGSProjectID.get,
    subFolder = subFolder,
    tagToFilenameFunc = tagToDataIdentifierFunc
  )

}
