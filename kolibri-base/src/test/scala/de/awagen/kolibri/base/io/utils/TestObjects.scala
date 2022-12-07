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

import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.ParameterMultiValueTag
import de.awagen.kolibri.datatypes.values.MetricValue

object TestObjects {

  // TODO: those are copied from the ParameterBasedMetricDocumentFormatSpec in kolibri-datatypes
  // might wanna remove this duplication
  val parameterTag1: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v1_1"), "p2" -> Seq("v1_2")))
  val parameterTag2: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v2_1"), "p2" -> Seq("v2_2")))
  val parameterTag3: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v3_1"), "p3" -> Seq("v3_2")))

  val metricsSuccess1: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics1", 0.2, 1.0)
  val metricsSuccess2: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics2", 0.4, 1.0)
  val metricsSuccess3: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics3", 0.1, 1.0)
  val metricsSuccess4: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics4", 0.3, 1.0)
  val metricsSuccess5: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics5", 0.6, 1.0)


  val metricRecord1: MetricRow = MetricRow.emptyForParams(parameterTag1.value).addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess1, metricsSuccess2)
  val metricRecord2: MetricRow = MetricRow.emptyForParams(parameterTag2.value).addMetricDontChangeCountStore(metricsSuccess3)
  val metricRecord3: MetricRow = MetricRow.emptyForParams(parameterTag3.value).addMetricDontChangeCountStore(metricsSuccess4)

  val doc1: MetricDocument[String] = MetricDocument.empty[String]("doc1")
  doc1.add(metricRecord1)
  doc1.add(metricRecord2)
  doc1.add(metricRecord3)

  val doc2: MetricDocument[String] = MetricDocument.empty[String]("doc2")
  doc2.add(metricRecord1)
  doc2.add(metricRecord2)

}
