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

import de.awagen.kolibri.base.io.utils.TestObjects.{doc1, doc2}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.functions.GeneralSerializableFunctions._
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation


class CSVFormatUtilsSpec extends UnitTestSpec {

  "CSVFormatUtils" must {

    "transform MetricDocument to csv string" in {
      //given
      val expectedHeader1 = "p1\tp2\tp3\tfail-count-metrics1\tweighted-fail-count-metrics1\tfailReasons-metrics1\tsuccess-count-metrics1\tweighted-success-count-metrics1\tvalue-metrics1\tfail-count-metrics2\tweighted-fail-count-metrics2\tfailReasons-metrics2\tsuccess-count-metrics2\tweighted-success-count-metrics2\tvalue-metrics2\tfail-count-metrics3\tweighted-fail-count-metrics3\tfailReasons-metrics3\tsuccess-count-metrics3\tweighted-success-count-metrics3\tvalue-metrics3\tfail-count-metrics4\tweighted-fail-count-metrics4\tfailReasons-metrics4\tsuccess-count-metrics4\tweighted-success-count-metrics4\tvalue-metrics4"
      val expectedRow1 = "v1_1\tv1_2\t\t0\t0.0\t\t1\t1.0\t0.2000\t0\t0.0\t\t1\t1.0\t0.4000\t0\t0.0\t\t0\t0.0\t0.0000\t0\t0.0\t\t0\t0.0\t0.0000"
      val expectedRow2 = "v2_1\tv2_2\t\t0\t0.0\t\t0\t0.0\t0.0000\t0\t0.0\t\t0\t0.0\t0.0000\t0\t0.0\t\t1\t1.0\t0.1000\t0\t0.0\t\t0\t0.0\t0.0000"
      val expectedRow3 = "v3_1\t\tv3_2\t0\t0.0\t\t0\t0.0\t0.0000\t0\t0.0\t\t0\t0.0\t0.0000\t0\t0.0\t\t0\t0.0\t0.0000\t0\t0.0\t\t1\t1.0\t0.3000"
      val expectedDocString = Seq(expectedHeader1, expectedRow1, expectedRow2, expectedRow3).mkString("\n")
      // when
      val actual = CSVFormatUtils.metricDocumentToCsv(doc1)
      actual mustBe expectedDocString
    }

    "transform MetricAggregation to key -> (csv file content) map" in {
      // given
      val aggregationFull: MetricAggregation[String] = MetricAggregation.empty[String](identity)
      doc1.rows.values.foreach(x => aggregationFull.addResults(Set(doc1.id), x))
      doc2.rows.values.foreach(x => aggregationFull.addResults(Set(doc2.id), x))
      //  when
      val result: Map[String, String] = CSVFormatUtils.metricsAggregationFilenameToCsvContentMap[String](aggregationFull, x => s"$x-fin")
      // then
      result("doc1-fin") mustBe CSVFormatUtils.metricDocumentToCsv(doc1)
      result("doc2-fin") mustBe CSVFormatUtils.metricDocumentToCsv(doc2)
    }

  }

}
