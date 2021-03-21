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

package de.awagen.kolibri.datatypes.collections

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class BaseIndexedGeneratorSpec extends UnitTestSpec {

  "BaseIndexedGenerator" should {

    "correctly map elements" in {
      // given
      val elements: Seq[Int] = Seq(2, 4, 6, 8)
      val generator: BaseIndexedGenerator[Int] = BaseIndexedGenerator(4, c => Some(elements(c)))
      // when
      val new_generator: IndexedGenerator[String] = generator.mapGen(x => s"$x")
      val new_iterator: Iterator[String] = new_generator.iterator
      // then
      new_generator.nrOfElements mustBe 4
      new_iterator.next() mustBe "2"
      new_iterator.next() mustBe "4"
      new_iterator.next() mustBe "6"
      new_iterator.next() mustBe "8"
    }

    "correctly generate part" in {
      // given
      val elements = Seq(1, 4, 5, 2, 1, 2, 9, 10, 11, 0)
      val generator = BaseIndexedGenerator(10, c => Some(elements(c)))
      // when
      val firstHalf = generator.getPart(0, 5)
      val secondHalf = generator.getPart(5, 10)
      // then
      firstHalf.nrOfElements mustBe 5
      secondHalf.nrOfElements mustBe 5
      Seq(firstHalf.get(0), firstHalf.get(1), firstHalf.get(2), firstHalf.get(3),
        firstHalf.get(4)) mustBe Seq(Some(1), Some(4), Some(5), Some(2), Some(1))
      firstHalf.iterator.toSeq mustBe Seq(1, 4, 5, 2, 1)
      Seq(secondHalf.get(0), secondHalf.get(1), secondHalf.get(2), secondHalf.get(3),
        secondHalf.get(4)) mustBe Seq(Some(2), Some(9), Some(10), Some(11), Some(0))
      secondHalf.iterator.toSeq mustBe Seq(2, 9, 10, 11, 0)
    }

    "correctly provide iterator for part" in {
      // given
      val elements = Seq(1, 4, 5, 2, 1, 2, 9, 10, 11, 0)
      val generator = BaseIndexedGenerator(10, c => Some(elements(c)))
      // when, then
      generator.getPart(2, 6).iterator.toSeq mustBe Seq(5, 2, 1, 2)
    }

    "correctly provide iterator for whole" in {
      // given
      val elements = Seq(1, 4, 5, 2, 1, 2, 9, 10, 11, 0)
      val generator = BaseIndexedGenerator(10, c => Some(elements(c)))
      // when, then
      generator.iterator.toSeq mustBe Seq(1, 4, 5, 2, 1, 2, 9, 10, 11, 0)
    }

    "should reduce endIndex to max index if exceeding" in {
      // given
      val elements = Seq(1, 4, 5, 2, 1, 2, 9, 10, 11, 0)
      val generator = BaseIndexedGenerator(10, c => Some(elements(c)))
      // when, then
      generator.getPart(2, 100).iterator.toSeq mustBe Seq(5, 2, 1, 2, 9, 10, 11, 0)
    }

    "should throw assertion exception if startIndex < 0" in {
      // given
      val elements = Seq(1, 4, 5, 2, 1, 2, 9, 10, 11, 0)
      val generator = BaseIndexedGenerator(10, c => Some(elements(c)))
      // when
      assertThrows[AssertionError](generator.getPart(-1, 3))
    }

  }

}
