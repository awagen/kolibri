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
import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementValidation.{EXIST_JUDGEMENTS, EXIST_RESULTS, JUDGEMENTS_MISSING_AT_MOST_10PERCENT, JudgementValidation}

class JudgementValidationSpec extends UnitTestSpec {

  object Fixture {

    val empty: Seq[Option[Double]] = Seq.empty
    val exist0Percent: Seq[Option[Double]] = Seq(None, None, None)
    val exist80Percent: Seq[Option[Double]] = Seq(Some(0.1), Some(0.0), Some(1.0), Some(0.2), None)
    val exist90Percent: Seq[Option[Double]] = Seq(Some(0.1), Some(0.0), Some(1.0), Some(0.2), None, Some(0.0), Some(0.1), Some(0.2), Some(0.9), Some(0.4))
    val exist100Percent: Seq[Option[Double]] = Seq(Some(0.1), Some(0.0))

    val strategy: JudgementHandlingStrategy = JudgementHandlingStrategy(Seq(EXIST_RESULTS, EXIST_JUDGEMENTS, JUDGEMENTS_MISSING_AT_MOST_10PERCENT),
      MissingValueStrategy.AS_ZEROS)
  }

  "JudgementValidation" must {

    "correctly apply EXIST_RESULT" in {
      EXIST_RESULTS.function.apply(Fixture.empty) mustBe false
      EXIST_RESULTS.function.apply(Fixture.exist0Percent) mustBe true
    }

    "correctly apply EXIST_JUDGEMENTS" in {
      EXIST_JUDGEMENTS.function.apply(Fixture.empty) mustBe false
      EXIST_JUDGEMENTS.function.apply(Fixture.exist0Percent) mustBe false
      EXIST_JUDGEMENTS.function.apply(Fixture.exist80Percent) mustBe true
    }

    "correctly apply JUDGEMENTS_MISSING_AT_MOST_10PERCENT" in {
      JUDGEMENTS_MISSING_AT_MOST_10PERCENT.function.apply(Fixture.empty) mustBe true
      JUDGEMENTS_MISSING_AT_MOST_10PERCENT.function.apply(Fixture.exist0Percent) mustBe false
      JUDGEMENTS_MISSING_AT_MOST_10PERCENT.function.apply(Fixture.exist80Percent) mustBe false
      JUDGEMENTS_MISSING_AT_MOST_10PERCENT.function.apply(Fixture.exist90Percent) mustBe true
      JUDGEMENTS_MISSING_AT_MOST_10PERCENT.function.apply(Fixture.exist100Percent) mustBe true
    }

  }

  "JudgementHandlingStrategy" must {

    "correctly apply validateAndReturnFailed" in {
      // given,  when
      val resultEmpty: Seq[JudgementValidation] = Fixture.strategy.validateAndReturnFailed(Fixture.empty)
      val result0Percent: Seq[JudgementValidation] = Fixture.strategy.validateAndReturnFailed(Fixture.exist0Percent)
      val result80Percent: Seq[JudgementValidation] = Fixture.strategy.validateAndReturnFailed(Fixture.exist80Percent)
      val result90Percent: Seq[JudgementValidation] = Fixture.strategy.validateAndReturnFailed(Fixture.exist90Percent)
      val result100Percent: Seq[JudgementValidation] = Fixture.strategy.validateAndReturnFailed(Fixture.exist100Percent)
      // then
      resultEmpty mustBe Seq(EXIST_RESULTS, EXIST_JUDGEMENTS)
      result0Percent mustBe Seq(EXIST_JUDGEMENTS, JUDGEMENTS_MISSING_AT_MOST_10PERCENT)
      result80Percent mustBe Seq(JUDGEMENTS_MISSING_AT_MOST_10PERCENT)
      result90Percent mustBe Seq.empty
      result100Percent mustBe Seq.empty
    }

    "correctly apply isValid" in {
      // given,  when
      val resultEmpty: Boolean = Fixture.strategy.isValid(Fixture.empty)
      val result0Percent: Boolean = Fixture.strategy.isValid(Fixture.exist0Percent)
      val result80Percent: Boolean = Fixture.strategy.isValid(Fixture.exist80Percent)
      val result90Percent: Boolean = Fixture.strategy.isValid(Fixture.exist90Percent)
      val result100Percent: Boolean = Fixture.strategy.isValid(Fixture.exist100Percent)
      // then
      resultEmpty mustBe false
      result0Percent mustBe false
      result80Percent mustBe false
      result90Percent mustBe true
      result100Percent mustBe true
    }

    "correctly apply extractValues" in {
      // given,  when
      val resultEmpty: Seq[Double] = Fixture.strategy.extractValues(Fixture.empty)
      val result0Percent: Seq[Double] = Fixture.strategy.extractValues(Fixture.exist0Percent)
      val result80Percent: Seq[Double] = Fixture.strategy.extractValues(Fixture.exist80Percent)
      val result90Percent: Seq[Double] = Fixture.strategy.extractValues(Fixture.exist90Percent)
      val result100Percent: Seq[Double] = Fixture.strategy.extractValues(Fixture.exist100Percent)
      // then
      resultEmpty mustBe Seq.empty
      result0Percent mustBe Seq(0.0, 0.0, 0.0)
      result80Percent mustBe Seq(0.1, 0.0, 1.0, 0.2, 0.0)
      result90Percent mustBe Seq(0.1, 0.0, 1.0, 0.2, 0.0, 0.0, 0.1, 0.2, 0.9, 0.4)
      result100Percent mustBe Seq(0.1, 0.0)
    }

  }

}
