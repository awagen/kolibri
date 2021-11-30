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

import de.awagen.kolibri.datatypes.fixtures.JsonFixture
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.utils.StringUtils._

class StringUtilsSpec extends UnitTestSpec {

  "StringUtils" must {

    "extract value for fields from string" in {
      val tuple: Option[(String, String)] = StringUtils.extractField(""""id":"a450321"""", "id", PATTERN_ALPHANUMERIC)
      tuple.get._1 mustBe "id"
      tuple.get._2 mustBe "a450321"
    }

    "not extract value for fields not existing or value not matching pattern" in {
      val tuple: Option[(String, String)] = StringUtils.extractField(""""id":"a450321"""", "test", PATTERN_ALPHANUMERIC)
      val tuple1: Option[(String, String)] = StringUtils.extractField(""""id":"a450321"""", "id", PATTERN_ALPHA)
      tuple.isEmpty mustBe true
      tuple1.isEmpty mustBe true
    }

    "extract all values for numeric field" in {
      //given,when
      val varNumericSeq: Seq[String] = StringUtils.extractValuesForFieldFromStringOrdered(JsonFixture.json, "varNumeric", PATTERN_NUMERIC)
      //then
      varNumericSeq.size mustBe 3
      varNumericSeq mustBe Seq("123456", "234567", "345678")
    }

    "extract all values for alphanumeric field" in {
      //given,when
      val varAlphaNumericSeq: Seq[String] = StringUtils.extractValuesForFieldFromStringOrdered(JsonFixture.json, "varAlphaNumeric", PATTERN_ALPHANUMERIC)
      //then
      varAlphaNumericSeq.size mustBe 3
      varAlphaNumericSeq mustBe Seq("123456ABC", "234567ABC", "345678ABC")
    }

    "extract all values for date field" in {
      //given,when
      val varDateSeq: Seq[String] = StringUtils.extractValuesForFieldFromStringOrdered(JsonFixture.json, "varDate", PATTERN_DATE)
      //then
      varDateSeq.size mustBe 3
      varDateSeq mustBe Seq("2018-06-21T11:52:14.773Z", "2019-01-21T09:50:14.773Z", "2011-01-20T08:32:01.000Z")
    }

    "extract all values for alpha field" in {
      //given,when
      val varAlphaSeq: Seq[String] = StringUtils.extractValuesForFieldFromStringOrdered(JsonFixture.json, "varAlpha", PATTERN_ALPHA)
      //then
      varAlphaSeq.size mustBe 3
      varAlphaSeq mustBe Seq("adjdsahsa", "adjdsahsAAa", "adjdsahsaDDD")
    }

    "extract all values for float field" in {
      //given,when
      val varFloatSeq: Seq[Any] = StringUtils.extractValuesForFieldFromStringOrdered(JsonFixture.json, "varFloatingNumeric", PATTERN_FLOAT)
      //then
      varFloatSeq.size mustBe 3
      varFloatSeq mustBe Seq("5.390900", "2.3609", "1.110")
    }
  }

}
