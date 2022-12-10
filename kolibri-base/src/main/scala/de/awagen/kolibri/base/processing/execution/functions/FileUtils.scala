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


package de.awagen.kolibri.base.processing.execution.functions

import de.awagen.kolibri.base.config.AppConfig.persistenceModule.persistenceDIModule
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import org.slf4j.{Logger, LoggerFactory}

import java.util.Objects
import scala.util.matching.Regex

object FileUtils {

  private[this] val logger: Logger = LoggerFactory.getLogger(FileUtils.getClass)

  def readValueAggregatorMappingFromLines(lines: Seq[String]): Map[String, AggregationType] = {
    lines.filter(s => s.startsWith("K_METRIC_AGGREGATOR_MAPPING"))
      .map(s => {
        try {
          val splitted: Seq[String] = s.split("\\s+").toSeq
          (splitted(1), AggregationType.byName(splitted(2)))
        }
        catch {
          case _: Exception =>
            logger.warn(s"Could not create name to AggregationType mapping from line: $s")
            null
        }
      })
      .filter(x => Objects.nonNull(x))
      .toMap
  }

  /**
    * Read file into MetricDocument
    * @param file - full file path
    * @param fileReader - the file reader
    * @return
    */
  def fileToMetricDocument(file: String,
                           fileReader: Reader[String, Seq[String]]): MetricDocument[Tag] = {
    val csvFormat: CSVParameterBasedMetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")
    val allLines: Seq[String] = fileReader.getSource(file).getLines().toSeq
    val comments: Seq[String] = allLines.filter(line => line.startsWith("#"))
      .map(x => x.stripPrefix("#").trim)
    val metricNameTypeMapping = readValueAggregatorMappingFromLines(comments)
    val lines: Seq[String] = allLines
      .filter(line => !line.startsWith("#") && line.trim.nonEmpty)
    val headerColumns: Seq[String] = csvFormat.readHeader(lines.head)
    val rows: Seq[String] = lines.slice(1, lines.length)

    csvFormat.readDocument(headerColumns, rows, StringTag(""), metricNameTypeMapping)
  }

  def regexDirectoryReader(regex: Regex): DataOverviewReader = persistenceDIModule.dataOverviewReader(
    new SerializableFunction1[String, Boolean] {
      override def apply(v1: String): Boolean = regex.matches(v1)
    }
  )

}
