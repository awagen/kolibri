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


package de.awagen.kolibri.datatypes.utils

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.utils.PermutationUtils.{findFirstNonMaxIndex, findNNextElementsFromPosition, findNthElementForwardCalc, generateFirstParameterIndices, generateNextParameters, increaseByStepAndResetPrevious, iterateFirstTillMax, stepsForNthElementBackwardCalc}

class PermutationUtilsSpec extends UnitTestSpec {

  "PermutationUtils" must {

    "correctly find first non-max index" in {
      // given, when
      val allAtMax: Option[Int] = findFirstNonMaxIndex(Seq(2, 2, 3), Seq(3, 3, 4))
      val secondIsNonMax: Option[Int] = findFirstNonMaxIndex(Seq(2, 1, 3), Seq(3, 3, 4))
      // then
      allAtMax mustBe None
      secondIsNonMax mustBe Some(1)
    }

    "correctly iterate first till max" in {
      // given, when
      val values: Seq[Seq[Int]] = iterateFirstTillMax(Seq(1, 0, 2), Seq(5, 4, 4))
      // then
      values mustBe Seq(Seq(2, 0, 2), Seq(3, 0, 2), Seq(4, 0, 2))
    }

    "correctly iterate first till max with limit" in {
      // given, when
      val values: Seq[Seq[Int]] = iterateFirstTillMax(Seq(1, 0, 2), Seq(10, 4, 4), 4)
      // then
      values mustBe Seq(Seq(2, 0, 2), Seq(3, 0, 2), Seq(4, 0, 2), Seq(5, 0, 2))
    }

    "correctly increase by step and reset previous" in {
      // given, when
      val values: Seq[Int] = increaseByStepAndResetPrevious(Seq(2, 3, 1, 4), 2)
      // then
      values mustBe Seq(0, 0, 2, 4)
    }

    "correctly generate first n parameter indices" in {
      // given, when
      val values: Seq[Seq[Int]] = generateFirstParameterIndices(4, Seq(2, 3, 4))
      // then
      values mustBe Seq(Seq(0, 0, 0), Seq(1, 0, 0), Seq(0, 1, 0), Seq(1, 1, 0))
    }

    "correctly generate next n parameters" in {
      // given, when
      val values = generateNextParameters(seq = Seq(1, 3, 2, 1),
        nrOfValuesPerParameter = Seq(2, 4, 4, 4), nr = 5)
      // then
      values mustBe Seq(Seq(0, 0, 3, 1), Seq(1, 0, 3, 1), Seq(0, 1, 3, 1), Seq(1, 1, 3, 1), Seq(0, 2, 3, 1))
    }

    "correctly find n next elements from position" in {
      // given, when
      val values: Seq[Seq[Int]] = findNNextElementsFromPosition(nrOfValuesPerParameter = Seq(2, 2, 2, 2), 0, 4)
      // then
      values mustBe Seq(Seq(0, 0, 0, 0), Seq(1, 0, 0, 0), Seq(0, 1, 0, 0), Seq(1, 1, 0, 0))
    }

    "correctly find nth element" in {
      // given, when
      val values_0: Option[Seq[Int]] = findNthElementForwardCalc(nrOfValuesPerParameter = Seq(2, 2, 2, 2), 0)
      val values_3: Option[Seq[Int]] = findNthElementForwardCalc(nrOfValuesPerParameter = Seq(2, 2, 2, 2), 3)
      val values_4: Option[Seq[Int]] = findNthElementForwardCalc(nrOfValuesPerParameter = Seq(2, 2, 2, 2), 4)
      // then
      values_0 mustBe Some(Seq(0, 0, 0, 0))
      values_3 mustBe Some(Seq(1, 1, 0, 0))
      values_4 mustBe Some(Seq(0, 0, 1, 0))
    }

    "correctly identify steps for n-th element starting from first parameter" in {
      // given, when
      val values_0 = stepsForNthElementBackwardCalc(Seq(2, 2, 2, 2), 0)
      val values_1 = stepsForNthElementBackwardCalc(Seq(2, 2, 2, 2), 1)
      val values_2 = stepsForNthElementBackwardCalc(Seq(2, 2, 2, 2), 2)
      val values_3 = stepsForNthElementBackwardCalc(Seq(2, 2, 2, 2), 3)
      val values_4 = stepsForNthElementBackwardCalc(Seq(2, 2, 2, 2), 4)
      // then
      values_0 mustBe List((0, 0))
      values_1 mustBe List((0, 1))
      values_2 mustBe List((0, 0), (1, 1))
      values_3 mustBe List((0, 1), (1, 1))
      values_4 mustBe List((0, 0), (2, 1))
    }

  }

}
