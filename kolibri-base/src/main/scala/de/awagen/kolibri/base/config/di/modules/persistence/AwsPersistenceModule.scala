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
import de.awagen.kolibri.base.config.AppProperties.config.{csvColumnSeparator, directoryPathSeparator}
import de.awagen.kolibri.base.config.di.modules.Modules.{AWS_MODULE, PersistenceDIModule}
import de.awagen.kolibri.storage.io.reader.{AwsS3DirectoryReader, AwsS3FileReader, DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.storage.io.writer.base.AwsS3FileWriter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Writer
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags


class AwsPersistenceModule extends PersistenceDIModule with tagging.Tag[AWS_MODULE] {

  assert(AppProperties.config.awsS3Bucket.isDefined, "no s3 bucket defined")
  assert(AppProperties.config.awsS3BucketPath.isDefined, "no s3 bucket path defined")
  assert(AppProperties.config.awsS3Region.isDefined, "no s3 bucket region defined")

  override lazy val writer: FileWriter[String, _] = AwsS3FileWriter(
    bucketName = AppProperties.config.awsS3Bucket.get,
    dirPath = AppProperties.config.awsS3BucketPath.get,
    region = AppProperties.config.awsS3Region.get,
    contentType = "text/plain"
  )

  override lazy val reader: Reader[String, Seq[String]] = AwsS3FileReader(
    AppProperties.config.awsS3Bucket.get,
    AppProperties.config.awsS3BucketPath.get,
    AppProperties.config.awsS3Region.get
  )

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = AwsS3DirectoryReader(
    AppProperties.config.awsS3Bucket.get,
    AppProperties.config.awsS3BucketPath.get,
    AppProperties.config.awsS3Region.get,
    directoryPathSeparator,
    fileFilter
  )

  override def metricAggregationWriter(subFolder: String,
                                       tagToDataIdentifierFunc: Tags.Tag => String): Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] = Writer.awsMetricAggregationWriter(
    AppProperties.config.awsS3Bucket.get,
    AppProperties.config.awsS3BucketPath.get,
    subFolder,
    tagToDataIdentifierFunc
  )
}
