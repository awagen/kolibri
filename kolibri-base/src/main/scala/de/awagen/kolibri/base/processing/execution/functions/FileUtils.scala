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
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

import scala.util.matching.Regex

object FileUtils {

  /**
   * Read file into MetricDocument
   *
   * @param file       - full file path
   * @param fileReader - the file reader
   * @return
   */
  def fileToMetricDocument(file: String,
                           fileReader: Reader[String, Seq[String]],
                           format: MetricDocumentFormat): MetricDocument[Tag] = {
    val allLines: Seq[String] = fileReader.getSource(file).getLines().toSeq
    format.contentToMetricDocumentAndMetricTypeMapping(allLines.mkString("\n"))
  }

  def regexDirectoryReader(regex: Regex): DataOverviewReader = persistenceDIModule.dataOverviewReader(
    new SerializableFunction1[String, Boolean] {
      override def apply(v1: String): Boolean = regex.matches(v1)
    }
  )

}
