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

class ReceiveCountExpectationSpec extends UnitTestSpec {

  "ReceiveCountExpectation" must {

    "correctly manage element count expectation" in {
      // given
      val countExpectation: ReceiveCountExpectation = ReceiveCountExpectation(Map(1 -> 3, "msg1" -> 3, "msg2" -> 6))
      // when, then
      countExpectation.succeeded mustBe false
      countExpectation.accept(1)
      countExpectation.accept(1)
      countExpectation.accept(1)
      countExpectation.accept("msg1")
      countExpectation.succeeded mustBe false
      countExpectation.accept("msg1")
      countExpectation.accept("msg1")
      countExpectation.accept("msg2")
      countExpectation.accept("msg2")
      countExpectation.accept("msg2")
      countExpectation.accept("msg2")
      countExpectation.accept("msg2")
      countExpectation.succeeded mustBe false
      countExpectation.accept("msg2")
      countExpectation.succeeded mustBe true
    }

  }

}
