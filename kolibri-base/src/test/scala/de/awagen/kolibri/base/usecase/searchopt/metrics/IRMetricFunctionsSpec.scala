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
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.CalculationResult
import de.awagen.kolibri.base.usecase.searchopt.metrics.IRMetricFunctions.NO_JUDGEMENTS
import de.awagen.kolibri.datatypes.utils.MathUtils

class IRMetricFunctionsSpec extends UnitTestSpec {

  "IRMetricFunctions" should {

    val sample1: Seq[Double] = Seq(0.8, 0.0, 0.9, 0.5)

    "DcgAtK" must {
      "correctly calculate" in {
        //given, when
        val score = IRMetricFunctions.dcgAtK(10).apply(sample1).right.get
        val expectedScore = 0.8 + 0.9 / Math.log(4) + 0.5 / Math.log(5)
        //then
        MathUtils.equalWithPrecision(score, expectedScore, 0.00001) mustBe true
      }

      "return None for no element" in {
        //given, when
        val score: CalculationResult[Double] = IRMetricFunctions.dcgAtK(10).apply(Seq())
        //then
        score mustBe Left(Seq(NO_JUDGEMENTS))
      }
    }

    "NdcgAtK" must {
      "correctly calculate" in {
        //given, when
        val score = IRMetricFunctions.ndcgAtK(10).apply(sample1).right.get
        val expectedScore = (0.8 + 0.9 / Math.log(4) + 0.5 / Math.log(5)) / (0.9 + 0.8 / Math.log(3) + 0.5 / Math.log(4))
        //then
        MathUtils.equalWithPrecision(score, expectedScore, 0.00001) mustBe true
      }

      "return None for no element" in {
        //given, when
        val score = IRMetricFunctions.ndcgAtK(10).apply(Seq())
        //then
        score mustBe Left(Seq(NO_JUDGEMENTS))
      }
    }

    "PrecisionAtK" must {
      "correcty caculate" in {
        //given, when
        val score1 = IRMetricFunctions.precisionAtK(10, 0.5).apply(sample1).right.get
        val score2 = IRMetricFunctions.precisionAtK(10, 0.6).apply(sample1).right.get
        //then
        MathUtils.equalWithPrecision(score1, 3.0 / 4, 0.001) mustBe true
        MathUtils.equalWithPrecision(score2, 1.0 / 2, 0.001) mustBe true
      }

      "return None for no element" in {
        //given, when
        val score = IRMetricFunctions.precisionAtK(10, 0.5).apply(Seq())
        //then
        score mustBe Left(Seq(NO_JUDGEMENTS))
      }

      "RecallAtK" must {
        "correcty caculate" in {
          //given, when
          val score1 = IRMetricFunctions.recallAtK(10, 0.5).apply(sample1).right.get
          val score2 = IRMetricFunctions.recallAtK(10, 0.6).apply(sample1).right.get
          val score3 = IRMetricFunctions.recallAtK(3, 0.6).apply(Seq(0.8, 0.0, 0.9, 0.5)).right.get
          val score4 = IRMetricFunctions.recallAtK(2, 0.6).apply(Seq(0.8, 0.0, 0.9, 0.5)).right.get
          //then
          MathUtils.equalWithPrecision(score1, 1.0, 0.001) mustBe true
          MathUtils.equalWithPrecision(score2, 1.0, 0.001) mustBe true
          MathUtils.equalWithPrecision(score3, 1.0, 0.001) mustBe true
          MathUtils.equalWithPrecision(score4, 0.5, 0.001) mustBe true
        }

        "return None for no element" in {
          //given, when
          val score = IRMetricFunctions.precisionAtK(10, 0.5).apply(Seq())
          //then
          score mustBe Left(Seq(NO_JUDGEMENTS))
        }
      }

      "ERR_3" must {
        "correctly calculate" in {
          //given, when
          val score = IRMetricFunctions.errAtK(10, 3.0).apply(sample1).right.get
          val probs = Seq((Math.pow(2, 0.8 * 3.0) - 1) / 8.0, 0.0, (Math.pow(2, 0.9 * 3.0) - 1) / 8.0, (Math.pow(2, 0.5 * 3.0) - 1) / 8.0)
          var expectedScore = 0.0
          var remainingProbability = 1.0
          probs.indices.foreach(
            x => {
              val prob = probs(x)
              expectedScore += remainingProbability * prob / (x + 1.0)
              remainingProbability = remainingProbability * (1.0 - prob)
            }
          )
          //then
          MathUtils.equalWithPrecision(score, expectedScore, 0.00001) mustBe true
        }

        "return None for no element" in {
          //given, when
          val score = IRMetricFunctions.errAtK(10, 3.0).apply(Seq())
          //then
          score mustBe Left(Seq(NO_JUDGEMENTS))
        }
      }
    }

  }

}
