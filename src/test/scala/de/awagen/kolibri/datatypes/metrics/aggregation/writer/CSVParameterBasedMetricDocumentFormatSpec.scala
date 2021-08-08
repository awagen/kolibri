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

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat.{FAIL_COUNT_COLUMN_PREFIX, FAIL_REASONS_COLUMN_PREFIX, SUCCESS_COUNT_COLUMN_PREFIX, VALUE_COLUMN_PREFIX}
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.{ParameterMultiValueTag, StringTag, Tag}
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

  "CSVParameterBasedMetricDocumentFormat" should {

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
      row1 mustBe "v1_1\tv1_2\t0\t\t1\t0.2000\t0\t\t1\t0.4000"
      header2 mustBe expectedHeader2
      row2 mustBe "v2_1\tv2_2\t0\t\t1\t0.1000"
      header3 mustBe expectedHeader3
      row3 mustBe "v3_1\tv3_2\t0\t\t1\t0.3000"
    }

    "correctly give formatted representation of the aggregation for full document" in {
      //given
      val expectedHeader1 = "p1\tp2\tp3\tfail-count-metrics1\tfailReasons-metrics1\tsuccess-count-metrics1\tvalue-metrics1\tfail-count-metrics2\tfailReasons-metrics2\tsuccess-count-metrics2\tvalue-metrics2\tfail-count-metrics3\tfailReasons-metrics3\tsuccess-count-metrics3\tvalue-metrics3\tfail-count-metrics4\tfailReasons-metrics4\tsuccess-count-metrics4\tvalue-metrics4"
      val expectedRow1 = "v1_1\tv1_2\t\t0\t\t1\t0.2000\t0\t\t1\t0.4000\t0\t\t0\t0.0000\t0\t\t0\t0.0000"
      val expectedRow2 = "v2_1\tv2_2\t\t0\t\t0\t0.0000\t0\t\t0\t0.0000\t0\t\t1\t0.1000\t0\t\t0\t0.0000"
      val expectedRow3 = "v3_1\t\tv3_2\t0\t\t0\t0.0000\t0\t\t0\t0.0000\t0\t\t0\t0.0000\t0\t\t1\t0.3000"
      val expectedDocString = Seq(expectedHeader1, expectedRow1, expectedRow2, expectedRow3).mkString("\n")
      // when
      val actual = writer.metricDocumentToString(doc)
      actual mustBe expectedDocString
    }

    "correctly read header" in {
      val exampleHeader = s"p1\tp2\tp3\t${FAIL_COUNT_COLUMN_PREFIX}-M1\t\t"
      writer.readHeader(exampleHeader) mustBe Seq("p1", "p2", "p3", s"${FAIL_COUNT_COLUMN_PREFIX}-M1")
    }

    "correctly infer param names from headers" in {
      val exampleHeaderValues = Seq(
        "p1",
        s"$SUCCESS_COUNT_COLUMN_PREFIX-M1",
        "p2",
        s"$FAIL_COUNT_COLUMN_PREFIX-M1",
        "p3",
        s"$FAIL_REASONS_COLUMN_PREFIX-M1",
        "p4",
        s"$VALUE_COLUMN_PREFIX-M1"
      )
      writer.paramNameToValueColumnMapFromHeaders(exampleHeaderValues) mustBe
        Map("p1" -> 0, "p2" -> 2, "p3" -> 4, "p4" -> 6)
    }

    "correctly pick values by index map" in {
      val paramToIndexMap = Map("p1" -> 0, "p2" -> 2, "p3" -> 3)
      val values = Seq("v1_1", "", "v2_1&v2_2", "v3_1")
      writer.paramMapFromParamToColumnMap(paramToIndexMap, values) mustBe
        Map("p1" -> Seq("v1_1"),
          "p2" -> Seq("v2_1", "v2_2"),
          "p3" -> Seq("v3_1"))
    }

    val testHeaders = Seq(
      "p1",
      s"${FAIL_COUNT_COLUMN_PREFIX}M1",
      s"${FAIL_REASONS_COLUMN_PREFIX}M1",
      s"${VALUE_COLUMN_PREFIX}M1",
      s"${SUCCESS_COUNT_COLUMN_PREFIX}M1",
      "p2",
      s"${FAIL_COUNT_COLUMN_PREFIX}M2",
      s"${FAIL_REASONS_COLUMN_PREFIX}M2",
      s"${VALUE_COLUMN_PREFIX}M2",
      s"${SUCCESS_COUNT_COLUMN_PREFIX}M2"
    )

    val testParamsMap: Map[String, Seq[String]] = Map("p1" -> Seq("p1_v1"), "p2" -> Seq("p2_v1", "p2_v2"))
    val testColumns: Seq[String] = Seq(
      "p1_v1",
      "2",
      "JUSTFAILED:1,ANOTHERREASON:2",
      "0.43",
      "5",
      "p2_v1&p2_v2",
      "0",
      "",
      "0.68",
      "4"
    )
    val testColumns1: Seq[String] = Seq(
      "p1_v2",
      "0",
      "",
      "0.22",
      "3",
      "p2_a1&p2_a2",
      "0",
      "",
      "0.10",
      "4"
    )

    "correctly pick the right indices for category for metric name" in {
      val map: Map[String, Int] = writer.metricNameToColumnMapForCategoryFromHeaders(SUCCESS_COUNT_COLUMN_PREFIX, testHeaders)
      map mustBe Map("M1" -> 4, "M2" -> 9)
    }

    "correctly parse MetricRow" in {
      // given, when
      val metricRow: MetricRow = writer.metricRowFromHeadersAndColumns(testHeaders, testParamsMap, testColumns)
      val row1: MetricValue[Double] = metricRow.metrics("M1")
      val row2: MetricValue[Double] = metricRow.metrics("M2")
      // then
      metricRow.metrics.keys.size mustBe 2
      row1.name mustBe "M1"
      row2.name mustBe "M2"
      row1.biValue.value2.count mustBe 5
      row1.biValue.value2.value mustBe "0.43".toDouble
      row1.biValue.value1.count mustBe 2
      row1.biValue.value1.value mustBe Map(ComputeFailReason("JUSTFAILED") -> 1, ComputeFailReason("ANOTHERREASON") -> 2)
      row2.biValue.value2.count mustBe 4
      row2.biValue.value2.value mustBe "0.68".toDouble
      row2.biValue.value1.count mustBe 0
      row2.biValue.value1.value mustBe Map.empty
    }

    "correctly read row to MetricRow" in {
      // given
      val row: String = testColumns.mkString("\t")
      // when
      val metricRow: MetricRow = writer.readRow(testHeaders, row)
      val row1: MetricValue[Double] = metricRow.metrics("M1")
      val row2: MetricValue[Double] = metricRow.metrics("M2")
      // then
      metricRow.metrics.keys.size mustBe 2
      row1.name mustBe "M1"
      row2.name mustBe "M2"
      row1.biValue.value2.count mustBe 5
      row1.biValue.value2.value mustBe "0.43".toDouble
      row1.biValue.value1.count mustBe 2
      row1.biValue.value1.value mustBe Map(ComputeFailReason("JUSTFAILED") -> 1, ComputeFailReason("ANOTHERREASON") -> 2)
      row2.biValue.value2.count mustBe 4
      row2.biValue.value2.value mustBe "0.68".toDouble
      row2.biValue.value1.count mustBe 0
      row2.biValue.value1.value mustBe Map.empty
    }

    "correctly parse Document" in {
      // given
      val rows = Seq(
        testColumns.mkString("\t"),
        testColumns1.mkString("\t")
      )
      // when
      val metricDocument: stores.MetricDocument[Tag] = writer.readDocument(testHeaders, rows, StringTag("q=test"))
      val params1 = Map("p1" -> Seq("p1_v1"), "p2" -> Seq("p2_v1", "p2_v2"))
      val params2 = Map("p1" -> Seq("p1_v2"), "p2" -> Seq("p2_a1", "p2_a2"))
      val row1Metrics: Map[String, MetricValue[Double]] = metricDocument.rows(params1).metrics
      val row2Metrics: Map[String, MetricValue[Double]] = metricDocument.rows(params2).metrics
      val row1M1: MetricValue[Double] = row1Metrics("M1")
      val row1M2: MetricValue[Double] = row1Metrics("M2")
      val row2M1: MetricValue[Double] = row2Metrics("M1")
      val row2M2: MetricValue[Double] = row2Metrics("M2")
      // then
      metricDocument.id mustBe StringTag("q=test")
      metricDocument.rows.keys.size mustBe 2
      metricDocument.rows.keys.toSet mustBe Set(params1, params2)
      metricDocument.getMetricNames mustBe Set("M1", "M2")
      // check row1 values
      row1M1.biValue.value1.count mustBe 2
      row1M1.biValue.value1.value mustBe Map(ComputeFailReason("JUSTFAILED") -> 1, ComputeFailReason("ANOTHERREASON") -> 2)
      row1M1.biValue.value2.count mustBe 5
      row1M1.biValue.value2.value mustBe "0.43".toDouble
      row1M2.biValue.value1.count mustBe 0
      row1M2.biValue.value1.value mustBe Map.empty
      row1M2.biValue.value2.count mustBe 4
      row1M2.biValue.value2.value mustBe "0.68".toDouble
      // check row2 values
      row2M1.biValue.value1.count mustBe 0
      row2M1.biValue.value1.value mustBe Map.empty
      row2M1.biValue.value2.count mustBe 3
      row2M1.biValue.value2.value mustBe "0.22".toDouble
      row2M2.biValue.value1.count mustBe 0
      row2M2.biValue.value1.value mustBe Map.empty
      row2M2.biValue.value2.count mustBe 4
      row2M2.biValue.value2.value mustBe "0.10".toDouble
    }
  }

}
