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
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.StructDefs._
import de.awagen.kolibri.datatypes.utils.MathUtils
import spray.json.{DeserializationException, JsArray, JsBoolean, JsNumber, JsObject, JsString}

object StructDefs {
  val regexStructDef1: BaseStructDef[_] = RegexStructDef("^itWas$".r)
  val regexStructDef2: BaseStructDef[_] = RegexStructDef("^\\w+\\s+$".r)
  val seqRegexStructDef1: BaseStructDef[_] = SeqRegexStructDef("^\\w+\\s+$".r)
  val choiceStructDef1: BaseStructDef[_] = StringChoiceStructDef(Seq("a", "b"))
  val seqChoiceStructDef1: BaseStructDef[_] = StringSeqChoiceStructDef(Seq("a", "b"))
  val minMaxStructDef: BaseStructDef[_] = DoubleMinMaxStructDef(1.0, 2.0)
  val seqMinMaxStructDef: BaseStructDef[_] = DoubleSeqMinMaxStructDef(1.0, 2.0)
  val keyStructDef1: BaseStructDef[_] = RegexStructDef("^k\\w+".r)
  val stringConstantStructDef1: BaseStructDef[_] = StringConstantStructDef("constValue")

  val validConditionalFieldValueStructDef1Obj_a = new JsObject(Map(
    "choice1" -> JsString("a"),
    "conditional1" -> JsNumber(1.2)
  ))
  val invalidConditionalFieldValueStructDef1Obj_a = new JsObject(Map(
    "choice1" -> JsString("a"),
    "conditional1" -> JsString("a")
  ))
  val validConditionalFieldValueStructDef1Obj_b = new JsObject(Map(
    "choice1" -> JsString("b"),
    "conditional1" -> JsString("a")
  ))
  val invalidConditionalFieldValueStructDef1Obj_b = new JsObject(Map(
    "choice1" -> JsString("b"),
    "conditional1" -> JsNumber(1.2)
  ))

  val eitherOfStructDef1: BaseStructDef[_] = EitherOfStructDef(Seq(
    keyStructDef1,
    stringConstantStructDef1,
    seqChoiceStructDef1
  ))

  val nestedStructDef1: StructDef[_] = NestedFieldSeqStructDef(Seq(
    Fields.regexStructDef1Field,
    Fields.regexStructDef2Field,
    Fields.choiceStructDef1Field,
    Fields.seqChoiceStructDef1Field
  ), Seq.empty)

  val emptyNestedStructDef: StructDef[_] = NestedFieldSeqStructDef(Seq.empty, Seq.empty)

  val validNestedStructDef1Obj = new JsObject(Map(
    "regex1" -> JsString("itWas"),
    "regex2" -> JsString("aaa "),
    "choice1" -> JsString("a"),
    "seqChoice1" -> JsArray(JsString("a"), JsString("b")),
  ))

  val validNestedStructDef1ResultMap = Map(
    "regex1" -> "itWas",
    "regex2" -> "aaa ",
    "choice1" -> "a",
    "seqChoice1" -> Seq("a", "b")
  )

  val invalidRegex2NestedStructDef1Obj = new JsObject(Map(
    "regex1" -> JsString("itWas"),
    "regex2" -> JsString("aaa"),
    "choice1" -> JsString("a"),
    "seqChoice1" -> JsArray(JsString("a"), JsString("b")),
  ))

}

object Fields {
  val regexStructDef1Field: FieldDef = FieldDef(StringConstantStructDef("regex1"), regexStructDef1, required = true)
  val regexStructDef2Field: FieldDef = FieldDef(StringConstantStructDef("regex2"), regexStructDef2, required = true)
  val seqRegexStructDef1Field: FieldDef = FieldDef(StringConstantStructDef("seqRegex1"), seqRegexStructDef1, required = true)
  val choiceStructDef1Field: FieldDef = FieldDef(StringConstantStructDef("choice1"), choiceStructDef1, required = true)
  val seqChoiceStructDef1Field: FieldDef = FieldDef(StringConstantStructDef("seqChoice1"), seqChoiceStructDef1, required = true)
  val minMaxStructDefField: FieldDef = FieldDef(StringConstantStructDef("minMax1"), minMaxStructDef, required = true)
  val seqMinMaxStructDefField: FieldDef = FieldDef(StringConstantStructDef("seqMinMax1"), seqMinMaxStructDef, required = true)
}

class JsonStructDefsSpec extends UnitTestSpec {

  "JsonStructDefs" should {
    "StructDef should cast" in {
      IntStructDef.cast(JsNumber(1)) mustBe 1
      intercept[DeserializationException] {
        IntStructDef.cast(JsString("aaa"))
      }
      StringStructDef.cast(JsString("aaa")) mustBe "aaa"
      intercept[DeserializationException] {
        StringStructDef.cast(JsNumber(1))
      }
      DoubleStructDef.cast(JsNumber(1.0)) mustBe 1.0
      FloatStructDef.cast(JsNumber(1.0)) mustBe 1.0
      BooleanStructDef.cast(JsBoolean(false)) mustBe false
      BooleanStructDef.cast(JsBoolean(true)) mustBe true
      IntSeqStructDef.cast(JsArray(JsNumber(1), JsNumber(2))) mustBe Seq(1, 2)
      StringSeqStructDef.cast(JsArray(JsString("1"), JsString("2"))) mustBe Seq("1", "2")
      DoubleSeqStructDef.cast(JsArray(JsNumber(1.2), JsNumber(2.4))) mustBe Seq(1.2, 2.4)
      MathUtils.equalWithPrecision(FloatSeqStructDef.cast(JsArray(JsNumber(1.2), JsNumber(2.4))), Seq(1.2f, 2.4f), 0.001f) mustBe true
      BoolSeqStructDef.cast(JsArray(JsBoolean(false), JsBoolean(true))) mustBe Seq(false, true)
    }

    "Regex format should cherish regex" in {
      regexStructDef1.cast(JsString("itWas")) mustBe "itWas"
      intercept[IllegalArgumentException] {
        regexStructDef1.cast(JsString("itWas11"))
      }
      regexStructDef2.cast(JsString("aaa ")) mustBe "aaa "
      intercept[IllegalArgumentException] {
        regexStructDef2.cast(JsString("aaa"))
      }
    }

    "Regex Seq format should cherish regex over whole sequence" in {
      seqRegexStructDef1.cast(JsArray(JsString("aaa "), JsString("b  "))) mustBe Seq("aaa ", "b  ")
      intercept[IllegalArgumentException] {
        seqRegexStructDef1.cast(JsArray(JsString("a"), JsString("b")))
      }
    }

    "Choice format should cherish choices" in {
      choiceStructDef1.cast(JsString("a")) mustBe "a"
      choiceStructDef1.cast(JsString("b")) mustBe "b"
      intercept[IllegalArgumentException] {
        choiceStructDef1.cast(JsString("c"))
      }
    }

    "Choice Seq format should cherish choices over sequence" in {
      seqChoiceStructDef1.cast(JsArray(JsString("a"), JsString("b"))) mustBe Seq("a", "b")
      intercept[IllegalArgumentException] {
        seqChoiceStructDef1.cast(JsArray(JsString("a"), JsString("c")))
      }
    }

    "Min Max format should cherish boundaries" in {
      minMaxStructDef.cast(JsNumber(1.0)) mustBe 1.0
      minMaxStructDef.cast(JsNumber(1.5)) mustBe 1.5
      minMaxStructDef.cast(JsNumber(2.0)) mustBe 2.0
      intercept[IllegalArgumentException] {
        minMaxStructDef.cast(JsNumber(2.1))
      }
    }

    "Seq Min Max format should cherish boundaries" in {
      seqMinMaxStructDef.cast(JsArray(JsNumber(1.0), JsNumber(1.5))) mustBe Seq(1.0, 1.5)
      intercept[IllegalArgumentException] {
        seqMinMaxStructDef.cast(JsArray(JsNumber(2.0), JsNumber(2.1)))
      }
    }

    "NestedStructDef should correctly parse single attributes" in {
      nestedStructDef1.cast(validNestedStructDef1Obj) mustBe validNestedStructDef1ResultMap
    }

    "NestedStructDef should detect non-matching fields" in {
      intercept[IllegalArgumentException] {
        nestedStructDef1.cast(invalidRegex2NestedStructDef1Obj)
      }
    }

    "NestedStructDef without fields shall always accept" in {
      emptyNestedStructDef.cast(invalidRegex2NestedStructDef1Obj)
    }

    "EitherOfStructDef should declare valid if either format applies" in {
      eitherOfStructDef1.cast(JsString("constValue")) mustBe "constValue"
      eitherOfStructDef1.cast(JsString("k1")) mustBe "k1"
      eitherOfStructDef1.cast(JsArray(Seq(JsString("a"), JsString("b")).toVector)) mustBe Seq("a", "b")
    }

    "EitherOfStructDef should declare invalid if neither format applies" in {
      intercept[IllegalArgumentException] {
        eitherOfStructDef1.cast(JsString("constValueeee"))
      }
      intercept[IllegalArgumentException] {
        eitherOfStructDef1.cast(JsString("k"))
      }
      intercept[IllegalArgumentException] {
        eitherOfStructDef1.cast(JsArray(Seq(JsString("a"), JsString("c")).toVector))
      }
    }

  }

}
