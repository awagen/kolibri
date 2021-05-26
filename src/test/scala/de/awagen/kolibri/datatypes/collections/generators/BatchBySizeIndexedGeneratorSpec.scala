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

package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class BatchBySizeIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(2, x => Some(x))
  val generator2: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(2, x => Some(x + 1))
  val generator3: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(3, x => Some(x))

  val permutatingGenerator: IndexedGenerator[Seq[Int]] = PermutatingIndexedGenerator(Seq(generator1, generator2, generator3))
  val batchGenerator: IndexedGenerator[IndexedGenerator[Seq[Int]]] = BatchBySizeIndexedGenerator(permutatingGenerator, 5)

  "BatchBySizeIndexedGenerator" must {

    "correctly determine nr of elements" in {
      batchGenerator.size mustBe 3
    }

    "correctly determine part" in {
      // given, when
      val parts: Seq[Seq[Seq[Int]]] = batchGenerator.getPart(1,3).iterator.toSeq.map(x => x.iterator.toSeq)
      val expextedParts: Seq[Seq[Seq[Int]]] = Seq(permutatingGenerator.getPart(5, 10), permutatingGenerator.getPart(10, 15)).map(x => x.iterator.toSeq)
      // then
      parts mustBe expextedParts
    }

    "correctly provide single element" in {
      // given, when
      val none1 = batchGenerator.get(-1)
      val none2 = batchGenerator.get(10)
      val el1 = batchGenerator.get(0).get
      val el2 = batchGenerator.get(1).get
      val el3 = batchGenerator.get(2).get
      val none3 = batchGenerator.get(3)
      // then
      Seq(none1, none2, none3) mustBe Seq(None, None, None)
      el1.iterator.toSeq mustBe permutatingGenerator.getPart(0, 5).iterator.toSeq
      el2.iterator.toSeq mustBe permutatingGenerator.getPart(5, 10).iterator.toSeq
      el3.iterator.toSeq mustBe permutatingGenerator.getPart(10, 15).iterator.toSeq
    }

    "correctly map elements" in {
      // given, when
      val mapped: Seq[Seq[Seq[Int]]] = batchGenerator.mapGen(x => x.toSeq).iterator.toSeq
      val expected = batchGenerator.iterator.map(x => x.toSeq).toSeq
      // then
      mapped mustBe expected
    }

  }

}
