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

class CombinedIteratorSpec extends UnitTestSpec {

  "CombinedIterator" should {

    "correctly generate elements" in {
      // given
      val iterable1: Iterable[Int] = Seq(1, 2, 3, 4)
      val iterable2: Iterable[Int] = Seq(5, 6)
      val combineFunc: (Int, Int) => Int = (x, y) => x + y
      val combinedIterator: CombinedIterator[Int, Int, Int] = CombinedIterator(iterable1, iterable2, combineFunc)
      // when
      var elements = Seq.empty[Seq[Int]]
      while (combinedIterator.hasNext) {
        elements = elements :+ combinedIterator.next().toSeq
      }
      // then
      elements mustBe Seq(Seq(6, 7), Seq(7, 8), Seq(8, 9), Seq(9, 10))
    }

  }

}
