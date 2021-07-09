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

class BatchByGeneratorIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(2, x => Some(x))
  val generator2: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(3, x => Some(x + 1))
  val generator3: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(1, x => Some(x))

  def provideGenerator(batchByIndex: Int): BatchByGeneratorIndexedGenerator[Int] = {
    BatchByGeneratorIndexedGenerator(Seq(generator1, generator2, generator3), batchByIndex)
  }

  "BatchByGeneratorIndexedGenerator" must {

    "correctly determine nr of elements as size of generator batched by" in {
      provideGenerator(0).size mustBe generator1.size
      provideGenerator(1).size mustBe generator2.size
      provideGenerator(2).size mustBe generator3.size
    }

    "correctly determine elements" in {
      // given, when
      val generators: Seq[IndexedGenerator[Seq[Int]]] = provideGenerator(0).iterator.toSeq
      // then
      for (index <- generators.indices) {
        generators(index).iterator.toSeq mustBe PermutatingIndexedGenerator(Seq(generator2, generator3)).mapGen(x => {
          generator1.get(index).get +: x
        }).iterator.toSeq
      }
    }

    "correctly get part" in {
      // given, when
      val partGen: Seq[Seq[Seq[Int]]] = provideGenerator(0).getPart(0, 1).iterator.toSeq.map(x => x.iterator.toSeq)
      val expectedGen: Seq[Seq[Seq[Int]]] = BatchByGeneratorIndexedGenerator(Seq(generator1.getPart(0, 1), generator2, generator3), 0).iterator.toSeq.map(x => x.iterator.toSeq)
      // then
      partGen mustBe expectedGen
    }

    "correctly calculate single elements" in {
      // given, when
      val gen1: Option[Seq[Seq[Int]]] = provideGenerator(0).get(0).map(x => x.iterator.toSeq)
      val gen2: Option[Seq[Seq[Int]]] = provideGenerator(0).get(1).map(x => x.iterator.toSeq)
      val expectedGen1 = BatchByGeneratorIndexedGenerator(Seq(generator1.getPart(0, 1), generator2, generator3), 0).get(0).map(x => x.iterator.toSeq)
      val expectedGen2 = BatchByGeneratorIndexedGenerator(Seq(generator1.getPart(1, 2), generator2, generator3), 0).get(0).map(x => x.iterator.toSeq)
      // then
      gen1 mustBe expectedGen1
      gen2 mustBe expectedGen2
    }

    "correctly apply mapping" in {
      // given, when
      val data: Seq[Seq[Seq[Int]]] = provideGenerator(0).mapGen(x => x.get(0).iterator.toSeq).iterator.toSeq
      val expectedData: Seq[Seq[Seq[Int]]] = BatchByGeneratorIndexedGenerator(Seq(generator1, generator2.getPart(0, 1), generator3.getPart(0, 1)), 0).iterator.toSeq.map(x => x.iterator.toSeq).iterator.toSeq
      // then
      data mustBe expectedData
    }

  }

}
