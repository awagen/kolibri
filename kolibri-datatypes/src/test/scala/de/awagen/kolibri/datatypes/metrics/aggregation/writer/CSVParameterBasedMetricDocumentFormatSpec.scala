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

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat._
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricFormatTestHelper._
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import spray.json.DefaultJsonProtocol.{DoubleJsonFormat, StringJsonFormat, mapFormat}
import spray.json._


class CSVParameterBasedMetricDocumentFormatSpec extends UnitTestSpec {




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

      val expectedHeader1 = "p1\tp2\tfail-count-metrics1\tweighted-fail-count-metrics1\tfailReasons-metrics1\tsuccess-count-metrics1\tweighted-success-count-metrics1\tvalue-metrics1\tfail-count-metrics2\tweighted-fail-count-metrics2\tfailReasons-metrics2\tsuccess-count-metrics2\tweighted-success-count-metrics2\tvalue-metrics2\tcontextInfo"
      val expectedHeader2 = "p1\tp2\tfail-count-metrics3\tweighted-fail-count-metrics3\tfailReasons-metrics3\tsuccess-count-metrics3\tweighted-success-count-metrics3\tvalue-metrics3\tcontextInfo"
      val expectedHeader3 = "p1\tp3\tfail-count-metrics4\tweighted-fail-count-metrics4\tfailReasons-metrics4\tsuccess-count-metrics4\tweighted-success-count-metrics4\tvalue-metrics4\tcontextInfo"
      // then
      header1 mustBe expectedHeader1
      row1 mustBe "v1_1\tv1_2\t0\t0.0000\t\t1\t1.0000\t0.2000\t0\t0.0000\t\t1\t1.0000\t0.4000\t{}"
      header2 mustBe expectedHeader2
      row2 mustBe "v2_1\tv2_2\t0\t0.0000\t\t1\t1.0000\t0.1000\t{}"
      header3 mustBe expectedHeader3
      row3 mustBe "v3_1\tv3_2\t0\t0.0000\t\t1\t1.0000\t0.3000\t{}"
    }

    "correctly give formatted representation of the aggregation for full document" in {
      //given
      val typeHeader = "# K_METRIC_AGGREGATOR_MAPPING metrics1 DOUBLE_AVG\n# K_METRIC_AGGREGATOR_MAPPING metrics2 DOUBLE_AVG\n# K_METRIC_AGGREGATOR_MAPPING metrics3 DOUBLE_AVG\n# K_METRIC_AGGREGATOR_MAPPING metrics4 DOUBLE_AVG"
      val expectedHeader1 = "p1\tp2\tp3\tfail-count-metrics1\tweighted-fail-count-metrics1\tfailReasons-metrics1\tsuccess-count-metrics1\tweighted-success-count-metrics1\tvalue-metrics1\tfail-count-metrics2\tweighted-fail-count-metrics2\tfailReasons-metrics2\tsuccess-count-metrics2\tweighted-success-count-metrics2\tvalue-metrics2\tfail-count-metrics3\tweighted-fail-count-metrics3\tfailReasons-metrics3\tsuccess-count-metrics3\tweighted-success-count-metrics3\tvalue-metrics3\tfail-count-metrics4\tweighted-fail-count-metrics4\tfailReasons-metrics4\tsuccess-count-metrics4\tweighted-success-count-metrics4\tvalue-metrics4\tcontextInfo"
      val expectedRow1 = "v1_1\tv1_2\t\t0\t0.0000\t\t1\t1.0000\t0.2000\t0\t0.0000\t\t1\t1.0000\t0.4000\t0\t0.0000\t\t0\t0.0000\t0.0000\t0\t0.0000\t\t0\t0.0000\t0.0000\t{}"
      val expectedRow2 = "v2_1\tv2_2\t\t0\t0.0000\t\t0\t0.0000\t0.0000\t0\t0.0000\t\t0\t0.0000\t0.0000\t0\t0.0000\t\t1\t1.0000\t0.1000\t0\t0.0000\t\t0\t0.0000\t0.0000\t{}"
      val expectedRow3 = "v3_1\t\tv3_2\t0\t0.0000\t\t0\t0.0000\t0.0000\t0\t0.0000\t\t0\t0.0000\t0.0000\t0\t0.0000\t\t0\t0.0000\t0.0000\t0\t0.0000\t\t1\t1.0000\t0.3000\t{}"
      val expectedDocString = Seq(typeHeader, expectedHeader1, expectedRow1, expectedRow2, expectedRow3).mkString("\n")
      // when
      val actual = writer.metricDocumentToString(doc)
      actual mustBe expectedDocString
    }

    "correctly give formatted representation of histogram aggregation" in {
      // given
      val typeHeader = "# K_METRIC_AGGREGATOR_MAPPING histogram1 NESTED_MAP_UNWEIGHTED_SUM_VALUE"
      val expectedHeader1 = "p1\tp2\tfail-count-histogram1\tweighted-fail-count-histogram1\tfailReasons-histogram1\tsuccess-count-histogram1\tweighted-success-count-histogram1\tvalue-histogram1\tcontextInfo"
      val expectedRow1 = "v1_1\tv1_2\t0\t0.0000\t\t1\t1.0000\t{\"key1\":{\"1\":1.0,\"2\":2.0},\"key2\":{\"3\":1.0}}\t{}"
      val expectedDocString = Seq(typeHeader, expectedHeader1, expectedRow1).mkString("\n")
      // when
      val actualFormat = writer.metricDocumentToString(histogramDoc1)
      // then
      actualFormat mustBe expectedDocString
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
      s"${WEIGHTED_FAIL_COUNT_COLUMN_PREFIX}M1",
      s"${FAIL_REASONS_COLUMN_PREFIX}M1",
      s"${VALUE_COLUMN_PREFIX}M1",
      s"${SUCCESS_COUNT_COLUMN_PREFIX}M1",
      s"${WEIGHTED_SUCCESS_COUNT_COLUMN_PREFIX}M1",
      "p2",
      s"${FAIL_COUNT_COLUMN_PREFIX}M2",
      s"${WEIGHTED_FAIL_COUNT_COLUMN_PREFIX}M2",
      s"${FAIL_REASONS_COLUMN_PREFIX}M2",
      s"${VALUE_COLUMN_PREFIX}M2",
      s"${SUCCESS_COUNT_COLUMN_PREFIX}M2",
      s"${WEIGHTED_SUCCESS_COUNT_COLUMN_PREFIX}M2",
      "contextInfo"
    )

    val testParamsMap: Map[String, Seq[String]] = Map("p1" -> Seq("p1_v1"), "p2" -> Seq("p2_v1", "p2_v2"))
    val testColumns: Seq[String] = Seq(
      "p1_v1",
      "2",
      "2.0",
      "JUSTFAILED:1,ANOTHERREASON:2",
      "0.43",
      "5",
      "5.0",
      "p2_v1&p2_v2",
      "0",
      "0.0",
      "",
      "0.68",
      "4",
      "4.0",
      "{}"
    )
    val testColumns1: Seq[String] = Seq(
      "p1_v2",
      "0",
      "0.0",
      "",
      "0.22",
      "3",
      "3.0",
      "p2_a1&p2_a2",
      "0",
      "0.0",
      "",
      "0.10",
      "4",
      "4.0",
      "{}"
    )

    "correctly pick the right indices for category for metric name" in {
      val map: Map[String, Int] = writer.metricNameToColumnMapForCategoryFromHeaders(SUCCESS_COUNT_COLUMN_PREFIX, testHeaders)
      map mustBe Map("M1" -> 5, "M2" -> 12)
    }

    "correctly parse MetricRow" in {
      // given, when
      val nameToTypeMapping = Map("M1" -> AggregationType.DOUBLE_AVG, "M2" -> AggregationType.DOUBLE_AVG)
      val metricRow: MetricRow = writer.metricRowFromHeadersAndColumns(testHeaders, testParamsMap, testColumns,
        nameToTypeMapping, Map.empty)
      val row1: MetricValue[Double] = metricRow.metrics("M1").asInstanceOf[MetricValue[Double]]
      val row2: MetricValue[Double] = metricRow.metrics("M2").asInstanceOf[MetricValue[Double]]
      // then
      metricRow.metrics.keys.size mustBe 2
      row1.name mustBe "M1"
      row2.name mustBe "M2"
      row1.biValue.value2.numSamples mustBe 5
      row1.biValue.value2.value mustBe "0.43".toDouble
      row1.biValue.value1.numSamples mustBe 2
      row1.biValue.value1.value mustBe Map(ComputeFailReason("JUSTFAILED") -> 1, ComputeFailReason("ANOTHERREASON") -> 2)
      row2.biValue.value2.numSamples mustBe 4
      row2.biValue.value2.value mustBe "0.68".toDouble
      row2.biValue.value1.numSamples mustBe 0
      row2.biValue.value1.value mustBe Map.empty
    }

    "correctly read row to MetricRow" in {
      // given
      val row: String = testColumns.mkString("\t")
      // when
      val nameToTypeMapping = Map("M1" -> AggregationType.DOUBLE_AVG, "M2" -> AggregationType.DOUBLE_AVG)
      val metricRow: MetricRow = writer.readRow(testHeaders, row, nameToTypeMapping)
      val row1: MetricValue[Double] = metricRow.metrics("M1").asInstanceOf[MetricValue[Double]]
      val row2: MetricValue[Double] = metricRow.metrics("M2").asInstanceOf[MetricValue[Double]]
      // then
      metricRow.metrics.keys.size mustBe 2
      row1.name mustBe "M1"
      row2.name mustBe "M2"
      row1.biValue.value2.numSamples mustBe 5
      row1.biValue.value2.value mustBe "0.43".toDouble
      row1.biValue.value1.numSamples mustBe 2
      row1.biValue.value1.value mustBe Map(ComputeFailReason("JUSTFAILED") -> 1, ComputeFailReason("ANOTHERREASON") -> 2)
      row2.biValue.value2.numSamples mustBe 4
      row2.biValue.value2.value mustBe "0.68".toDouble
      row2.biValue.value1.numSamples mustBe 0
      row2.biValue.value1.value mustBe Map.empty
    }

    "correctly parse Document" in {
      // given
      val rows = Seq(
        testColumns.mkString("\t"),
        testColumns1.mkString("\t")
      )
      // when
      val nameToTypeMapping = Map("M1" -> AggregationType.DOUBLE_AVG, "M2" -> AggregationType.DOUBLE_AVG)
      val metricDocument: MetricDocument[Tag] = writer.readDocument(testHeaders, rows, StringTag("q=test"), nameToTypeMapping)
      val params1 = Map("p1" -> Seq("p1_v1"), "p2" -> Seq("p2_v1", "p2_v2"))
      val params2 = Map("p1" -> Seq("p1_v2"), "p2" -> Seq("p2_a1", "p2_a2"))
      val row1Metrics: Map[String, MetricValue[Double]] = metricDocument.rows(params1).metrics.asInstanceOf[Map[String, MetricValue[Double]]]
      val row2Metrics: Map[String, MetricValue[Double]] = metricDocument.rows(params2).metrics.asInstanceOf[Map[String, MetricValue[Double]]]
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
      row1M1.biValue.value1.numSamples mustBe 2
      row1M1.biValue.value1.value mustBe Map(ComputeFailReason("JUSTFAILED") -> 1, ComputeFailReason("ANOTHERREASON") -> 2)
      row1M1.biValue.value2.numSamples mustBe 5
      row1M1.biValue.value2.value mustBe "0.43".toDouble
      row1M2.biValue.value1.numSamples mustBe 0
      row1M2.biValue.value1.value mustBe Map.empty
      row1M2.biValue.value2.numSamples mustBe 4
      row1M2.biValue.value2.value mustBe "0.68".toDouble
      // check row2 values
      row2M1.biValue.value1.numSamples mustBe 0
      row2M1.biValue.value1.value mustBe Map.empty
      row2M1.biValue.value2.numSamples mustBe 3
      row2M1.biValue.value2.value mustBe "0.22".toDouble
      row2M2.biValue.value1.numSamples mustBe 0
      row2M2.biValue.value1.value mustBe Map.empty
      row2M2.biValue.value2.numSamples mustBe 4
      row2M2.biValue.value2.value mustBe "0.10".toDouble
    }
  }

  "correctly parse mapping" in {
    // given
    val testString =  "{\"p12\":{\"0\":1.0},\"p15\":{\"2\":1.0},\"p3\":{\"4\":1.0,\"7\":1.0},\"p14\":{\"3\":1.0},\"p17\":{\"1\":1.0},\"p6\":{\"5\":1.0,\"8\":1.0},\"p4\":{\"9\":1.0},\"p5\":{\"6\":1.0}}"
    // when, then
    val result: Map[String, Map[String, Double]] = testString.stripMargin.parseJson.convertTo[Map[String, Map[String, Double]]]
    result mustBe Map(
      "p12" -> Map("0" -> 1.0),
      "p15" -> Map("2" -> 1.0),
      "p3" -> Map("4" -> 1.0, "7" -> 1.0),
      "p14" -> Map("3" -> 1.0),
      "p17" -> Map("1" -> 1.0),
      "p6" -> Map("5" -> 1.0, "8" -> 1.0),
      "p4" -> Map("9" -> 1.0),
      "p5" -> Map("6" -> 1.0),
    )
  }

}
