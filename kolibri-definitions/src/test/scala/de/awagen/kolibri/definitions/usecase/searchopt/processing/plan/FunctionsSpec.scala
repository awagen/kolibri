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


package de.awagen.kolibri.definitions.usecase.searchopt.processing.plan

import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.definitions.usecase.searchopt.domain.ExtTaskDataKeys.{JUDGEMENT_PROVIDER, PRODUCT_ID_RESULT}
import de.awagen.kolibri.definitions.usecase.searchopt.domain.ExtTaskFailType.{JudgementProviderMissing, ProductIdsMissing}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{IRMetricFunctions, JudgementHandlingStrategy, Metric, MetricsCalculation}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}

import scala.collection.mutable

class FunctionsSpec extends UnitTestSpec {

  object Fixtures {

    val metricsCalculation: MetricsCalculation = MetricsCalculation(
      metrics = Seq(
        Metric("NDCG@2", MetricFunction(MetricType.NDCG, 2, IRMetricFunctions.ndcgAtK(2))),
        Metric("NDCG@4", MetricFunction(MetricType.NDCG, 4, IRMetricFunctions.ndcgAtK(4))),
        Metric("NDCG@5", MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5)))
      ),
      judgementHandling = JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
    )

    val exampleMap: Map[String, Map[String, Double]] = Map(
      "q1" -> Map(
        "p1" -> 0.1,
        "p2" -> 0.2,
        "p3" -> 0.3
      ),
      "q2" -> Map(
        "p1" -> 0.1,
        "p3" -> 0.4
      )
    )

    def judgementProvider(judgementMap: Map[String, Map[String, Double]]): JudgementProvider[Double] = new JudgementProvider[Double] {

      override def retrieveJudgement(searchTerm: String, productId: String): Option[Double] = {
        judgementMap.get(searchTerm).flatMap(x => x.get(productId))
      }

      override def retrieveJudgementsForTerm(searchTerm: String): Map[String, Double] =
        judgementMap.getOrElse(searchTerm, Map.empty)

      override def retrieveSortedJudgementsForTerm(searchTerm: String, k: Int): Seq[Double] = {
        judgementMap.get(searchTerm).map(x => x.values.toSeq.sorted.reverse.take(k)).getOrElse(Seq.empty)
      }
    }
  }

  "Functions" must {

    "correctly provide judgements for query and results" in {
      // given
      val provider = Fixtures.judgementProvider(Fixtures.exampleMap)
      val func = Functions.dataToJudgementsFunc(provider, "q1")
      val exampleData: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      val exampleDataMissingProducts: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      exampleData.put(PRODUCT_ID_RESULT, Seq("p2", "p3", "p1", "pNot"))
      // when
      val result: Either[TaskFailType.TaskFailType, Seq[Option[Double]]] = func.apply(exampleData)
      val resultMissingProducts: Either[TaskFailType.TaskFailType, Seq[Option[Double]]] = func.apply(exampleDataMissingProducts)
      result mustBe Right(Seq(Some(0.2), Some(0.3), Some(0.1), None))
      resultMissingProducts mustBe Left(ProductIdsMissing)
    }

    "correctly retrieve judgements from provider in result map" in {
      // given
      val provider = Fixtures.judgementProvider(Fixtures.exampleMap)
      val exampleData: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      val exampleDataMissingProducts: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      val exampleDataMissingProductsJudgementProvider: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      exampleData.put(PRODUCT_ID_RESULT, Seq("p2", "p3", "p1", "pNot"))
      exampleData.put(JUDGEMENT_PROVIDER, provider)
      exampleDataMissingProducts.put(JUDGEMENT_PROVIDER, provider)
      exampleDataMissingProductsJudgementProvider.put(PRODUCT_ID_RESULT, Seq("p2"))
      // when
      val result = Functions.judgementRetrievalFunc("q1").apply(exampleData)
      val resultNoProducts = Functions.judgementRetrievalFunc("q1").apply(exampleDataMissingProducts)
      val resultNoJudgements = Functions.judgementRetrievalFunc("q1").apply(exampleDataMissingProductsJudgementProvider)
      // then
      result mustBe Right(Seq(Some(0.2), Some(0.3), Some(0.1), None))
      resultNoProducts mustBe Left(ProductIdsMissing)
      resultNoJudgements mustBe Left(JudgementProviderMissing)
    }
  }
}
