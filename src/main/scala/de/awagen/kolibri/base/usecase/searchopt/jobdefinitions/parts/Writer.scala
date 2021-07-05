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

import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.base.io.writer.aggregation.{BaseMetricAggregationWriter, BaseMetricDocumentWriter}
import de.awagen.kolibri.base.io.writer.base.LocalDirectoryFileFileWriter
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag

object Writer {


  def localDirectoryfileWriter(directory: String): FileWriter[String, Any] = LocalDirectoryFileFileWriter(directory = directory)

  def localDocumentWriter(directory: String,
                          columnSeparator: "\t",
                          subFolder: String,
                          tagToFilenameFunc: Tag => String = x => x.toString): Writer[MetricDocument[Tag], Tag, Any] = BaseMetricDocumentWriter(
    writer = localDirectoryfileWriter(directory),
    format = CSVParameterBasedMetricDocumentFormat(columnSeparator = columnSeparator),
    subFolder = subFolder,
    keyToFilenameFunc = tagToFilenameFunc)

  def localMetricAggregationWriter(directory: String,
                                   columnSeparator: "\t",
                                   subFolder: String,
                                   tagToFilenameFunc: Tag => String = x => x.toString): Writer[MetricAggregation[Tag], Tag, Any] = BaseMetricAggregationWriter(
    writer = localDocumentWriter(
      directory,
      columnSeparator,
      subFolder,
      tagToFilenameFunc)
  )

}
