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


package de.awagen.kolibri.base.usecase.searchopt.processing.plan

import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.domain.ExtTaskDataKeys.{JUDGEMENTS, JUDGEMENT_PROVIDER, PRODUCT_ID_RESULT}
import de.awagen.kolibri.base.usecase.searchopt.domain.ExtTaskFailType.{JudgementProviderMissing, JudgementsMissing, ProductIdsMissing}
import de.awagen.kolibri.base.usecase.searchopt.metrics.{IRMetricFunctions, JudgementHandlingStrategy, Metric, MetricsCalculation}
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.stores.MetricRow.ResultCountStore
import de.awagen.kolibri.datatypes.utils.MathUtils
import de.awagen.kolibri.datatypes.values.Calculations.ComputeResult
import de.awagen.kolibri.datatypes.values.aggregation.AggregateValue

import scala.collection.mutable

class FunctionsSpec extends UnitTestSpec {

  object Fixtures {

    val metricsCalculation: MetricsCalculation = MetricsCalculation(
      metrics = Seq(
        Metric("NDCG@2", IRMetricFunctions.ndcgAtK(2)),
        Metric("NDCG@4", IRMetricFunctions.ndcgAtK(4)),
        Metric("NDCG@5", IRMetricFunctions.ndcgAtK(5))
      ),
      judgementHandling = JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
    )

    val exampleMap: Map[String, Double] = Map(
      "q1-p1" -> 0.1,
      "q1-p2" -> 0.2,
      "q1-p3" -> 0.3,
      "q2-p1" -> 0.1,
      "q2-p3" -> 0.4
    )

    def judgementProvider(judgementMap: Map[String, Double]): JudgementProvider[Double] = new JudgementProvider[Double] {
      override def allJudgements: Map[String, Double] = judgementMap

      override def retrieveJudgement(searchTerm: String, productId: String): Option[Double] = {
        val key = s"$searchTerm-$productId"
        allJudgements.get(key)
      }

      override def retrieveJudgementsForTerm(searchTerm: String): Map[String, Double] = allJudgements
        .map(x => {
          val split = x._1.split("-")
          ((split(0), split(1)), x._2)
        })
        .filter(x => x._1._1 == searchTerm)
        .map(x => (x._1._2, x._2))

      override def retrieveSortedJudgementsForTerm(searchTerm: String, k: Int): Seq[Double] = judgementMap.keys
        .filter(key => key.startsWith(searchTerm))
        .map(key => judgementMap(key))
        .toSeq
        .sorted
        .reverse
        .take(k)

      override def getIdealDCGForTerm(searchTerm: String, k: Int): ComputeResult[Double] = {
        IRMetricFunctions.dcgAtK(k)(retrieveSortedJudgementsForTerm(searchTerm, k))
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

    "correctly apply judgementsToMetricsFunc" in {
      // given
      val exampleData: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      val exampleDataMissingJudgements: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      exampleData.put(JUDGEMENTS, Seq(Some(0.2), Some(0.3), Some(0.1), None))
      // when
      val func = Functions.judgementsToMetricsFunc(Map("p1" -> Seq("v1")), Fixtures.metricsCalculation)
      val result: Either[TaskFailType.TaskFailType, MetricRow] = func.apply(exampleData)
      val resultNoJudgements: Either[TaskFailType.TaskFailType, MetricRow] = func.apply(exampleDataMissingJudgements)
      // then
      resultNoJudgements mustBe Left(JudgementsMissing)
      result match {
        case Right(MetricRow(countStore, params, metrics)) =>
          countStore mustBe new ResultCountStore(1, 0)
          params mustBe Map("p1" -> List("v1"))
          val ndcgAt2: AggregateValue[Double] = metrics("NDCG@2").biValue.value2.asInstanceOf[AggregateValue[Double]]
          val ndcgAt4: AggregateValue[Double] = metrics("NDCG@4").biValue.value2.asInstanceOf[AggregateValue[Double]]
          val ndcgAt5: AggregateValue[Double] = metrics("NDCG@5").biValue.value2.asInstanceOf[AggregateValue[Double]]
          MathUtils.equalWithPrecision(0.9813, ndcgAt2.value, 0.0001f) mustBe true
          MathUtils.equalWithPrecision(0.9838, ndcgAt4.value, 0.0001f) mustBe true
          MathUtils.equalWithPrecision(0.9838, ndcgAt5.value, 0.0001f) mustBe true
        case Left(_) =>
          fail("failed")
      }
    }

  }

}
