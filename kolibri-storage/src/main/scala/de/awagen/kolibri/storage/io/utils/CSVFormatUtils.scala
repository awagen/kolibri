package de.awagen.kolibri.storage.io.utils

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
