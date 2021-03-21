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

package de.awagen.kolibri.datatypes.multivalues

import de.awagen.kolibri.datatypes.collections.IndexedGenerator
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.DistinctValues

class OrderedMultiValuesImplicitsSpec extends UnitTestSpec {

  "OrderedMultiValuesImplicits" should {

    "correctly provide batch iterator of named values" in {
      // given
      import OrderedMultiValuesImplicits._
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[String]("v2", Seq("a", "b", "c"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2))
      // when
      val params: Iterator[Seq[(String, Any)]] = multiValues.toNamedParamValuesIterator
      // then
      params.next() mustBe Seq(("v1", 1), ("v2", "a"))
      params.next() mustBe Seq(("v1", 2), ("v2", "a"))
      params.next() mustBe Seq(("v1", 1), ("v2", "b"))
      params.next() mustBe Seq(("v1", 2), ("v2", "b"))
      params.next() mustBe Seq(("v1", 1), ("v2", "c"))
      params.next() mustBe Seq(("v1", 2), ("v2", "c"))
      assertThrows[NoSuchElementException] {
        params.next()
      }
    }

    "correctly provide batch iterator of values" in {
      // given
      import OrderedMultiValuesImplicits._
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[String]("v2", Seq("a", "b", "c"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2))
      // when
      val params: Iterator[Seq[Any]] = multiValues.toParamValuesIterator
      // then
      params.next() mustBe Seq(1, "a")
      params.next() mustBe Seq(2, "a")
      params.next() mustBe Seq(1, "b")
      params.next() mustBe Seq(2, "b")
      params.next() mustBe Seq(1, "c")
      params.next() mustBe Seq(2, "c")
      assertThrows[NoSuchElementException] {
        params.next()
      }
    }

    "correctly provide batch iterator of maps" in {
      // given
      import OrderedMultiValuesImplicits._
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[Int]("v1", Seq(3, 1))
      val orderedValues_3 = DistinctValues[String]("v2", Seq("a", "b"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2, orderedValues_3))
      // when
      val params: Iterator[Map[String, Seq[Any]]] = multiValues.toParamNameValuesMapIterator
      // then
      params.next() mustBe Map("v1" -> Seq(1, 3), "v2" -> Seq("a"))
      params.next() mustBe Map("v1" -> Seq(2, 3), "v2" -> Seq("a"))
      params.next() mustBe Map("v1" -> Seq(1, 1), "v2" -> Seq("a"))
      params.next() mustBe Map("v1" -> Seq(2, 1), "v2" -> Seq("a"))
      params.next() mustBe Map("v1" -> Seq(1, 3), "v2" -> Seq("b"))
      params.next() mustBe Map("v1" -> Seq(2, 3), "v2" -> Seq("b"))
      params.next() mustBe Map("v1" -> Seq(1, 1), "v2" -> Seq("b"))
      params.next() mustBe Map("v1" -> Seq(2, 1), "v2" -> Seq("b"))
      assertThrows[NoSuchElementException] {
        params.next()
      }
    }

    "correctly transform to indexed generator of value sequence" in {
      // given
      import OrderedMultiValuesImplicits._
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[String]("v2", Seq("a", "b", "c"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2))
      // when
      val params: IndexedGenerator[Seq[Any]] = multiValues.toParamValuesIndexedGenerator
      // then
      params.get(0) mustBe Some(Seq(1, "a"))
      params.get(1) mustBe Some(Seq(2, "a"))
      params.get(2) mustBe Some(Seq(1, "b"))
      params.get(3) mustBe Some(Seq(2, "b"))
      params.get(4) mustBe Some(Seq(1, "c"))
      params.get(5) mustBe Some(Seq(2, "c"))
      params.get(6) mustBe None
    }

    "correctly transform to indexed generator of param name/values maps" in {
      // given
      import OrderedMultiValuesImplicits._
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[Int]("v1", Seq(3, 1))
      val orderedValues_3 = DistinctValues[String]("v2", Seq("a", "b"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2, orderedValues_3))
      // when
      val params = multiValues.toParamNameValuesMapIndexedGenerator
      // then
      params.get(0) mustBe Some(Map("v1" -> Seq(1, 3), "v2" -> Seq("a")))
      params.get(1) mustBe Some(Map("v1" -> Seq(2, 3), "v2" -> Seq("a")))
      params.get(2) mustBe Some(Map("v1" -> Seq(1, 1), "v2" -> Seq("a")))
      params.get(3) mustBe Some(Map("v1" -> Seq(2, 1), "v2" -> Seq("a")))
      params.get(4) mustBe Some(Map("v1" -> Seq(1, 3), "v2" -> Seq("b")))
      params.get(5) mustBe Some(Map("v1" -> Seq(2, 3), "v2" -> Seq("b")))
      params.get(6) mustBe Some(Map("v1" -> Seq(1, 1), "v2" -> Seq("b")))
      params.get(7) mustBe Some(Map("v1" -> Seq(2, 1), "v2" -> Seq("b")))
      params.get(8) mustBe None
    }

    "correctly transform to indexed generator of named values" in {
      // given
      import OrderedMultiValuesImplicits._
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[String]("v2", Seq("a", "b", "c"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2))
      // when
      val params: IndexedGenerator[Seq[(String, Any)]] = multiValues.toNamedParamValuesIndexedGenerator
      // then
      params.get(-1) mustBe None
      params.get(0) mustBe Some(Seq(("v1", 1), ("v2", "a")))
      params.get(1) mustBe Some(Seq(("v1", 2), ("v2", "a")))
      params.get(2) mustBe Some(Seq(("v1", 1), ("v2", "b")))
      params.get(3) mustBe Some(Seq(("v1", 2), ("v2", "b")))
      params.get(4) mustBe Some(Seq(("v1", 1), ("v2", "c")))
      params.get(5) mustBe Some(Seq(("v1", 2), ("v2", "c")))
      params.get(6) mustBe None
    }

  }

}
