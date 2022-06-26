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
import de.awagen.kolibri.datatypes.io.json.JsonFormatsJsonProtocol.JsonKeys._
import de.awagen.kolibri.datatypes.types.JsonFormats._
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, IntJsonFormat, StringJsonFormat, immSeqFormat, jsonFormat3, lazyFormat, mapFormat, rootFormat}
import spray.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.util.matching.Regex


object JsonFormatsJsonProtocol {

  object JsonKeys {
    val TYPE_KEY = "type"
    val KEY_FORMAT_KEY = "keyFormat"
    val VALUE_FORMAT_KEY = "valueFormat"
    val REGEX_KEY = "regex"
    val VALUE_KEY = "value"
    val CHOICES_KEY = "choices"
    val MIN_KEY = "min"
    val MAX_KEY = "max"
    val FIELDS_KEY = "fields"
    val NAME_FORMAT_KEY = "nameFormat"
    val REQUIRED_KEY = "required"
    val FORMATS_KEY = "formats"
    val CONDITION_FIELD_ID_KEY = "conditionFieldId"
    val CONDITION_FIELD_VALUES_TO_FORMAT_KEY = "conditionFieldValuesToFormat"
    val PER_ELEMENT_FORMAT_KEY = "perElementFormat"
  }

  object FormatTypes {
    val INT_TYPE = "INT"
    val STRING_TYPE = "STRING"
    val DOUBLE_TYPE = "DOUBLE"
    val FLOAT_TYPE = "FLOAT"
    val BOOLEAN_TYPE = "BOOLEAN"
    val INT_SEQ_TYPE = "INT_SEQ"
    val STRING_SEQ_TYPE = "STRING_SEQ"
    val REGEX_TYPE = "REGEX"
    val STRING_CONSTANT_TYPE = "STRING_CONSTANT"
    val SEQ_REGEX_TYPE = "SEQ_REGEX"
    val CHOICE_INT_TYPE = "CHOICE_INT"
    val CHOICE_FLOAT_TYPE = "CHOICE_FLOAT"
    val CHOICE_DOUBLE_TYPE = "CHOICE_DOUBLE"
    val CHOICE_STRING_TYPE = "CHOICE_STRING"
    val SEQ_CHOICE_INT_TYPE = "SEQ_CHOICE_INT"
    val SEQ_CHOICE_FLOAT_TYPE = "SEQ_CHOICE_FLOAT"
    val SEQ_CHOICE_DOUBLE_TYPE = "SEQ_CHOICE_DOUBLE"
    val SEQ_CHOICE_STRING_TYPE = "SEQ_CHOICE_STRING"
    val SEQ_MIN_MAX_FLOAT_TYPE = "SEQ_MIN_MAX_FLOAT"
    val SEQ_MIN_MAX_DOUBLE_TYPE = "SEQ_MIN_MAX_DOUBLE"
    val SEQ_MIN_MAX_INT_TYPE = "SEQ_MIN_MAX_INT"
    val NESTED_TYPE = "NESTED"
    val MAP_TYPE = "MAP"
    val EITHER_OF_TYPE = "EITHER_OF"
    val CONDITIONAL_CHOICE_TYPE = "CONDITIONAL_CHOICE"
    val GENERIC_SEQ_FORMAT_TYPE = "GENERIC_SEQ_FORMAT"
    val MIN_MAX_INT_TYPE = "MIN_MAX_INT"
    val MIN_MAX_FLOAT_TYPE = "MIN_MAX_FLOAT"
    val MIN_MAX_DOUBLE_TYPE = "MIN_MAX_DOUBLE"
  }

  object FormatClassifications {
    val allStringTypes = Seq(FormatTypes.STRING_CONSTANT_TYPE, FormatTypes.REGEX_TYPE,
      FormatTypes.CHOICE_STRING_TYPE)
  }

  // needed to satisfy the demand for JsonFormat[Format[_]] in fieldTypeFormat
  implicit val lazyJsonFormatsFormat: JsonFormat[Format[_]] = lazyFormat(JsonFormatsFormat)
  implicit val lazyJsonStringFormatsFormat: JsonFormat[Format[String]] = lazyFormat(JsonStringFormatsFormat)
  implicit val fieldTypeFormat: RootJsonFormat[FieldType] = rootFormat(lazyFormat(jsonFormat3(FieldType)))

  implicit object JsonStringFormatsFormat extends JsonFormat[Format[String]] {
    override def read(json: JsValue): Format[String] = json match {
      case JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case t if FormatClassifications.allStringTypes.contains(t) =>
          JsonFormatsFormat.read(json).asInstanceOf[BaseFormat[String]]
      }
    }

    override def write(obj: Format[String]): JsValue = JsonFormatsFormat.write(obj)

  }

  implicit object JsonFormatsFormat extends JsonFormat[Format[_]] {

    override def read(json: JsValue): Format[_] = json match {
      case JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case FormatTypes.INT_TYPE => IntFormat
        case FormatTypes.STRING_TYPE => StringFormat
        case FormatTypes.DOUBLE_TYPE => DoubleFormat
        case FormatTypes.FLOAT_TYPE => FloatFormat
        case FormatTypes.BOOLEAN_TYPE => BooleanFormat
        case FormatTypes.INT_SEQ_TYPE => IntSeqFormat
        case FormatTypes.STRING_SEQ_TYPE => StringSeqFormat
        case FormatTypes.STRING_CONSTANT_TYPE =>
          val value = fields(VALUE_KEY).convertTo[String]
          StringConstantFormat(value)
        case FormatTypes.MIN_MAX_INT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Int]
          val max = fields(MAX_KEY).convertTo[Int]
          IntMinMaxFormat(min, max)
        case FormatTypes.MIN_MAX_FLOAT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Float]
          val max = fields(MAX_KEY).convertTo[Float]
          FloatMinMaxFormat(min, max)
        case FormatTypes.MIN_MAX_DOUBLE_TYPE =>
          val min = fields(MIN_KEY).convertTo[Double]
          val max = fields(MAX_KEY).convertTo[Double]
          DoubleMinMaxFormat(min, max)
        case FormatTypes.SEQ_MIN_MAX_INT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Int]
          val max = fields(MAX_KEY).convertTo[Int]
          IntSeqMinMaxFormat(min, max)
        case FormatTypes.SEQ_MIN_MAX_DOUBLE_TYPE =>
          val min = fields(MIN_KEY).convertTo[Double]
          val max = fields(MAX_KEY).convertTo[Double]
          DoubleSeqMinMaxFormat(min, max)
        case FormatTypes.SEQ_MIN_MAX_FLOAT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Float]
          val max = fields(MAX_KEY).convertTo[Float]
          FloatSeqMinMaxFormat(min, max)
        case FormatTypes.REGEX_TYPE =>
          val regex = new Regex(fields(REGEX_KEY).convertTo[String])
          RegexFormat(regex)
        case FormatTypes.SEQ_REGEX_TYPE =>
          val regex = new Regex(fields(REGEX_KEY).convertTo[String])
          SeqRegexFormat(regex)
        case FormatTypes.CHOICE_INT_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[Int]]
          IntChoiceFormat(choices)
        case FormatTypes.CHOICE_STRING_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[String]]
          StringChoiceFormat(choices)
        case FormatTypes.SEQ_CHOICE_INT_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[Int]]
          IntSeqChoiceFormat(choices)
        case FormatTypes.SEQ_CHOICE_STRING_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[String]]
          StringSeqChoiceFormat(choices)
        case FormatTypes.NESTED_TYPE =>
          val types = fields(FIELDS_KEY).convertTo[Seq[FieldType]]
          NestedFieldSeqFormat(types)
        case FormatTypes.MAP_TYPE =>
          val keyFormat = fields(KEY_FORMAT_KEY).convertTo[Format[String]]
          val valueFormat = fields(VALUE_FORMAT_KEY).convertTo[Format[_]]
          MapFormat(keyFormat, valueFormat)
        case FormatTypes.EITHER_OF_TYPE =>
          val formats = fields(FORMATS_KEY).convertTo[Seq[Format[_]]]
          EitherOfFormat(formats)
        case FormatTypes.CONDITIONAL_CHOICE_TYPE =>
          val conditionFieldId = fields(CONDITION_FIELD_ID_KEY).convertTo[String]
          val conditionFieldValuesToFormat = fields(CONDITION_FIELD_VALUES_TO_FORMAT_KEY).convertTo[Map[String, Format[_]]]
          ConditionalFieldValueChoiceFormat(conditionFieldId, conditionFieldValuesToFormat)
        case FormatTypes.GENERIC_SEQ_FORMAT_TYPE =>
          val format = fields(PER_ELEMENT_FORMAT_KEY).convertTo[Format[_]]
          GenericSeqFormat(format)

      }

    }

    override def write(obj: Format[_]): JsValue = obj match {
      case IntFormat => new JsObject(Map(
        TYPE_KEY -> JsString(INT_TYPE)
      ))
      case StringFormat => new JsObject(Map(
        TYPE_KEY -> JsString(STRING_TYPE)
      ))
      case DoubleFormat => new JsObject(Map(
        TYPE_KEY -> JsString(DOUBLE_TYPE)
      ))
      case FloatFormat => new JsObject(Map(
        TYPE_KEY -> JsString(FLOAT_TYPE)
      ))
      case BooleanFormat => new JsObject(Map(
        TYPE_KEY -> JsString(BOOLEAN_TYPE)
      ))
      case IntSeqFormat => new JsObject(Map(
        TYPE_KEY -> JsString(INT_SEQ_TYPE)
      ))
      case StringSeqFormat => new JsObject(Map(
        TYPE_KEY -> JsString(STRING_SEQ_TYPE),
      ))
      case RegexFormat(regex) => new JsObject(Map(
        TYPE_KEY -> JsString(REGEX_TYPE),
        REGEX_KEY -> JsString(regex.toString()),
      ))
      case f: StringConstantFormat => new JsObject(Map(
        TYPE_KEY -> JsString(STRING_CONSTANT_TYPE),
        VALUE_KEY -> JsString(f.value)
      ))
      case SeqRegexFormat(regex) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_REGEX_TYPE),
        REGEX_KEY -> JsString(regex.toString()),
      ))
      case f: IntChoiceFormat => new JsObject(Map(
        TYPE_KEY -> JsString(CHOICE_INT_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsNumber(x)).toVector),
      ))
      case f: StringChoiceFormat => new JsObject(Map(
        TYPE_KEY -> JsString(CHOICE_STRING_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsString(x)).toVector),
      ))
      case f: IntSeqChoiceFormat => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_CHOICE_INT_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsNumber(x)).toVector),
      ))
      case f: StringSeqChoiceFormat => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_CHOICE_STRING_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsString(x)).toVector),
      ))
      case IntMinMaxFormat(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(MIN_MAX_INT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case FloatMinMaxFormat(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(MIN_MAX_FLOAT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case DoubleMinMaxFormat(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(MIN_MAX_DOUBLE_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case IntSeqMinMaxFormat(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_MIN_MAX_INT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case FloatSeqMinMaxFormat(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_MIN_MAX_FLOAT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case DoubleSeqMinMaxFormat(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_MIN_MAX_DOUBLE_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case NestedFieldSeqFormat(fields) => new JsObject(Map(
        TYPE_KEY -> JsString(NESTED_TYPE),
        FIELDS_KEY -> new JsArray(fields.map(x => {
          new JsObject(Map(
            NAME_FORMAT_KEY -> write(x.nameFormat),
            REQUIRED_KEY -> JsBoolean(x.required),
            VALUE_FORMAT_KEY -> write(x.valueFormat)
          ))
        }).toVector)
      ))
      case MapFormat(keyFormat, valueFormat) => new JsObject(Map(
        TYPE_KEY -> JsString(MAP_TYPE),
        KEY_FORMAT_KEY -> write(keyFormat),
        VALUE_FORMAT_KEY -> write(valueFormat)
      ))
      case EitherOfFormat(formats) => new JsObject(Map(
        TYPE_KEY -> JsString(EITHER_OF_TYPE),
        FORMATS_KEY -> JsArray(formats.map(format => write(format)).toVector)
      ))
      case ConditionalFieldValueChoiceFormat(conditionFieldId, conditionFieldValuesToFormat) => new JsObject(Map(
        TYPE_KEY -> JsString(CONDITIONAL_CHOICE_TYPE),
        CONDITION_FIELD_ID_KEY -> JsString(conditionFieldId),
        CONDITION_FIELD_VALUES_TO_FORMAT_KEY -> JsObject(conditionFieldValuesToFormat.view.map(x => (x._1, write(x._2))).toSeq:_*)
      ))
      case GenericSeqFormat(format) => new JsObject(Map(
        TYPE_KEY -> JsString(GENERIC_SEQ_FORMAT_TYPE),
        PER_ELEMENT_FORMAT_KEY -> write(format),
      ))

      case _ => throw new IllegalArgumentException(s"no json conversion defined for object '$obj'")
    }
  }

}
