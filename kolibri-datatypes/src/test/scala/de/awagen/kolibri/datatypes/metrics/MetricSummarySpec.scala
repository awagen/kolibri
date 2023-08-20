/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.metrics

import de.awagen.kolibri.datatypes.metrics.MetricSummary.BestAndWorstConfigs
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricsHelper._
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.utils.MathUtils
import spray.json._

class MetricSummarySpec extends UnitTestSpec {

  val rows: Seq[MetricRow] = Seq(
    metricRecordWParams1,
    metricRecordWParams2,
    metricRecordWParams3,
    metricRecordWParams4,
    metricRecordWParams5
  )

  val expectedSummaryValue: MetricSummary = MetricSummary(
    BestAndWorstConfigs((Map("p1" -> Seq("v3"), "p2" -> Seq("0.0")), 0.6), (Map("p1" -> Seq("v4")), 0.3)),
    Map("p1" -> 0.3, "p2" -> 0.0)
  )

  val expectedSummaryValueJson: JsValue =
    """
      |{
      |  "bestAndWorstConfigs": {
      |     "best": [{"p1": ["v3"], "p2": ["0.0"]}, 0.6],
      |     "worst": [{"p1": ["v4"]}, 0.3]
      |  },
      |  "parameterEffectEstimate": {
      |    "p1": 0.3,
      |    "p2": 0.0
      |  }
      |}
      |""".stripMargin.parseJson

  "MetricSummary" must {

    "correctly determine best and worst config" in {
      // given, when
      val bestAndWorst = MetricSummary.calculateBestAndWorstConfigs(rows, "metrics1")
      // then
      bestAndWorst.best mustBe (Map("p1" -> Seq("v3"), "p2" -> Seq("0.0")), 0.6)
      bestAndWorst.worst mustBe (Map("p1" -> Seq("v4")), 0.3)
    }

    "correctly calculate effect estimate" in {
      // given, when
      val estimate = MetricSummary.calculateParameterEffectEstimate(rows, "metrics1")
      // then
      MathUtils.equalWithPrecision(estimate("p1"), 0.3, 0.00001) mustBe true
      MathUtils.equalWithPrecision(estimate("p2"), 0.0, 0.00001) mustBe true
    }

    "correctly calculate summary" in {
      // given, when
      val summary = MetricSummary.summarize(Seq((StringTag("t1"), rows)), "metrics1")
      // then
      summary mustBe Map(StringTag("t1") -> expectedSummaryValue)
    }

    "correctly write json" in {
      // given, when, then
      expectedSummaryValue.toJson.toString() mustBe expectedSummaryValueJson.toString()
    }

  }

}
