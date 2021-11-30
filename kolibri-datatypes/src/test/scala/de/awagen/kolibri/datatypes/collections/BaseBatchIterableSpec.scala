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

class BaseBatchIterableSpec extends UnitTestSpec {

  "BaseBatchIterator" should {

    "generate iterables of size batch size" in {
      // given
      val batchIterable: BaseBatchIterable[Int] = BaseBatchIterable(Seq(1, 2, 3, 4, 5, 6, 7), 2)
      val batchIterator = batchIterable.iterator
      // when
      var batches = Seq.empty[Seq[Int]]
      while (batchIterator.hasNext) {
        batches = batches :+ batchIterator.next.toSeq
      }
      // then
      batches.size mustBe 4
      batches mustBe Seq(Seq(1, 2), Seq(3, 4), Seq(5, 6), Seq(7))
    }

  }

}
