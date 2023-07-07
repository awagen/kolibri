/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.metrics.aggregation.writer

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.values.Calculations.ResultRecord
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue, RunningValues}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.RunningValue

object MetricDocumentFormatHelper {

  /**
   * This reads the AggregationType information the rows of the MetricDocument,
   * assuming this row contains all metrics that are occurring in other rows.
   *
   * NOTE: we might want to provide option to only use the first row to derive
   * the mappings, assuming every row contains the same metrics
   * @param metricDocument
   * @return
   */
  def getMetricNameToAggregationTypeMap(metricDocument: MetricDocument[_]): Map[String, AggregationType] = {
    metricDocument.rows.values.flatMap(metricRow => {
      metricRow.metrics.map(metric => {
        (metric._1, metric._2.biValue.value2.asInstanceOf[RunningValue[_]].aggregationType)
      })
    }).toMap
  }

  def getMetricValueFromTypeAndSample[T](aggregationType: AggregationType.Val[T], sample: ResultRecord[T]): MetricValue[T] = {
    val emptyValueRunningValue: RunningValue[T] = aggregationType.emptyRunningValueSupplier.apply()
    sample.value match {
      case Left(failReasons: Seq[ComputeFailReason]) =>
        val failMap: Map[ComputeFailReason, Int] = RunningValues.mapFromFailReasons(failReasons)
        MetricValue(name = sample.name, BiRunningValue(
          RunningValues.calcErrorRunningValue(1, failMap),
          emptyValueRunningValue))
      case Right(metricValue) =>
        MetricValue(name = sample.name, BiRunningValue(
          RunningValues.calcErrorRunningValue(0, Map.empty),
          emptyValueRunningValue.copy(weight = 1.0, numSamples = 1, value = metricValue)))
    }
  }

}
