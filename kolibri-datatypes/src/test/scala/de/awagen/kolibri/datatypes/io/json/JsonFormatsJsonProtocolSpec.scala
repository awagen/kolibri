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

import de.awagen.kolibri.datatypes.io.json.JsonFormatsJsonProtocol.FormatTypes._
import de.awagen.kolibri.datatypes.io.json.JsonFormatsJsonProtocol._
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.JsonFormats._
import spray.json.{JsValue, _}

import scala.util.matching.Regex

class JsonFormatsJsonProtocolSpec extends UnitTestSpec {

  object FormatSamples {

    case class JsonAndObj(json: JsValue, obj: Any)

    val minMaxIntFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$MIN_MAX_INT_TYPE",
         |"min": 4,
         |"max": 5
         |}
         |""".stripMargin.parseJson

    val minMaxIntSample = JsonAndObj(minMaxIntFormatJsonString, IntMinMaxFormat(4, 5))

    val minMaxFloatFormatJsonString: JsValue =
      s"""
        |{
        |"type": "$MIN_MAX_FLOAT_TYPE",
        |"min": 0.0,
        |"max": 1.0
        |}
        |""".stripMargin.parseJson

    val minMaxFloatSample = JsonAndObj(minMaxFloatFormatJsonString, FloatMinMaxFormat(0.0f, 1.0f))

    val minMaxDoubleFormatJsonString: JsValue =
      s"""
        |{
        |"type": "$MIN_MAX_DOUBLE_TYPE",
        |"min": 0.0,
        |"max": 1.0
        |}
        |""".stripMargin.parseJson

    val minMaxDoubleSample = JsonAndObj(minMaxDoubleFormatJsonString, DoubleMinMaxFormat(0.0f, 1.0f))

    val seqMinMaxIntFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_MIN_MAX_INT_TYPE",
         |"min": 5,
         |"max": 8
         |}
         |""".stripMargin.parseJson

    val seqMinMaxIntSample = JsonAndObj(seqMinMaxIntFormatJsonString, IntSeqMinMaxFormat(5, 8))


    val seqMinMaxDoubleFormatJsonString: JsValue =
      s"""
        |{
        |"type": "$SEQ_MIN_MAX_DOUBLE_TYPE",
        |"min": 1.0,
        |"max": 2.0
        |}
        |""".stripMargin.parseJson

    val seqMinMaxDoubleSample = JsonAndObj(seqMinMaxDoubleFormatJsonString, DoubleSeqMinMaxFormat(1.0f, 2.0f))

    val seqMinMaxFloatFormatJsonString: JsValue =
      s"""
        |{
        |"type": "$SEQ_MIN_MAX_FLOAT_TYPE",
        |"min": 1.0,
        |"max": 2.0
        |}
        |""".stripMargin.parseJson

    val seqMinMaxFloatSample = JsonAndObj(seqMinMaxFloatFormatJsonString, FloatSeqMinMaxFormat(1.0f, 2.0f))

    val regexFormatJsonString: JsValue =
      """
        |{
        |"type": "REGEX",
        |"regex": "\\s+\\S+"
        |}
        |""".stripMargin.parseJson

    val regexSample = JsonAndObj(regexFormatJsonString, RegexFormat("\\s+\\S+".r))

    val stringConstantFormatJsonString: JsValue =
      """
        |{
        |"type": "STRING_CONSTANT",
        |"value": "constantValue"
        |}
        |""".stripMargin.parseJson

    val stringConstantSample = JsonAndObj(stringConstantFormatJsonString, StringConstantFormat("constantValue"))

    val seqRegexFormatJsonString: JsValue =
      """
        |{
        |"type": "SEQ_REGEX",
        |"regex": "\\s+\\S+"
        |}
        |""".stripMargin.parseJson

    val seqRegexSample = JsonAndObj(seqRegexFormatJsonString, SeqRegexFormat(new Regex("\\s+\\S+")))

    val intChoiceFormatJsonString: JsValue =
      s"""
        |{
        |"type": "$CHOICE_INT_TYPE",
        |"choices": [1, 2]
        |}
        |""".stripMargin.parseJson

    val intChoiceSample = JsonAndObj(intChoiceFormatJsonString, IntChoiceFormat(Seq(1,2)))

    val seqIntChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_CHOICE_INT_TYPE",
         |"choices": [1, 2]
         |}
         |""".stripMargin.parseJson

    val seqIntChoiceSample = JsonAndObj(seqIntChoiceFormatJsonString, IntSeqChoiceFormat(Seq(1,2)))

    val stringChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$CHOICE_STRING_TYPE",
         |"choices": ["a", "b"]
         |}
         |""".stripMargin.parseJson

    val stringChoiceSample = JsonAndObj(stringChoiceFormatJsonString, StringChoiceFormat(Seq("a", "b")))

    val seqStringChoiceFormatJsonString: JsValue =
      s"""
         |{
         |"type": "$SEQ_CHOICE_STRING_TYPE",
         |"choices": ["a", "b"]
         |}
         |""".stripMargin.parseJson

    val seqStringChoiceSample = JsonAndObj(seqStringChoiceFormatJsonString, StringSeqChoiceFormat(Seq("a", "b")))

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
         |  "format": $intChoiceFormatJsonString,
         |  "required": true
         | },
         | {
         |  "nameFormat": {
         |    "type": "STRING_CONSTANT",
         |    "value": "b"
         |  },
         |  "format": $seqIntChoiceFormatJsonString,
         |  "required": true
         | }
         |]
         |}
         |""".stripMargin.parseJson

    val nestedSample = JsonAndObj(nestedFormatJsonString, NestedFormat(Seq(
      FieldType(StringConstantFormat("a"), intChoiceSample.obj.asInstanceOf[Format[_]], required = true),
      FieldType(StringConstantFormat("b"), seqIntChoiceSample.obj.asInstanceOf[Format[_]], required = true)
    )))

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
      stringConstantSample
    )

  }

  "JsonFormat" must {

    "correctly parse formats" in {
      FormatSamples.sampleCollection1.foreach(sample => {
        sample.json.convertTo[Format[_]] mustBe sample.obj
      })
    }

    "correctly write formats" in {
      FormatSamples.sampleCollection1.foreach(sample => {
        sample.json mustBe JsonFormatsFormat.write(sample.obj.asInstanceOf[Format[_]])
      })
    }

    "correctly parse regex format" in {
      FormatSamples.regexSample.json.convertTo[Format[_]].asInstanceOf[RegexFormat].regex.toString() mustBe "\\s+\\S+"
    }

    "correctly parse seq regex format" in {
      FormatSamples.seqRegexSample.json.convertTo[Format[_]].asInstanceOf[SeqRegexFormat].regex.toString() mustBe "\\s+\\S+"
    }

  }

}
