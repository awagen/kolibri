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

package de.awagen.kolibri.base.processing.execution.expectation

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

class StopExpectationSpec extends UnitTestSpec {

  "StopExpectation" must {

    "correctly determine when condition is met" in {
      // given
      // classifier classifying message as error if its Int and even
      val errorClassifier: SerializableFunction1[Any, Boolean] =  {
        case e: Int if e % 2 == 0 => true
        case _ => false
      }
      // stopCriterion that is fulfilled if more or less than 40 % of overall expected messages correspond to error
      val stopCriterion: SerializableFunction1[(Int, Int), Boolean] = {
        case e if BigDecimal(e._2.toFloat / e._1).setScale(2, BigDecimal.RoundingMode.HALF_UP) >= 0.4 => true
        case _ => false
      }
      val expectation = StopExpectation(10, errorClassifier, stopCriterion)
      // when, then
      expectation.accept(1)
      expectation.accept(2)
      expectation.accept(4)
      expectation.accept(6)
      expectation.succeeded mustBe false
      expectation.accept(6)
      expectation.succeeded mustBe true
    }

  }

}
