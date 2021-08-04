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

class OneAfterAnotherIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = OneAfterAnotherIndexedGenerator(
    Seq(ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(1, 2, 3)),
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(6, 7, 8)),
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(10, 11, 12)))
  )

  "OneAfterAnotherIndexedGenerator" must {

    "correctly provide elements" in {
      // given, when, then
      generator1.iterator.toSeq mustBe Seq(1, 2, 3, 6, 7, 8, 10, 11, 12)
    }

    "correctly provide parts" in {
      // given, when, then
      generator1.getPart(3, 9).iterator.toSeq mustBe Seq(6, 7, 8, 10, 11, 12)
      generator1.getPart(-1, 0).iterator.toSeq mustBe Seq.empty
      generator1.getPart(0, 0).iterator.toSeq mustBe Seq.empty
      generator1.getPart(0, 1).iterator.toSeq mustBe Seq(1)
    }

    "correctly get single elements" in {
      // given, when, then
      generator1.get(0) mustBe Some(1)
      generator1.get(1) mustBe Some(2)
      generator1.get(3) mustBe Some(6)
      generator1.get(9) mustBe None
    }

    "correctly provide mapped elements" in {
      // given, when, then
      generator1.mapGen(_ + 10).iterator.toSeq mustBe Seq(11, 12, 13, 16, 17, 18, 20, 21, 22)
    }

  }

}
