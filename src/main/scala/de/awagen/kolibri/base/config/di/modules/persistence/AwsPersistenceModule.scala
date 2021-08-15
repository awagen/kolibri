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
import de.awagen.kolibri.base.config.di.modules.Modules.{AWS_MODULE, PersistenceDIModule}
import de.awagen.kolibri.base.io.reader.{AwsS3DirectoryReader, AwsS3FileReader, DirectoryReader, FileReader}
import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.base.io.writer.base.AwsS3FileWriter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Writer
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags


class AwsPersistenceModule extends PersistenceDIModule with tagging.Tag[AWS_MODULE] {

  assert(AppConfig.config.awsS3Bucket.isDefined, "no s3 bucket defined")
  assert(AppConfig.config.awsS3BucketPath.isDefined, "no s3 bucket path defined")
  assert(AppConfig.config.awsS3Region.isDefined, "no s3 bucket region defined")

  lazy val fileWriter: FileWriter[String, _] = AwsS3FileWriter(
    bucketName = AppConfig.config.awsS3Bucket.get,
    dirPath = AppConfig.config.awsS3BucketPath.get,
    region = AppConfig.config.awsS3Region.get,
    contentType = "text/plain"
  )

  lazy val fileReader: FileReader = AwsS3FileReader(
    AppConfig.config.awsS3Bucket.get,
    AppConfig.config.awsS3BucketPath.get,
    AppConfig.config.awsS3Region.get
  )

  val directoryPathSeparator: String = "/"
  val csvColumnSeparator: String = "\t"

  def directoryReader(fileFilter: String => Boolean): DirectoryReader = AwsS3DirectoryReader(
    AppConfig.config.awsS3Bucket.get,
    AppConfig.config.awsS3BucketPath.get,
    AppConfig.config.awsS3Region.get,
    directoryPathSeparator,
    fileFilter
  )

  def csvMetricAggregationWriter(subFolder: String, tagToFilenameFunc: Tags.Tag => String): Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any] = Writer.awsCsvMetricAggregationWriter(
    AppConfig.config.awsS3Bucket.get,
    csvColumnSeparator,
    subFolder,
    tagToFilenameFunc
  )
}
