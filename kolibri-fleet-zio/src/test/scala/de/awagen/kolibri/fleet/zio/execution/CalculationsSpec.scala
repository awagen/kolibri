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


package de.awagen.kolibri.fleet.zio.execution

import akka.util.Timeout
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.utils.MathUtils
import de.awagen.kolibri.datatypes.values.Calculations.{ComputeResult, ResultRecord}
import de.awagen.kolibri.definitions.directives.{Resource, ResourceDirectives, ResourceType}
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys._
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations._
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{IRMetricFunctions, JudgementHandlingStrategy, Metric, MetricsCalculation}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{JudgementInfo, JudgementProvider}
import de.awagen.kolibri.fleet.zio.config.AppConfig.filepathToJudgementProvider
import de.awagen.kolibri.fleet.zio.execution.CalculationsSpec._
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.testclasses.UnitTestSpec

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}


object CalculationsSpec {

  def judgementsToSuccessJudgementInfo(judgements: Seq[Double]): JudgementInfo = {
    JudgementInfo(
      "testQuery",
      products = judgements.indices.map(index => s"p$index"),
      judgements,
      judgements.sorted.reverse,
      Seq.empty
    )
  }

  def requestTemplateForQuery(query: String): RequestTemplate = {
    RequestTemplate("/", Map(QUERY_PARAM -> Seq(query)), Seq.empty)
  }

  val CALCULATION_NAME = "testCalc"
  val QUERY_PARAM = "q"
  val REQUEST_TEMPLATE_KEY = "template"
  val PRODUCT_IDS_KEY = "pids"
  val NDCG2_NAME = "NDCG_2"
  val NDCG5_NAME = "NDCG_5"
  val NDCG10_NAME = "NDCG_10"

}

class CalculationsSpec extends UnitTestSpec {

  def prepareJudgementResource(implicit ec: ExecutionContext): Unit = {
    val judgementSupplier = new SerializableSupplier[JudgementProvider[Double]] {
      override def apply(): JudgementProvider[Double] = {
        filepathToJudgementProvider("data/calculations_test_judgements.txt")
      }
    }
    val judgementResourceDirective: ResourceDirectives.ResourceDirective[JudgementProvider[Double]] = ResourceDirectives.getDirective(
      judgementSupplier,
      Resource(ResourceType.JUDGEMENT_PROVIDER, "judgements1")
    )
    implicit val timeout: Timeout = 5 seconds
    val resourceAsk: Promise[Any] = NodeResourceProvider.createResource(judgementResourceDirective)
    Await.result(resourceAsk.future, timeout.duration)
  }



  "JudgementBasedMetricsCalculation" must {
    implicit val ec: ExecutionContext = global

    "correctly calculate metrics" in {
      // given
      prepareJudgementResource
      val calculation = JudgementsFromResourceIRMetricsCalculations(
        "pids",
        REQUEST_TEMPLATE_STORAGE_KEY.name,
        "q",
        Resource(ResourceType.JUDGEMENT_PROVIDER, "judgements1"),
        MetricsCalculation(
          Seq(
            Metric(NDCG5_NAME, MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5))),
            Metric(NDCG10_NAME, MetricFunction(MetricType.NDCG, 10, IRMetricFunctions.ndcgAtK(10))),
            Metric(NDCG2_NAME, MetricFunction(MetricType.NDCG, 2, IRMetricFunctions.ndcgAtK(2)))
          ),
          JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
        ),
        NodeResourceProvider
      )
      val inputData: WeaklyTypedMap[String] = BaseWeaklyTypedMap(mutable.Map.empty)
      inputData.put(REQUEST_TEMPLATE_STORAGE_KEY.name, requestTemplateForQuery("q0"))
      // p4 does not exist in the judgement list and as per above judgement handling strategy
      // is treated as 0.0
      inputData.put(PRODUCT_IDS_KEY, Seq("p0", "p3", "p2", "p1", "p4"))
      // when

      val calcResult: Seq[ResultRecord[_]] = calculation.calculation.apply(inputData)
      val ndcg5Result: ComputeResult[_] = calcResult.find(x => x.name == NDCG5_NAME).get.value
      val ndcg10Result: ComputeResult[_] = calcResult.find(x => x.name == NDCG10_NAME).get.value
      val ndcg2Result: ComputeResult[_] = calcResult.find(x => x.name == NDCG2_NAME).get.value
      val expectedNDCG2Result: ComputeResult[Double] = IRMetricFunctions.ndcgAtK(2).apply(
        judgementsToSuccessJudgementInfo(Seq(0.10, 0.4, 0.3, 0.2, 0.0)))
      val expectedNDCG5Result: ComputeResult[Double] = IRMetricFunctions.ndcgAtK(5).apply(
        judgementsToSuccessJudgementInfo(Seq(0.10, 0.4, 0.3, 0.2, 0.0)))
      // then
      MathUtils.equalWithPrecision[Double](
        ndcg2Result.getOrElse[Any](-10).asInstanceOf[Double],
        expectedNDCG2Result.getOrElse[Any](-1.0).asInstanceOf[Double], 0.0001) mustBe true
      MathUtils.equalWithPrecision[Double](
        ndcg5Result.getOrElse[Any](-10).asInstanceOf[Double],
        expectedNDCG5Result.getOrElse[Any](-1.0).asInstanceOf[Double], 0.0001) mustBe true
      MathUtils.equalWithPrecision[Double](ndcg5Result.getOrElse[Any](-10).asInstanceOf[Double],
        ndcg10Result.getOrElse[Any](-1).asInstanceOf[Double], 0.0001) mustBe true
    }
  }

}
