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

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations._
import de.awagen.kolibri.base.usecase.searchopt.metrics.CalculationsTestHelper._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Functions._
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.utils.MathUtils
import de.awagen.kolibri.datatypes.values.AggregateValue

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class CalculationsSpec extends UnitTestSpec {

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

  "BaseBooleanCalculation" must {

    "correctly calculate metrics" in {
      // given
      val calculation = BaseBooleanCalculation("testCalc", x => Right(x.count(y => y)))
      // when
      val result: CalculationResult[Double] = calculation.apply(Seq(true, true, false, false))
      // then
      result.getOrElse(-1) mustBe 2
    }

  }

  "FromMapCalculation" must {

    "correctly calculate metrics" in {
      // given
      val calculation = FromMapCalculation[Seq[Int], Int]("testCalc", "key1", x => Right(x.sum))
      // when, then
      calculation.apply(BaseWeaklyTypedMap(mutable.Map("key1" -> Seq(1, 2, 3)))).getOrElse(-1) mustBe 6
    }

  }

  "FromMapFutureCalculation" must {
    implicit val ec: ExecutionContext = global

    "correctly calculate metrics" in {
      // given
      val calculation: FutureCalculation[WeaklyTypedMap[String], String, Int] = new FutureCalculation[WeaklyTypedMap[String], String, Int] {
        override val name: String = "testCalc"
        override val calculationResultIdentifier: String = "key1Sum"

        override def apply(in: WeaklyTypedMap[String])(implicit ec: ExecutionContext): Future[Int] = {
          Future {
            in.get[Seq[Int]]("key1").get.sum
          }
        }
      }
      val futureCalculation = FromMapFutureCalculation("testCalc", "key1Sum", calculation)
      // when
      val futureResult: Future[Int] = futureCalculation.apply(BaseWeaklyTypedMap(mutable.Map("key1" -> Seq(3, 2))))
      Await.result(futureResult, 1 second) mustBe 5
    }

  }

  "Functions" must {

    "correctly apply throwableToMetricRowResponse" in {
      // given, when
      val params = Map("p1" -> Seq("p1v1"), "p2" -> Seq("p2v1"))
      val result: MetricRow = throwableToMetricRowResponse(
        e = new RuntimeException("ups"),
        valueNames = Set("val1", "val2"),
        params = params)
      val val1Result = result.metrics("val1").biValue
      val val2Result = result.metrics("val2").biValue
      val val1FailReasonKey = val1Result.value1.value.keySet.head
      val val2FailReasonKey = val2Result.value1.value.keySet.head
      // then
      result.params mustBe params
      result.metrics.keySet mustBe Set("val1", "val2")
      val1Result.value2.count mustBe 0
      val2Result.value2.count mustBe 0
      val1Result.value1.count mustBe 1
      val1FailReasonKey.description mustBe "java.lang.RuntimeException"
      val1Result.value1.value(val1FailReasonKey) mustBe 1
      val2Result.value1.count mustBe 1
      val2FailReasonKey.description mustBe "java.lang.RuntimeException"
      val2Result.value1.value(val2FailReasonKey) mustBe 1
    }

    "correctly apply computeFailReasonsToMetricRowResponse" in {
      // given
      val failReasons = Seq(
        de.awagen.kolibri.datatypes.reason.ComputeFailReason.NO_RESULTS,
        de.awagen.kolibri.datatypes.reason.ComputeFailReason.ZERO_DENOMINATOR
      )
      val params = Map("p1" -> Seq("p1v1"), "p2" -> Seq("p2v1"))
      // when
      val metricRow: MetricRow = computeFailReasonsToMetricRowResponse(failReasons, "testMetric", params)
      val metricResult = metricRow.metrics("testMetric").biValue
      // then
      metricResult.value1.count mustBe 2
      metricResult.value2.count mustBe 0
      metricResult.value1.value(de.awagen.kolibri.datatypes.reason.ComputeFailReason.NO_RESULTS) mustBe 1
      metricResult.value1.value(de.awagen.kolibri.datatypes.reason.ComputeFailReason.ZERO_DENOMINATOR) mustBe 1
    }

    "correctly apply resultEitherToMetricRowResponse" in {
      // given
      val params = Map("p1" -> Seq("p1v1"), "p2" -> Seq("p2v1"))
      // when
      val metricRow1 = resultEitherToMetricRowResponse(
        "testMetric",
        Right(0.2),
        params
      )
      val metricRow2 = resultEitherToMetricRowResponse(
        "testMetric",
        Left[Seq[ComputeFailReason], Double](Seq(new ComputeFailReason("runtimeException"))),
        params
      )
      val row1Result = metricRow1.metrics("testMetric").biValue
      val row2Result = metricRow2.metrics("testMetric").biValue
      // then
      row1Result.value1.count mustBe 0
      row1Result.value2.count mustBe 1
      row2Result.value1.count mustBe 1
      row2Result.value2.count mustBe 0
      row1Result.value2.value mustBe 0.2
      row2Result.value1.value.keySet.map(x => x.description) mustBe Set("runtimeException")

    }

    "correctly apply findFirstValue" in {
      findFirstValue(false).apply(Seq(true, true, false, true)).getOrElse(-1) mustBe 2
      findFirstValue(true).apply(Seq(true, true, false, true)).getOrElse(-1) mustBe 0
    }

    "correctly apply countValues" in {
      countValues(false).apply(Seq(true, false, false, true, true)).getOrElse(-1) mustBe 2
      countValues(true).apply(Seq(true, false, false, true, true)).getOrElse(-1) mustBe 3
    }

    "correctly apply binarizeBooleanSeq" in {
      binarizeBooleanSeq(invert = false, Seq(true, true, false)) mustBe Seq(1.0, 1.0, 0.0)
      binarizeBooleanSeq(invert = true, Seq(true, true, false)) mustBe Seq(0.0, 0.0, 1.0)
    }

    "correctly apply booleanPrecision" in {
      booleanPrecision(useTrue = true, 3).apply(Seq(true, true, false, true)).getOrElse(-1) mustBe 2.0 / 3.0
      booleanPrecision(useTrue = false, 2).apply(Seq(true, false, false, true)).getOrElse(-1) mustBe 0.5
    }

  }


}
