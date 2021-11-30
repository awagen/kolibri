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

package de.awagen.kolibri.datatypes.utils

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class MathsUtilsSpec extends UnitTestSpec {

  "MathUtils" must {
    "correctly compare Floats with given precision" in {
      MathUtils.equalWithPrecision(0.01f, 0.010001f, 0.00001f) mustBe true

      val seq1_1: Seq[Float] = List(0.01f, 0.02f, 0.03f)
      val seq2_1: Seq[Float] = List(0.01001f, 0.02001f, 0.0300001f)
      val seq1_2: Seq[Float] = List(0.01f, 0.02f, 0.03f)
      val seq2_2: Seq[Float] = List(0.01001f, 0.02001f, 0.0300001f)
      MathUtils.equalWithPrecision(0.01f, 0.01001f, 0.000015f) mustBe true
      MathUtils.equalWithPrecision(0.01f, 0.01001f, 0.000001f) mustBe false
      MathUtils.equalWithPrecision(seq1_1, seq2_1, 0.000015f) mustBe true
      MathUtils.equalWithPrecision(seq1_1, seq2_1, 0.000002f) mustBe false

      MathUtils.seqEqualWithPrecision(List(seq1_1, seq1_2), List(seq2_1, seq2_2), 0.000015f) mustBe true
      MathUtils.seqEqualWithPrecision(List(seq1_1, seq1_2), List(seq2_1, seq2_2), 0.000002f) mustBe false
    }
  }


}
