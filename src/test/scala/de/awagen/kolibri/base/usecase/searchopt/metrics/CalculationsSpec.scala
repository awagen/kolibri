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


package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{CalculationResult, JudgementBasedMetricsCalculation}
import de.awagen.kolibri.base.usecase.searchopt.provider.FileBasedJudgementProviderFactory
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.utils.MathUtils
import de.awagen.kolibri.datatypes.values.AggregateValue

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class CalculationsSpec extends UnitTestSpec {

  val CALCULATION_NAME = "testCalc"
  val QUERY_PARAM = "q"
  val REQUEST_TEMPLATE_KEY = "template"
  val PRODUCT_IDS_KEY = "pids"
  val NDCG2_NAME = "NDCG_2"
  val NDCG5_NAME = "NDCG_5"
  val NDCG10_NAME = "NDCG_10"

  def getJudgementBasedMetricsCalculation(judgementFilePath: String,
                                          metrics: Seq[Metric]): JudgementBasedMetricsCalculation = {
    JudgementBasedMetricsCalculation(
      CALCULATION_NAME,
      QUERY_PARAM,
      REQUEST_TEMPLATE_KEY,
      PRODUCT_IDS_KEY,
      FileBasedJudgementProviderFactory(judgementFilePath),
      MetricsCalculation(
        metrics,
        JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
      ),
      excludeParamsFromMetricRow = Seq(QUERY_PARAM)
    )
  }

  def requestTemplateForQuery(query: String): RequestTemplate = {
    RequestTemplate("/", Map(QUERY_PARAM -> Seq(query)), Seq.empty)
  }

  "JudgementBasedMetricsCalculation" must {
    implicit val ec: ExecutionContext = global

    "correctly calculate metrics" in {
      // given
      val calculation = getJudgementBasedMetricsCalculation("data/calculations_test_judgements.txt",
        Seq(
          Metric(NDCG5_NAME, IRMetricFunctions.ndcgAtK(5)),
          Metric(NDCG10_NAME, IRMetricFunctions.ndcgAtK(10)),
          Metric(NDCG2_NAME, IRMetricFunctions.ndcgAtK(2))
        )
      )
      val inputData: WeaklyTypedMap[String] = BaseWeaklyTypedMap(mutable.Map.empty)
      inputData.put(REQUEST_TEMPLATE_KEY, requestTemplateForQuery("q0"))
      // p4 does not exist in the judgement list and as per above judgement handling strategy
      // is treated as 0.0
      inputData.put(PRODUCT_IDS_KEY, Seq("p0", "p3", "p2", "p1", "p4"))
      // when
      val calcResult: MetricRow = Await.result(calculation.apply(inputData), 5 seconds)
      val ndcg5Result: AggregateValue[Double] = calcResult.metrics(NDCG5_NAME).biValue.value2
      val ndcg10Result: AggregateValue[Double] = calcResult.metrics(NDCG10_NAME).biValue.value2
      val ndcg2Result: AggregateValue[Double] = calcResult.metrics(NDCG2_NAME).biValue.value2
      val expectedNDCG2Result: CalculationResult[Double] = IRMetricFunctions.ndcgAtK(2).apply(Seq(0.10, 0.4, 0.3, 0.2, 0.0))
      val expectedNDCG5Result: CalculationResult[Double] = IRMetricFunctions.ndcgAtK(5).apply(Seq(0.10, 0.4, 0.3, 0.2, 0.0))
      // then
      Seq(ndcg2Result.count, ndcg5Result.count, ndcg10Result.count) mustBe Seq(1, 1, 1)
      MathUtils.equalWithPrecision[Double](ndcg2Result.value, expectedNDCG2Result.getOrElse(-1.0), 0.0001) mustBe true
      MathUtils.equalWithPrecision[Double](ndcg5Result.value, expectedNDCG5Result.getOrElse(-1.0), 0.0001) mustBe true
      MathUtils.equalWithPrecision[Double](ndcg5Result.value, ndcg10Result.value, 0.0001) mustBe true
    }


  }


}
