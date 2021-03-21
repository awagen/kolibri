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

package de.awagen.kolibri.datatypes.metrics.aggregation.writer

import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.ParameterMultiValueTag
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.MetricValue

class CSVParameterBasedMetricDocumentFormatSpec extends UnitTestSpec {

  val parameterTag1: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v1_1"), "p2" -> Seq("v1_2")))
  val parameterTag2: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v2_1"), "p2" -> Seq("v2_2")))
  val parameterTag3: ParameterMultiValueTag = ParameterMultiValueTag(Map("p1" -> Seq("v3_1"), "p3" -> Seq("v3_2")))

  val metricsSuccess1: MetricValue[Double] = MetricValue.createAvgSuccessSample("metrics1", 0.2)
  val metricsSuccess2: MetricValue[Double] = MetricValue.createAvgSuccessSample("metrics2", 0.4)
  val metricsSuccess3: MetricValue[Double] = MetricValue.createAvgSuccessSample("metrics3", 0.1)
  val metricsSuccess4: MetricValue[Double] = MetricValue.createAvgSuccessSample("metrics4", 0.3)
  val metricsSuccess5: MetricValue[Double] = MetricValue.createAvgSuccessSample("metrics5", 0.6)


  val metricRecord1: MetricRow = MetricRow(parameterTag1.value, Map.empty).addMetrics(metricsSuccess1, metricsSuccess2)
  val metricRecord2: MetricRow = MetricRow(parameterTag2.value, Map.empty).addMetrics(metricsSuccess3)
  val metricRecord3: MetricRow = MetricRow(parameterTag3.value, Map.empty).addMetrics(metricsSuccess4)

  val doc: MetricDocument[String] = MetricDocument.empty[String]("doc1")
  doc.add(metricRecord1)
  doc.add(metricRecord2)
  doc.add(metricRecord3)


  val writer: CSVParameterBasedMetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")

  "ParameterBasedMetricDocumentFormat" should {

    "correctly give formatted representation of the aggregation for single rows" in {
      //given, when
      val header1: String = writer.formatHeader(parameterTag1.value.keySet.toSeq.sorted, metricRecord1.metrics.keySet.toSeq.sorted)
      val header2: String = writer.formatHeader(parameterTag2.value.keySet.toSeq.sorted, metricRecord2.metrics.keySet.toSeq.sorted)
      val header3: String = writer.formatHeader(parameterTag3.value.keySet.toSeq.sorted, metricRecord3.metrics.keySet.toSeq.sorted)
      val row1: String = writer.formatRow(metricRecord1, parameterTag1.value.keySet.toSeq.sorted, metricRecord1.metrics.keySet.toSeq.sorted)
      val row2: String = writer.formatRow(metricRecord2, parameterTag2.value.keySet.toSeq.sorted, metricRecord2.metrics.keySet.toSeq.sorted)
      val row3: String = writer.formatRow(metricRecord3, parameterTag3.value.keySet.toSeq.sorted, metricRecord3.metrics.keySet.toSeq.sorted)

      val expectedHeader1 = "p1\tp2\tfail-count-metrics1\tfailReasons-metrics1\tsuccess-count-metrics1\tvalue-metrics1\tfail-count-metrics2\tfailReasons-metrics2\tsuccess-count-metrics2\tvalue-metrics2"
      val expectedHeader2 = "p1\tp2\tfail-count-metrics3\tfailReasons-metrics3\tsuccess-count-metrics3\tvalue-metrics3"
      val expectedHeader3 = "p1\tp3\tfail-count-metrics4\tfailReasons-metrics4\tsuccess-count-metrics4\tvalue-metrics4"
      // then
      header1 mustBe expectedHeader1
      row1 mustBe "v1_1\tv1_2\t0\t\t1\t0.2\t0\t\t1\t0.4"
      header2 mustBe expectedHeader2
      row2 mustBe "v2_1\tv2_2\t0\t\t1\t0.1"
      header3 mustBe expectedHeader3
      row3 mustBe "v3_1\tv3_2\t0\t\t1\t0.3"
    }

    "correctly give formatted representation of the aggregation for full document" in {
      //given
      val expectedHeader1 = "p1\tp2\tp3\tfail-count-metrics1\tfailReasons-metrics1\tsuccess-count-metrics1\tvalue-metrics1\tfail-count-metrics2\tfailReasons-metrics2\tsuccess-count-metrics2\tvalue-metrics2\tfail-count-metrics3\tfailReasons-metrics3\tsuccess-count-metrics3\tvalue-metrics3\tfail-count-metrics4\tfailReasons-metrics4\tsuccess-count-metrics4\tvalue-metrics4"
      val expectedRow1 = "v1_1\tv1_2\t\t0\t\t1\t0.2\t0\t\t1\t0.4\t0\t\t0\t0.0\t0\t\t0\t0.0"
      val expectedRow2 = "v2_1\tv2_2\t\t0\t\t0\t0.0\t0\t\t0\t0.0\t0\t\t1\t0.1\t0\t\t0\t0.0"
      val expectedRow3 = "v3_1\t\tv3_2\t0\t\t0\t0.0\t0\t\t0\t0.0\t0\t\t0\t0.0\t0\t\t1\t0.3"
      val expectedDocString = Seq(expectedHeader1, expectedRow1, expectedRow2, expectedRow3).mkString("\n")
      // when
      val actual = writer.metricDocumentToString(doc)
      actual mustBe expectedDocString
    }

  }


}
