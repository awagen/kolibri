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
import de.awagen.kolibri.datatypes.types.Formats._
import de.awagen.kolibri.datatypes.types.JsonFormats._
import de.awagen.kolibri.datatypes.utils.MathUtils
import spray.json.{DeserializationException, JsArray, JsBoolean, JsNumber, JsObject, JsString}

object Formats {
  val regexFormat1: BaseFormat[_] = RegexFormat("^itWas$".r)
  val regexFormat2: BaseFormat[_] = RegexFormat("^\\w+\\s+$".r)
  val seqRegexFormat1: BaseFormat[_] = SeqRegexFormat("^\\w+\\s+$".r)
  val choiceFormat1: BaseFormat[_] = StringChoiceFormat(Seq("a", "b"))
  val seqChoiceFormat1: BaseFormat[_] = StringSeqChoiceFormat(Seq("a", "b"))
  val minMaxFormat: BaseFormat[_] = DoubleMinMaxFormat(1.0, 2.0)
  val seqMinMaxFormat: BaseFormat[_] = DoubleSeqMinMaxFormat(1.0, 2.0)
  val keyFormat1: BaseFormat[_] = RegexFormat("^k\\w+".r)
  val stringConstantFormat1: BaseFormat[_] = StringConstantFormat("constValue")

  val conditionalChoiceFormat: BaseFormat[_] = ConditionalFieldValueChoiceFormat(
    "field1",
    Map("value1" -> minMaxFormat, "value2" -> choiceFormat1)
  )

  val eitherOfFormat1: BaseFormat[_] = EitherOfFormat(Seq(
    keyFormat1,
    stringConstantFormat1,
    seqChoiceFormat1
  ))

  val nestedFormat1: BaseFormat[_] = NestedFieldSeqFormat(Seq(
    Fields.regexFormat1Field,
    Fields.regexFormat2Field,
    Fields.choiceFormat1Field,
    Fields.seqChoiceFormat1Field
  ))

  val emptyNestedFormat: BaseFormat[_] = NestedFieldSeqFormat(Seq.empty)

  val validNestedFormat1Obj = new JsObject(Map(
    "regex1" -> JsString("itWas"),
    "regex2" -> JsString("aaa "),
    "choice1" -> JsString("a"),
    "seqChoice1" -> JsArray(JsString("a"), JsString("b")),
  ))

  val validNestedFormat1ResultMap = Map(
    "regex1" -> "itWas",
    "regex2" -> "aaa ",
    "choice1" -> "a",
    "seqChoice1" -> Seq("a", "b")
  )

  val invalidRegex2NestedFormat1Obj = new JsObject(Map(
    "regex1" -> JsString("itWas"),
    "regex2" -> JsString("aaa"),
    "choice1" -> JsString("a"),
    "seqChoice1" -> JsArray(JsString("a"), JsString("b")),
  ))

}

object Fields {
  val regexFormat1Field: FieldType = FieldType(StringConstantFormat("regex1"), regexFormat1, required = true)
  val regexFormat2Field: FieldType = FieldType(StringConstantFormat("regex2"), regexFormat2, required = true)
  val seqRegexFormat1Field: FieldType = FieldType(StringConstantFormat("seqRegex1"), seqRegexFormat1, required = true)
  val choiceFormat1Field: FieldType = FieldType(StringConstantFormat("choice1"), choiceFormat1, required = true)
  val seqChoiceFormat1Field: FieldType = FieldType(StringConstantFormat("seqChoice1"), seqChoiceFormat1, required = true)
  val minMaxFormatField: FieldType = FieldType(StringConstantFormat("minMax1"), minMaxFormat, required = true)
  val seqMinMaxFormatField: FieldType = FieldType(StringConstantFormat("seqMinMax1"), seqMinMaxFormat, required = true)
}

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
      MathUtils.equalWithPrecision(FloatSeqFormat.cast(JsArray(JsNumber(1.2), JsNumber(2.4))), Seq(1.2f, 2.4f), 0.001f) mustBe true
      BoolSeqFormat.cast(JsArray(JsBoolean(false), JsBoolean(true))) mustBe Seq(false, true)
    }

    "Regex format should cherish regex" in {
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
      seqRegexFormat1.cast(JsArray(JsString("aaa "), JsString("b  "))) mustBe Seq("aaa ", "b  ")
      intercept[IllegalArgumentException] {
        seqRegexFormat1.cast(JsArray(JsString("a"), JsString("b")))
      }
    }

    "Choice format should cherish choices" in {
      choiceFormat1.cast(JsString("a")) mustBe "a"
      choiceFormat1.cast(JsString("b")) mustBe "b"
      intercept[IllegalArgumentException] {
        choiceFormat1.cast(JsString("c"))
      }
    }

    "Choice Seq format should cherish choices over sequence" in {
      seqChoiceFormat1.cast(JsArray(JsString("a"), JsString("b"))) mustBe Seq("a", "b")
      intercept[IllegalArgumentException] {
        seqChoiceFormat1.cast(JsArray(JsString("a"), JsString("c")))
      }
    }

    "Min Max format should cherish boundaries" in {
      minMaxFormat.cast(JsNumber(1.0)) mustBe 1.0
      minMaxFormat.cast(JsNumber(1.5)) mustBe 1.5
      minMaxFormat.cast(JsNumber(2.0)) mustBe 2.0
      intercept[IllegalArgumentException] {
        minMaxFormat.cast(JsNumber(2.1))
      }
    }

    "Seq Min Max format should cherish boundaries" in {
      seqMinMaxFormat.cast(JsArray(JsNumber(1.0), JsNumber(1.5))) mustBe Seq(1.0, 1.5)
      intercept[IllegalArgumentException] {
        seqMinMaxFormat.cast(JsArray(JsNumber(2.0), JsNumber(2.1)))
      }
    }

    "NestedFormat should correctly parse single attributes" in {
      nestedFormat1.cast(validNestedFormat1Obj) mustBe validNestedFormat1ResultMap
    }

    "NestedFormat should detect non-matching fields" in {
      intercept[IllegalArgumentException] {
        nestedFormat1.cast(invalidRegex2NestedFormat1Obj)
      }
    }

    "NestedFormat without fields shall always accept" in {
      emptyNestedFormat.cast(invalidRegex2NestedFormat1Obj)
    }

    "EitherOfFormat should declare valid if either format applies" in {
      eitherOfFormat1.cast(JsString("constValue")) mustBe "constValue"
      eitherOfFormat1.cast(JsString("k1")) mustBe "k1"
      eitherOfFormat1.cast(JsArray(Seq(JsString("a"), JsString("b")).toVector)) mustBe Seq("a", "b")
    }

    "EitherOfFormat should declare invalid if neither format applies" in {
      intercept[IllegalArgumentException] {
        eitherOfFormat1.cast(JsString("constValueeee"))
      }
      intercept[IllegalArgumentException] {
        eitherOfFormat1.cast(JsString("k"))
      }
      intercept[IllegalArgumentException] {
        eitherOfFormat1.cast(JsArray(Seq(JsString("a"), JsString("c")).toVector))
      }
    }

    "ConditionalFieldValueChoiceFormat should declare valid if any of the option declares valid" in {
      conditionalChoiceFormat.cast(JsNumber(1.1)) mustBe 1.1D
      conditionalChoiceFormat.cast(JsString("a")) mustBe "a"
    }

    "ConditionalFieldValueChoiceFormat should throw exception invalid if none of the option declares valid" in {
      intercept[IllegalArgumentException] {
        conditionalChoiceFormat.cast(JsString("c"))
      }
    }

  }

}
