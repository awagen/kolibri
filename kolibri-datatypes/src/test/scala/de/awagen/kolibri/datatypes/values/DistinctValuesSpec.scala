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

class DistinctValuesSpec extends UnitTestSpec {

  "String values" must {

    "correcty provide nth element" in {
      //given
      val stringValues: DistinctValues[String] = DistinctValues("test", List("test1", "test2", "test3", "test4"))
      //when
      val first = stringValues.getNthZeroBased(0)
      val second = stringValues.getNthZeroBased(1)
      val third = stringValues.getNthZeroBased(2)
      val fourth = stringValues.getNthZeroBased(3)
      val nonExist = stringValues.getNthZeroBased(4)
      //then
      first.get mustBe "test1"
      second.get mustBe "test2"
      third.get mustBe "test3"
      fourth.get mustBe "test4"
      nonExist mustBe None
    }

    "provide n elements starting from position" in {
      //given
      val stringValues: DistinctValues[String] = DistinctValues("test", List("test1", "test2", "test3", "test4"))
      //when
      val sub = stringValues.getNFromPositionZeroBased(1, 2)
      //then
      sub.size mustBe 2
      sub mustBe List("test2", "test3")
    }

    "provide all values" in {
      //given
      val stringValues: DistinctValues[String] = DistinctValues("test", List("test1", "test2", "test3", "test4"))
      //when
      val all = stringValues.getAll
      //then
      all mustBe List("test1", "test2", "test3", "test4")
    }

    "determine total number of values" in {
      //given
      val stringValues: DistinctValues[String] = DistinctValues("test", List("test1", "test2", "test3", "test4"))
      //when
      val nrOfValues = stringValues.totalValueCount
      //then
      nrOfValues mustBe 4
    }

    "remove duplicates from input sequence but remain order" in {
      //given, when
      val stringValues: DistinctValues[String] = DistinctValues("test",
        List("test1", "test4", "test2", "test1", "test3", "test4"))
      //then
      stringValues.seq mustBe List("test1", "test4", "test2", "test3")
    }

  }

}
