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
import de.awagen.kolibri.datatypes.utils.ValueCombine.{AVG_NUMERIC_APPEND_STR_AND_SEQ, CONCAT_SEQ}

class MapUtilsSpec extends UnitTestSpec {

  val testSeq1: Seq[Any] = Seq("aa", 5, "what", 1.0)
  val testSeq2: Seq[Any] = Seq("cc", 3, 6.0)
  val testSet1: Set[Any] = Set("1", 1, 2.0)
  val testSet2: Set[Any] = Set("1", 1, 3.0, "a")
  val testStr1: String = "abcd"
  val testStr2: String = "defg"

  "ValueCombine" must {
    "correctly combine two sequence values by extending them" in {
      // given, when
      val seq = CONCAT_SEQ.apply(testSeq1, testSeq2)
      //then
      seq mustBe Seq("aa", 5, "what", 1.0, "cc", 3, 6.0)
    }

    "correctly combine types" in {
      // given, when
      val set = AVG_NUMERIC_APPEND_STR_AND_SEQ.apply((testSet1, 1), (testSet2, 3))
      val seq = AVG_NUMERIC_APPEND_STR_AND_SEQ.apply((testSeq1, 1), (testSeq2, 3))
      val str = AVG_NUMERIC_APPEND_STR_AND_SEQ.apply((testStr1, 1), (testStr2, 3))
      val double = AVG_NUMERIC_APPEND_STR_AND_SEQ.apply((2.0D, 1), (6.0D, 3))
      val float = AVG_NUMERIC_APPEND_STR_AND_SEQ.apply((4.0F, 1), (4.0F, 2))
      //then
      set mustBe Set("1", 1, 2.0, 3.0, "a")
      seq mustBe Seq("aa", 5, "what", 1.0, "cc", 3, 6.0)
      str mustBe "abcd,defg"
      double mustBe 5.0D
      float mustBe 4.0F
    }

    "throw exceptions if types dont match" in {
      //given, when
      val set = AVG_NUMERIC_APPEND_STR_AND_SEQ.apply((testSet1, 1), (testStr2, 3))
      //then
      set mustBe CombineError
    }


  }

  "MapUtils" must {
    "correctly combine two Map[String, Seq[_]]" in {
      //given
      val map1 = Map("1" -> Seq(1,"2",3), "2" -> Seq(0,1))
      val map2 = Map("2" -> Seq(3,"4"), "3" -> Seq.empty)
      //when
      val resultMap: Map[String, Seq[_]] = MapUtils.combineMaps(map1, map2, ValueCombine.CONCAT_SEQ)
      //then
      resultMap mustBe Map("1" -> Seq(1,"2",3), "2" -> Seq(0,1,3,"4"), "3" -> Seq.empty)
    }

    "correctly combine two Map[String, Any]" in {
      //given
      val map1: Map[String, Any] = Map("1" -> Seq(1,"2"), "2" -> Seq(0), "4" -> 3.0D, "5" -> 2.0F, "6" -> "120")
      val map2: Map[String, Any] = Map("2" -> Seq(3), "3" -> Seq.empty,  "4" -> 1.0D, "5" -> 1.0F, "6" -> 5)
      //when
      val resultMap: Map[String, Any] = MapUtils.combineMapsWithAggregateCounts((map1, 3), (map2, 1), ValueCombine.AVG_NUMERIC_APPEND_STR_AND_SEQ)
      //then
      resultMap mustBe Map("1" -> Seq(1,"2"), "2" -> Seq(0,3), "3" -> Seq.empty, "4" -> 2.5D, "5" -> 7/4F, "6" -> CombineError)
    }

  }

}
