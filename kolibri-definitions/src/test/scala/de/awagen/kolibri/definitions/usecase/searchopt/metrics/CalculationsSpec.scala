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


package de.awagen.kolibri.definitions.usecase.searchopt.metrics

import de.awagen.kolibri.datatypes.mutable.stores.BaseWeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.values.Calculations.ResultRecord
import de.awagen.kolibri.datatypes.values.RunningValues
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations.{BooleanSeqToDoubleCalculation, FromMapCalculation, FromTwoMapsCalculation}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.ComputeResultFunctions.{booleanPrecision, countValues, findFirstValue}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricRowFunctions.throwableToMetricRowResponse
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.PlainMetricValueFunctions.binarizeBooleanSeq
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.TwoInComputeResultFunctions.jaccardSimilarity

import scala.collection.mutable

class CalculationsSpec extends UnitTestSpec {

  "BaseBooleanCalculation" must {

    "correctly calculate metrics" in {
      // given
      val calculation = BooleanSeqToDoubleCalculation(Set("testCalc"), x => Seq(ResultRecord("testCalc", Right(x.count(y => y)))))
      // when
      val result: Seq[ResultRecord[Double]] = calculation.calculation.apply(Seq(true, true, false, false))
      // then
      result.head.value.getOrElse(-1) mustBe 2
    }

  }

  "FromMapCalculation" must {

    "correctly calculate metrics" in {
      // given
      val calculation: FromMapCalculation[Seq[Int], Int] = FromMapCalculation[Seq[Int], Int](Set("testCalc"), "key1", x => Right(x.sum))
      // when, then
      val result: Seq[ResultRecord[Int]] = calculation.calculation.apply(BaseWeaklyTypedMap(mutable.Map("key1" -> Seq(1, 2, 3))))
      result.head.value.getOrElse(-1) mustBe 6
    }

  }

  "Functions" must {

    "correctly apply throwableToMetricRowResponse" in {
      // given, when
      val params = Map("p1" -> Seq("p1v1"), "p2" -> Seq("p2v1"))
      val result: MetricRow = throwableToMetricRowResponse(
        e = new RuntimeException("ups"),
        valueNamesToEmptyAggregationValuesMap = Map(
          "val1" -> RunningValues.doubleAvgRunningValue(0.0, 0, 0),
          "val2" -> RunningValues.doubleAvgRunningValue(0.0, 0, 0)
        ),
        params = params)
      val val1Result = result.metrics("val1").biValue
      val val2Result = result.metrics("val2").biValue
      val val1FailReasonKey = val1Result.value1.value.keySet.head
      val val2FailReasonKey = val2Result.value1.value.keySet.head
      // then
      result.params mustBe params
      result.metrics.keySet mustBe Set("val1", "val2")
      val1Result.value2.numSamples mustBe 0
      val2Result.value2.numSamples mustBe 0
      val1Result.value1.numSamples mustBe 1
      val1FailReasonKey.description mustBe "java.lang.RuntimeException"
      val1Result.value1.value(val1FailReasonKey) mustBe 1
      val2Result.value1.numSamples mustBe 1
      val2FailReasonKey.description mustBe "java.lang.RuntimeException"
      val2Result.value1.value(val2FailReasonKey) mustBe 1
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

  "TwoInComputeResultFunctions" must {

    "calculate jaccardSimilarity" in {
      jaccardSimilarity.apply(Seq(1, 5, 3, 2), Seq(1, 6)) .getOrElse(-1) mustBe 1.0 / 5.0
    }

  }

  "FromTwoMapsCalculation" must {

    "calculate value typed map with two map-values" in {
      // given
      val calc: FromTwoMapsCalculation[Seq[String], Seq[String], Double] = FromTwoMapsCalculation("compare1", "data1", "data2", jaccardSimilarity)
      // when
      val result: ResultRecord[Double] = calc.calculation.apply(
        BaseWeaklyTypedMap(mutable.Map("data1" -> Seq("a", "b", "c"))),
        BaseWeaklyTypedMap(mutable.Map("data2" -> Seq("d", "a", "c")))
      ).head
      // then
      result.value.getOrElse(-1) mustBe 1.0/2.0
    }

  }

}
