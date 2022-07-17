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


package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.StructDefTypes._
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.JsonKeys.{CONDITIONAL_MAPPING_KEY, CONDITION_FIELD_ID_KEY}
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol._
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import spray.json.{JsValue, _}

import scala.util.matching.Regex

class JsonStructDefsJsonProtocolSpec extends UnitTestSpec {

  object StructDefSamples {

    case class JsonAndObj(json: JsValue, obj: Any)

    val minMaxIntFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$MIN_MAX_INT_TYPE",
         |"min": 4,
         |"max": 5
         |}
         |""".stripMargin.parseJson

    val minMaxIntSample = JsonAndObj(minMaxIntFormatJsonString, IntMinMaxStructDef(4, 5))

    val minMaxFloatFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$MIN_MAX_FLOAT_TYPE",
         |"min": 0.0,
         |"max": 1.0
         |}
         |""".stripMargin.parseJson

    val minMaxFloatSample = JsonAndObj(minMaxFloatFormatJsonString, FloatMinMaxStructDef(0.0f, 1.0f))

    val minMaxDoubleFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$MIN_MAX_DOUBLE_TYPE",
         |"min": 0.0,
         |"max": 1.0
         |}
         |""".stripMargin.parseJson

    val minMaxDoubleSample = JsonAndObj(minMaxDoubleFormatJsonString, DoubleMinMaxStructDef(0.0f, 1.0f))

    val seqMinMaxIntFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_MIN_MAX_INT_TYPE",
         |"min": 5,
         |"max": 8
         |}
         |""".stripMargin.parseJson

    val seqMinMaxIntSample = JsonAndObj(seqMinMaxIntFormatJsonString, IntSeqMinMaxStructDef(5, 8))


    val seqMinMaxDoubleFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_MIN_MAX_DOUBLE_TYPE",
         |"min": 1.0,
         |"max": 2.0
         |}
         |""".stripMargin.parseJson

    val seqMinMaxDoubleSample = JsonAndObj(seqMinMaxDoubleFormatJsonString, DoubleSeqMinMaxStructDef(1.0f, 2.0f))

    val seqMinMaxFloatFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_MIN_MAX_FLOAT_TYPE",
         |"min": 1.0,
         |"max": 2.0
         |}
         |""".stripMargin.parseJson

    val seqMinMaxFloatSample = JsonAndObj(seqMinMaxFloatFormatJsonString, FloatSeqMinMaxStructDef(1.0f, 2.0f))

    val regexFormatJsonString: JsValue =
      """
        |{
        |"type": "REGEX",
        |"regex": "\\s+\\S+"
        |}
        |""".stripMargin.parseJson

    val regexSample = JsonAndObj(regexFormatJsonString, RegexStructDef("\\s+\\S+".r))

    val stringConstantFormatJsonString: JsValue =
      """
        |{
        |"type": "STRING_CONSTANT",
        |"value": "constantValue"
        |}
        |""".stripMargin.parseJson

    val stringConstantSample = JsonAndObj(stringConstantFormatJsonString, StringConstantStructDef("constantValue"))

    val seqRegexFormatJsonString: JsValue =
      """
        |{
        |"type": "SEQ_REGEX",
        |"regex": "\\s+\\S+"
        |}
        |""".stripMargin.parseJson

    val seqRegexSample = JsonAndObj(seqRegexFormatJsonString, SeqRegexStructDef(new Regex("\\s+\\S+")))

    val intChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$CHOICE_INT_TYPE",
         |"choices": [1, 2]
         |}
         |""".stripMargin.parseJson

    val intChoiceSample = JsonAndObj(intChoiceFormatJsonString, IntChoiceStructDef(Seq(1, 2)))

    val seqIntChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_CHOICE_INT_TYPE",
         |"choices": [1, 2]
         |}
         |""".stripMargin.parseJson

    val seqIntChoiceSample = JsonAndObj(seqIntChoiceFormatJsonString, IntSeqChoiceStructDef(Seq(1, 2)))

    val stringChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$CHOICE_STRING_TYPE",
         |"choices": ["a", "b"]
         |}
         |""".stripMargin.parseJson

    val stringChoiceSample = JsonAndObj(stringChoiceFormatJsonString, StringChoiceStructDef(Seq("a", "b")))

    val seqStringChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_CHOICE_STRING_TYPE",
         |"choices": ["a", "b"]
         |}
         |""".stripMargin.parseJson

    val seqStringChoiceSample = JsonAndObj(seqStringChoiceFormatJsonString, StringSeqChoiceStructDef(Seq("a", "b")))

    val nestedFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$NESTED_TYPE",
         |"fields": [
         | {
         |  "nameFormat": {
         |    "type": "STRING_CONSTANT",
         |    "value": "a"
         |  },
         |  "valueFormat": $intChoiceFormatJsonString,
         |  "required": true
         | },
         | {
         |  "nameFormat": {
         |    "type": "STRING_CONSTANT",
         |    "value": "b"
         |  },
         |  "valueFormat": $seqIntChoiceFormatJsonString,
         |  "required": true
         | }
         |],
         |"conditionalFieldsSeq": []
         |}
         |""".stripMargin.parseJson

    val eitherOfFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$EITHER_OF_TYPE",
         |"formats": [
         |  $intChoiceFormatJsonString,
         |  $minMaxFloatFormatJsonString
         |]
         |}
         |""".stripMargin.parseJson

    val intChoiceFieldDefJsonString = s"""{
      "nameFormat": {
        "type": "STRING_CONSTANT",
        "value": "c"
      },
      "valueFormat": $intChoiceFormatJsonString,
      "required": true
    }"""
    val minMaxFloatFieldDefJsonString = s"""{
      "nameFormat": {
        "type": "STRING_CONSTANT",
        "value": "d"
      },
      "valueFormat": $minMaxFloatFormatJsonString,
      "required": true
    }"""
    val conditionalFieldsJsonString: JsValue =
      s"""
         |{
         |"$CONDITION_FIELD_ID_KEY": "field1",
         |"$CONDITIONAL_MAPPING_KEY": {
         |  "a": [$intChoiceFieldDefJsonString],
         |  "b": [$minMaxFloatFieldDefJsonString]
         |}
         |}
         |""".stripMargin.parseJson

    val nestedChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$NESTED_TYPE",
         |"fields": [
         | {
         |  "nameFormat": {
         |    "type": "STRING_CONSTANT",
         |    "value": "a"
         |  },
         |  "valueFormat": $intChoiceFormatJsonString,
         |  "required": true
         | },
         | {
         |  "nameFormat": {
         |    "type": "STRING_CONSTANT",
         |    "value": "field1"
         |  },
         |  "valueFormat": $stringChoiceFormatJsonString,
         |  "required": true
         | }
         |],
         |"conditionalFieldsSeq": [
         |  $conditionalFieldsJsonString
         |]
         |}
         |""".stripMargin.parseJson

    val nestedSample = JsonAndObj(nestedFormatJsonString, NestedFieldSeqStructDef(
      Seq(
        FieldDef(StringConstantStructDef("a"), intChoiceSample.obj.asInstanceOf[StructDef[_]], required = true),
        FieldDef(StringConstantStructDef("b"), seqIntChoiceSample.obj.asInstanceOf[StructDef[_]], required = true)
      ),
      Seq.empty[ConditionalFields]
    ))

    val eitherOfSample = JsonAndObj(eitherOfFormatJsonString, EitherOfStructDef(Seq(
      intChoiceSample.obj.asInstanceOf[StructDef[_]],
      minMaxFloatSample.obj.asInstanceOf[StructDef[_]])
    ))

    val conditionalNestedSample = JsonAndObj(nestedChoiceFormatJsonString, NestedFieldSeqStructDef(
      Seq(
        FieldDef(StringConstantStructDef("a"), intChoiceSample.obj.asInstanceOf[StructDef[_]], required = true),
        FieldDef(StringConstantStructDef("field1"), stringChoiceSample.obj.asInstanceOf[StructDef[_]], required = true)
      ),
      Seq(ConditionalFields("field1", Map(
        "a" -> Seq(FieldDef(StringConstantStructDef("c"), intChoiceSample.obj.asInstanceOf[StructDef[_]], required = true)),
        "b" -> Seq(FieldDef(StringConstantStructDef("d"), minMaxFloatSample.obj.asInstanceOf[StructDef[_]], required = true)
      ))))
    ))

    val sampleCollection1: Seq[JsonAndObj] = Seq(
      minMaxFloatSample,
      minMaxDoubleSample,
      minMaxIntSample,
      seqMinMaxDoubleSample,
      seqMinMaxFloatSample,
      seqMinMaxIntSample,
      intChoiceSample,
      stringChoiceSample,
      seqIntChoiceSample,
      nestedSample,
      stringConstantSample,
      eitherOfSample,
      conditionalNestedSample
    )

  }

  "JsonFormat" must {

    "correctly parse formats" in {
      StructDefSamples.sampleCollection1.foreach(sample => {
        sample.json.convertTo[StructDef[_]] mustBe sample.obj
      })
    }

    "correctly write formats" in {
      StructDefSamples.sampleCollection1.foreach(sample => {
        sample.json mustBe JsonStructDefsFormat.write(sample.obj.asInstanceOf[StructDef[_]])
      })
    }

    "correctly parse regex format" in {
      StructDefSamples.regexSample.json.convertTo[StructDef[_]].asInstanceOf[RegexStructDef].regex.toString() mustBe "\\s+\\S+"
    }

    "correctly parse seq regex format" in {
      StructDefSamples.seqRegexSample.json.convertTo[StructDef[_]].asInstanceOf[SeqRegexStructDef].regex.toString() mustBe "\\s+\\S+"
    }

  }

}
