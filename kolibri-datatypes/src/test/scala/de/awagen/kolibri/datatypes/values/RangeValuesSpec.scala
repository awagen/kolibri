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

package de.awagen.kolibri.datatypes.values

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class RangeValuesSpec extends UnitTestSpec {

  "RangeValues" must {
    "correctly calculate value count" in {
      //given
      val vals = RangeValues[Float]("test", 0.0f, 1.0f, 0.1f)
      //when, then
      vals.totalValueCount mustBe 11
    }

    "correctly calculate values" in {
      //given
      val vals = RangeValues[Float]("test", 0.0f, 1.0f, 0.1f)
      //when
      val seq: Seq[Float] = vals.getAll
      val expected = List(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)
      // then
      seq.size mustBe expected.size
      seq.indices.foreach(x =>
        RangeValues.equalWithPrecision(seq(x), expected(x), 0.00001f) mustBe true)
    }

    "correctly determine n elements starting from position" in {
      //given
      val vals = RangeValues[Float]("test", 0.0f, 1.0f, 0.1f)
      //when
      val n1 = vals.getNFromPositionZeroBased(4, 100)
      val n2 = vals.getNFromPositionZeroBased(4, 7)
      val expected1 = List(0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)
      val n3 = vals.getNFromPositionZeroBased(4, 2)
      val expected2 = List(0.4f, 0.5f)
      // then
      n1.size mustBe 7
      n2.size mustBe 7
      n3.size mustBe 2
      n1.indices.foreach(x =>
        RangeValues.equalWithPrecision(n1(x), expected1(x), 0.0001f) mustBe true)
      n2.indices.foreach(x =>
        RangeValues.equalWithPrecision(n2(x), expected1(x), 0.0001f) mustBe true)
      n3.indices.foreach(x =>
        RangeValues.equalWithPrecision(n3(x), expected2(x), 0.0001f) mustBe true)
    }

    "correctly determine nth element" in {
      //given
      val vals = RangeValues[Float]("test", 0.0f, 1.0f, 0.1f)
      //when
      val n1 = vals.getNthZeroBased(0)
      val n2 = vals.getNthZeroBased(1)
      val n5 = vals.getNthZeroBased(4)
      val notThere = vals.getNthZeroBased(100)
      RangeValues.equalWithPrecision(n1.get, 0.0f, 0.00001f) mustBe true
      RangeValues.equalWithPrecision(n2.get, 0.1f, 0.00001f) mustBe true
      RangeValues.equalWithPrecision(n5.get, 0.4f, 0.00001f) mustBe true
      notThere.isEmpty mustBe true
    }

    "throw AssertionError when index too low" in {
      //given
      val vals = RangeValues[Float]("test", 0.0f, 1.0f, 0.1f)
      //when,then
      assertThrows[AssertionError] {
        vals.getNthZeroBased(-1)
      }
    }
  }
}
