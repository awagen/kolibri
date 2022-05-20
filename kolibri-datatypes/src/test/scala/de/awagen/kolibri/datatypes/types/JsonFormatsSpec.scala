/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.types

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.JsonFormats._
import spray.json.DefaultJsonProtocol.{DoubleJsonFormat, StringJsonFormat, immSeqFormat}
import spray.json.{DeserializationException, JsArray, JsBoolean, JsNumber, JsString}

class JsonFormatsSpec extends UnitTestSpec {

  "JsonFormats" should {
    "Format should cast" in {
      IntFormat.cast(JsNumber(1)) mustBe 1
      intercept[DeserializationException] {
        IntFormat.cast(JsString("aaa"))
      }
      StringFormat.cast(JsString("aaa")) mustBe "aaa"
      intercept[DeserializationException] {
        StringFormat.cast(JsNumber(1))
      }
      DoubleFormat.cast(JsNumber(1.0)) mustBe 1.0
      FloatFormat.cast(JsNumber(1.0)) mustBe 1.0
      BooleanFormat.cast(JsBoolean(false)) mustBe false
      BooleanFormat.cast(JsBoolean(true)) mustBe true
      IntSeqFormat.cast(JsArray(JsNumber(1), JsNumber(2))) mustBe Seq(1, 2)
      StringSeqFormat.cast(JsArray(JsString("1"), JsString("2"))) mustBe Seq("1", "2")
      DoubleSeqFormat.cast(JsArray(JsNumber(1.2), JsNumber(2.4))) mustBe Seq(1.2, 2.4)
      FloatSeqFormat.cast(JsArray(JsNumber(1.2), JsNumber(2.4))) mustBe Seq(1.2, 2.4)
      BoolSeqFormat.cast(JsArray(JsBoolean(false), JsBoolean(true))) mustBe Seq(false, true)
    }

    "Regex format should cherish regex" in {
      val regexFormat1 = RegexFormat("^itWas$".r)
      val regexFormat2 = RegexFormat("^\\w+\\s+$".r)
      regexFormat1.cast(JsString("itWas")) mustBe "itWas"
      intercept[IllegalArgumentException] {
        regexFormat1.cast(JsString("itWas11"))
      }
      regexFormat2.cast(JsString("aaa ")) mustBe "aaa "
      intercept[IllegalArgumentException] {
        regexFormat2.cast(JsString("aaa"))
      }
    }

    "Regex Seq format should cherish regex over whole sequence" in {
      val regexFormat1 = SeqRegexFormat("^\\w+\\s+$".r)
      regexFormat1.cast(JsArray(JsString("aaa "), JsString("b  "))) mustBe Seq("aaa ", "b  ")
      intercept[IllegalArgumentException] {
        regexFormat1.cast(JsArray(JsString("a"), JsString("b")))
      }
    }

    "Choice format should cherish choices" in {
      val choiceFormat1 = ChoiceFormat[String](Seq("a", "b"))
      choiceFormat1.cast(JsString("a")) mustBe "a"
      choiceFormat1.cast(JsString("b")) mustBe "b"
      intercept[IllegalArgumentException] {
        choiceFormat1.cast(JsString("c"))
      }
    }

    "Choice Seq format should cherish choices over sequence" in {
      val choiceFormat1 = SeqChoiceFormat[String](Seq("a", "b"))
      choiceFormat1.cast(JsArray(JsString("a"), JsString("b"))) mustBe Seq("a", "b")
      intercept[IllegalArgumentException] {
        choiceFormat1.cast(JsArray(JsString("a"), JsString("c")))
      }
    }

    "Min Max format should cherish boundaries" in {
      val minMaxFormat = MinMaxFormat[Double](1.0, 2.0)
      minMaxFormat.cast(JsNumber(1.0)) mustBe 1.0
      minMaxFormat.cast(JsNumber(1.5)) mustBe 1.5
      minMaxFormat.cast(JsNumber(2.0)) mustBe 2.0
      intercept[IllegalArgumentException] {
        minMaxFormat.cast(JsNumber(2.1))
      }
    }

    "Seq Min Max format should cherish boundaries" in {
      val minMaxFormat = SeqMinMaxFormat[Double](1.0, 2.0)
      minMaxFormat.cast(JsArray(JsNumber(1.0), JsNumber(1.5))) mustBe Seq(1.0, 1.5)
      intercept[IllegalArgumentException] {
        minMaxFormat.cast(JsArray(JsNumber(2.0), JsNumber(2.1)))
      }
    }

  }

}
