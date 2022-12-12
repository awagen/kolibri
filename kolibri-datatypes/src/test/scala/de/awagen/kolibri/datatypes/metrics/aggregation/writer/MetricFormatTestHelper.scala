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

import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.ParameterMultiValueTag
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType

object MetricFormatTestHelper {

  val parameterTag1: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v1_1"), "p2" -> Seq("v1_2")))
  val parameterTag2: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v2_1"), "p2" -> Seq("v2_2")))
  val parameterTag3: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v3_1"), "p3" -> Seq("v3_2")))

  val metricsSuccess1: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics1", 0.2, 1.0)
  val metricsSuccess2: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics2", 0.4, 1.0)
  val metricsSuccess3: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics3", 0.1, 1.0)
  val metricsSuccess4: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics4", 0.3, 1.0)
  val metricsSuccess5: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics5", 0.6, 1.0)

  val histogramMetricSuccess1: MetricValue[Map[String, Map[String, Double]]] = MetricValue.createNestedMapSumValueSuccessSample(weighted = false)(
    "histogram1",
    Map("key1" -> Map("1" -> 1.0, "2" -> 2.0), "key2" -> Map("3" -> 1.0)),
    1.0)
  val histogramMetricSuccess2: MetricValue[Map[String, Map[String, Double]]] = MetricValue.createNestedMapSumValueSuccessSample(weighted = false)(
    "histogram1",
    Map("key1" -> Map("2" -> 1.0, "3" -> 4.0), "key2" -> Map("2" -> 1.0)),
    1.0)


  val metricRecord1: MetricRow = MetricRow.emptyForParams(parameterTag1.value).addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess1, metricsSuccess2)
  val metricRecord2: MetricRow = MetricRow.emptyForParams(parameterTag2.value).addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess3)
  val metricRecord3: MetricRow = MetricRow.emptyForParams(parameterTag3.value).addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess4)

  val histogramMetricRecord1: MetricRow = MetricRow.emptyForParams(parameterTag1.value).addFullMetricsSampleAndIncreaseSampleCount(histogramMetricSuccess1)

  val doc: MetricDocument[String] = MetricDocument.empty[String]("doc1")
  doc.add(metricRecord1)
  doc.add(metricRecord2)
  doc.add(metricRecord3)

  val histogramDoc1: MetricDocument[String] = MetricDocument.empty[String]("histogramDoc1")
  histogramDoc1.add(histogramMetricRecord1)

  val metricsNameAggregationTypeMapping = Map(
    "metrics1" -> AggregationType.DOUBLE_AVG,
    "metrics2" -> AggregationType.DOUBLE_AVG,
    "metrics3" -> AggregationType.DOUBLE_AVG,
    "metrics4" -> AggregationType.DOUBLE_AVG,
    "metrics5" -> AggregationType.DOUBLE_AVG,
    "histogram1" -> AggregationType.NESTED_MAP_UNWEIGHTED_SUM_VALUE,
    "histogram2" -> AggregationType.NESTED_MAP_UNWEIGHTED_SUM_VALUE
  )

}
