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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts

import com.amazonaws.regions.Regions
import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.base.io.writer.aggregation.{BaseMetricAggregationWriter, BaseMetricDocumentWriter}
import de.awagen.kolibri.base.io.writer.base.{AwsS3FileWriter, GcpGSFileWriter, LocalDirectoryFileWriter}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import org.slf4j.{Logger, LoggerFactory}

object Writer {

  private[this] val logger: Logger = LoggerFactory.getLogger(Writer.getClass)

  def localDirectoryfileWriter(directory: String): FileWriter[String, Any] = LocalDirectoryFileWriter(directory = directory)

  def documentWriter(fileWriter: FileWriter[String, Any],
                     columnSeparator: String = "\t",
                     subFolder: String,
                     tagToFilenameFunc: Tag => String = x => x.toString,
                     commentLines: Seq[String]): Writer[MetricDocument[Tag], Tag, Any] = {
    BaseMetricDocumentWriter(
      writer = fileWriter,
      format = CSVParameterBasedMetricDocumentFormat(columnSeparator = columnSeparator),
      subFolder = subFolder,
      keyToFilenameFunc = tagToFilenameFunc,
      commentLines = commentLines
    )
  }

  def localMetricAggregationWriter(directory: String,
                                   columnSeparator: String = "\t",
                                   subFolder: String,
                                   tagToFilenameFunc: Tag => String = x => x.toString,
                                   commentLines: Seq[String] = Seq.empty): Writer[MetricAggregation[Tag], Tag, Any] = BaseMetricAggregationWriter(
    writer = documentWriter(
      localDirectoryfileWriter(directory),
      columnSeparator,
      subFolder,
      tagToFilenameFunc,
      commentLines)
  )

  def awsCsvMetricAggregationWriter(bucketName: String,
                                    directory: String,
                                    columnSeparator: String = "\t",
                                    subFolder: String,
                                    tagToFilenameFunc: Tag => String = x => x.toString,
                                    commentLines: Seq[String]): Writer[MetricAggregation[Tag], Tag, Any] = {
    val awsWriter = AwsS3FileWriter(
      bucketName = bucketName,
      dirPath = directory,
      region = Regions.EU_CENTRAL_1
    )
    val writer: Writer[MetricDocument[Tag], Tag, Any] = documentWriter(
      awsWriter,
      columnSeparator,
      subFolder,
      tagToFilenameFunc,
      commentLines
    )
    BaseMetricAggregationWriter(writer = writer)
  }

  def gcpCsvMetricAggregationWriter(bucketName: String,
                                    directory: String,
                                    projectId: String,
                                    columnSeparator: String = "\t",
                                    subFolder: String,
                                    tagToFilenameFunc: Tag => String = x => x.toString,
                                    commentLines: Seq[String] = Seq.empty): Writer[MetricAggregation[Tag], Tag, Any] = {
    val gcpWriter = GcpGSFileWriter(
      bucketName = bucketName,
      dirPath = directory,
      projectID = projectId
    )
    val writer: Writer[MetricDocument[Tag], Tag, Any] = documentWriter(
      gcpWriter,
      columnSeparator,
      subFolder,
      tagToFilenameFunc,
      commentLines)
    BaseMetricAggregationWriter(writer = writer)
  }

}
