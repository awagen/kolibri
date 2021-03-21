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

class ClassifyingCountExpectationSpec extends UnitTestSpec {

  "ClassifyingCountExpectation" must {

    "correctly classify messages" in {
      // given
      val classifier: Map[String, SerializableFunction1[Any, Boolean]] = Map("int" -> (x => x.isInstanceOf[Int]), "string" -> (x => x.isInstanceOf[String]))
      val classCounts: Map[String, Int] = Map("int" -> 2, "string" -> 1)
      val expectation = ClassifyingCountExpectation(classifier = classifier, expectedClassCounts = classCounts)
      // when, then
      expectation.succeeded mustBe false
      expectation.getUnfulfilledClasses mustBe Seq("int", "string")
      expectation.accept(1)
      expectation.getUnfulfilledClasses mustBe Seq("int", "string")
      expectation.accept(2)
      expectation.getUnfulfilledClasses mustBe Seq("string")
      expectation.succeeded mustBe false
      expectation.accept("s1")
      expectation.getUnfulfilledClasses mustBe Seq()
      expectation.succeeded mustBe true
    }

    "throw AssertionError in case there is unmeetable expectation" in {
      // given
      val classifier: Map[String, SerializableFunction1[Any, Boolean]] = Map("int" -> (x => x.isInstanceOf[Int]), "string" -> (x => x.isInstanceOf[String]))
      val singleElementExpectations: Map[String, Int] = Map("int" -> 2, "string" -> 1, "other" -> 1)
      // when, then
      assertThrows[AssertionError] {
        ClassifyingCountExpectation(classifier = classifier, expectedClassCounts = singleElementExpectations)
      }
    }

    "throw NO AssertionError in case there is expectation without classifier but expectation is 0" in {
      // given
      val classifier: Map[String, SerializableFunction1[Any, Boolean]] = Map("int" -> (x => x.isInstanceOf[Int]), "string" -> (x => x.isInstanceOf[String]))
      val singleElementExpectations: Map[String, Int] = Map("int" -> 2, "string" -> 1, "other" -> 0)
      val expectation = ClassifyingCountExpectation(classifier = classifier, expectedClassCounts = singleElementExpectations)
      // when, then
      expectation.getUnfulfilledClasses mustBe Seq("int", "string")
    }

    "correctly format statusDesc" in {
      val classifier: Map[String, SerializableFunction1[Any, Boolean]] = Map("int" -> (x => x.isInstanceOf[Int]), "string" -> (x => x.isInstanceOf[String]))
      val singleElementExpectations: Map[String, Int] = Map("int" -> 2, "string" -> 1, "other" -> 0)
      val expectation = ClassifyingCountExpectation(classifier = classifier, expectedClassCounts = singleElementExpectations)
      val expectedDesc = "unmeetable expectation classes: List()\n" +
        "unfulfilled classes: List(int, string)\n" +
        "currentCounts: HashMap(other -> 0, string -> 0, int -> 0)\n" +
        "expectedClassCounts: Map(int -> 2, string -> 1, other -> 0)"
      // when
      val statusDesc: String = expectation.statusDesc
      // then
      statusDesc mustBe expectedDesc
    }

  }

}
