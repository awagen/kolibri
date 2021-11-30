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

class PermutatingIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(5, x => Some(x))
  val generator2: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(5, x => Some(x + 1))
  val generator3: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(10, x => Some(x))

  val permutatingGen: IndexedGenerator[Seq[Int]] = PermutatingIndexedGenerator(Seq(generator1, generator2, generator3))
  val allValues: Seq[Seq[Int]] = permutatingGen.iterator.toSeq

  "PermutatingIndexedGenerator" must {

    "correctly calculate nr of elements" in {
      permutatingGen.nrOfElements mustBe 250
    }

    "correctly calculate part" in {
      permutatingGen.getPart(10, 250).iterator.toSeq mustBe allValues.slice(10, 250)
      permutatingGen.getPart(10, 240).iterator.toSeq mustBe allValues.slice(10, 240)
    }

    "correctly get single element" in {
      Range(0, 250).map(x => permutatingGen.get(x).get) mustBe allValues
    }

    "correctly apply mapping" in {
      permutatingGen.mapGen(x => x.mkString("-")).iterator.toSeq mustBe allValues.map(x => x.mkString("-"))
    }

  }

}
