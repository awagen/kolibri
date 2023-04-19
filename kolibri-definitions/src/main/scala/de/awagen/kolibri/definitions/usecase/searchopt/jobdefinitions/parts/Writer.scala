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


package de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts

import com.amazonaws.regions.Regions
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.storage.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.storage.io.writer.aggregation.{BaseMetricAggregationWriter, BaseMetricDocumentWriter}
import de.awagen.kolibri.storage.io.writer.base.{AwsS3FileWriter, GcpGSFileWriter, LocalDirectoryFileWriter}

object Writer {

  def localDirectoryfileWriter(directory: String): FileWriter[String, Any] = LocalDirectoryFileWriter(directory = directory)

  def documentWriter(fileWriter: FileWriter[String, Any],
                     formats: Seq[MetricDocumentFormat],
                     subFolder: String,
                     tagToFilenameFunc: Tag => String = x => x.toString): Writer[MetricDocument[Tag], Tag, Any] = {
    BaseMetricDocumentWriter(
      writer = fileWriter,
      formats = formats,
      subFolder = subFolder,
      keyToFilenameFunc = tagToFilenameFunc,
    )
  }

  def localMetricAggregationWriter(formats: Seq[MetricDocumentFormat],
                                   directory: String,
                                   subFolder: String,
                                   tagToFilenameFunc: Tag => String = x => x.toString): Writer[MetricAggregation[Tag], Tag, Any] = BaseMetricAggregationWriter(
    writer = documentWriter(
      localDirectoryfileWriter(directory),
      formats,
      subFolder,
      tagToFilenameFunc
    )
  )

  def awsMetricAggregationWriter(formats: Seq[MetricDocumentFormat],
                                 bucketName: String,
                                 directory: String,
                                 subFolder: String,
                                 tagToFilenameFunc: Tag => String = x => x.toString): Writer[MetricAggregation[Tag], Tag, Any] = {
    val awsWriter = AwsS3FileWriter(
      bucketName = bucketName,
      dirPath = directory,
      region = Regions.EU_CENTRAL_1
    )
    val writer: Writer[MetricDocument[Tag], Tag, Any] = documentWriter(
      awsWriter,
      formats,
      subFolder,
      tagToFilenameFunc
    )
    BaseMetricAggregationWriter(writer = writer)
  }

  def gcpCsvMetricAggregationWriter(formats: Seq[MetricDocumentFormat],
                                    bucketName: String,
                                    directory: String,
                                    projectId: String,
                                    subFolder: String,
                                    tagToFilenameFunc: Tag => String = x => x.toString): Writer[MetricAggregation[Tag], Tag, Any] = {
    val gcpWriter = GcpGSFileWriter(
      bucketName = bucketName,
      dirPath = directory,
      projectID = projectId
    )
    val writer: Writer[MetricDocument[Tag], Tag, Any] = documentWriter(
      gcpWriter,
      formats,
      subFolder,
      tagToFilenameFunc)
    BaseMetricAggregationWriter(writer = writer)
  }

}
