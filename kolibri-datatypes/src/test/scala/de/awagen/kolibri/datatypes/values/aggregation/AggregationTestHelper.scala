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


package de.awagen.kolibri.datatypes.values.aggregation

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormatHelper
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue}
import de.awagen.kolibri.datatypes.values.Calculations.ResultRecord
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.{RunningValue, nestedMapValueUnweightedSumUpRunningValue}

object AggregationTestHelper {

  val runningValueSupplier: () => RunningValue[Map[String, Map[String, Double]]] = () =>
    nestedMapValueUnweightedSumUpRunningValue(1.0, 1,
    Map("key1" -> Map("1" -> 1.0)))

  val biRunningValueSupplier: () => BiRunningValue[Map[ComputeFailReason, Int], Map[String, Map[String, Double]]] = () => {
    val runningValue = nestedMapValueUnweightedSumUpRunningValue(1.0, 1,
      Map("key1" -> Map("1" -> 1.0)))
    val value = MetricDocumentFormatHelper.getMetricValueFromTypeAndSample(
      AggregationType.NESTED_MAP_UNWEIGHTED_SUM_VALUE,
      ResultRecord("m1", Right(runningValue.value))
    )
    value.biValue
  }

  val metricValueSupplier: () => MetricValue[Map[String, Map[String, Double]]] = () => {
    val runningValue = nestedMapValueUnweightedSumUpRunningValue(1.0, 1,
      Map("key1" -> Map("1" -> 1.0)))

    MetricDocumentFormatHelper.getMetricValueFromTypeAndSample(
      AggregationType.NESTED_MAP_UNWEIGHTED_SUM_VALUE,
      ResultRecord("m1", Right(runningValue.value))
    )
  }

  val metricRowSupplier: () => MetricRow = () => {
    MetricRow.emptyForParams(Map("p" -> Seq("1.0")))
      .addFullMetricsSampleAndIncreaseSampleCount(metricValueSupplier.apply())
  }

  def extractBiggestNestedMapValue(metricName: String, doc: MetricDocument[Tag]): Double = {
    val allRowValues: Seq[Map[String, Map[String, Double]]] = doc.rows.values.map(x => x.metrics.get(metricName)
      .map(y => y.biValue.value2.value.asInstanceOf[Map[String, Map[String, Double]]]))
      .filter(x => x.nonEmpty).map(x => x.get).toSeq
    val biggestValuePerRow: Seq[Double] = allRowValues.flatMap(x => x.values).map(x => {
      return x.values.foldLeft[Double](0.0)((current, newVal) => math.max(current, newVal))
    })
    biggestValuePerRow.foldLeft(0.0)((current, newVal) => math.max(current, newVal))
  }

}
