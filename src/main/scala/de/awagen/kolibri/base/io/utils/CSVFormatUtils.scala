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

package de.awagen.kolibri.base.io.utils

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.{CSVParameterBasedMetricDocumentFormat, MetricDocumentFormat}
import de.awagen.kolibri.datatypes.stores.MetricDocument

import scala.collection.mutable

object CSVFormatUtils {

  // would rather need distinct writer that reuses a given MetricDocumentFormat
  def metricDocumentToCsv[T <: AnyRef](doc: MetricDocument[T]): String = {
    val format: MetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")
    format.metricDocumentToString(doc)
  }

  def metricsAggregationFilenameToCsvContentMap[T <: AnyRef](agg: MetricAggregation[T], keyToStrFunc: T => String): Map[String, String] = {
    // each key corresponds to a single document to write
    // e.g one distinct aggregation, corresponding to distinct tag
    val valueMap: mutable.Map[T, MetricDocument[T]] = agg.aggregationStateMap
    val allFiles: mutable.Map[String, String] = mutable.Map.empty
    valueMap.foreach(x => {
      val key: String = keyToStrFunc.apply(x._1)
      val value: MetricDocument[T] = x._2
      val content = metricDocumentToCsv(value)
      allFiles.addOne((key, content))
    })
    Map(allFiles.toSeq: _*)
  }

}
