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

import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.JsonKeys._
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.StructDefTypes._
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, IntJsonFormat, StringJsonFormat, immSeqFormat, jsonFormat2, jsonFormat4, lazyFormat, mapFormat, rootFormat}
import spray.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.util.matching.Regex


object JsonStructDefsJsonProtocol {

  object JsonKeys {
    val TYPE_KEY = "type"
    val KEY_FORMAT_KEY = "keyFormat"
    val VALUE_FORMAT_KEY = "valueFormat"
    val DESCRIPTION_KEY = "description"
    val REGEX_KEY = "regex"
    val VALUE_KEY = "value"
    val CHOICES_KEY = "choices"
    val MIN_KEY = "min"
    val MAX_KEY = "max"
    val FIELDS_KEY = "fields"
    val CONDITIONAL_FIELDS_SEQ_KEY = "conditionalFieldsSeq"
    val NAME_FORMAT_KEY = "nameFormat"
    val REQUIRED_KEY = "required"
    val FORMATS_KEY = "formats"
    val CONDITION_FIELD_ID_KEY = "conditionFieldId"
    val CONDITIONAL_MAPPING_KEY = "mapping"
    val PER_ELEMENT_FORMAT_KEY = "perElementFormat"
  }

  object StructDefTypes {
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

  object StructDefClassifications {
    val allStringTypes = Seq(StructDefTypes.STRING_CONSTANT_TYPE, StructDefTypes.REGEX_TYPE,
      StructDefTypes.CHOICE_STRING_TYPE)
  }

  // needed to satisfy the demand for JsonFormat[Format[_]] in fieldTypeFormat
  implicit val lazyJsonStructDefsFormat: JsonFormat[StructDef[_]] = lazyFormat(JsonStructDefsFormat)
  implicit val lazyJsonConditionalFieldsFormat: RootJsonFormat[ConditionalFields] = rootFormat(lazyFormat(jsonFormat2(ConditionalFields)))
  implicit val lazyJsonStringStructDefsFormat: JsonFormat[StructDef[String]] = lazyFormat(JsonStringStructDefFormat)
  implicit val fieldDefFormat: RootJsonFormat[FieldDef] = rootFormat(lazyFormat(jsonFormat4(FieldDef)))

  implicit object JsonStringStructDefFormat extends JsonFormat[StructDef[String]] {
    override def read(json: JsValue): StructDef[String] = json match {
      case JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case t if StructDefClassifications.allStringTypes.contains(t) =>
          JsonStructDefsFormat.read(json).asInstanceOf[BaseStructDef[String]]
      }
    }

    override def write(obj: StructDef[String]): JsValue = JsonStructDefsFormat.write(obj)

  }

  implicit object JsonStructDefsFormat extends JsonFormat[StructDef[_]] {

    override def read(json: JsValue): StructDef[_] = json match {
      case JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case StructDefTypes.INT_TYPE => IntStructDef
        case StructDefTypes.STRING_TYPE => StringStructDef
        case StructDefTypes.DOUBLE_TYPE => DoubleStructDef
        case StructDefTypes.FLOAT_TYPE => FloatStructDef
        case StructDefTypes.BOOLEAN_TYPE => BooleanStructDef
        case StructDefTypes.INT_SEQ_TYPE => IntSeqStructDef
        case StructDefTypes.STRING_SEQ_TYPE => StringSeqStructDef
        case StructDefTypes.STRING_CONSTANT_TYPE =>
          val value = fields(VALUE_KEY).convertTo[String]
          StringConstantStructDef(value)
        case StructDefTypes.MIN_MAX_INT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Int]
          val max = fields(MAX_KEY).convertTo[Int]
          IntMinMaxStructDef(min, max)
        case StructDefTypes.MIN_MAX_FLOAT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Float]
          val max = fields(MAX_KEY).convertTo[Float]
          FloatMinMaxStructDef(min, max)
        case StructDefTypes.MIN_MAX_DOUBLE_TYPE =>
          val min = fields(MIN_KEY).convertTo[Double]
          val max = fields(MAX_KEY).convertTo[Double]
          DoubleMinMaxStructDef(min, max)
        case StructDefTypes.SEQ_MIN_MAX_INT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Int]
          val max = fields(MAX_KEY).convertTo[Int]
          IntSeqMinMaxStructDef(min, max)
        case StructDefTypes.SEQ_MIN_MAX_DOUBLE_TYPE =>
          val min = fields(MIN_KEY).convertTo[Double]
          val max = fields(MAX_KEY).convertTo[Double]
          DoubleSeqMinMaxStructDef(min, max)
        case StructDefTypes.SEQ_MIN_MAX_FLOAT_TYPE =>
          val min = fields(MIN_KEY).convertTo[Float]
          val max = fields(MAX_KEY).convertTo[Float]
          FloatSeqMinMaxStructDef(min, max)
        case StructDefTypes.REGEX_TYPE =>
          val regex = new Regex(fields(REGEX_KEY).convertTo[String])
          RegexStructDef(regex)
        case StructDefTypes.SEQ_REGEX_TYPE =>
          val regex = new Regex(fields(REGEX_KEY).convertTo[String])
          SeqRegexStructDef(regex)
        case StructDefTypes.CHOICE_INT_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[Int]]
          IntChoiceStructDef(choices)
        case StructDefTypes.CHOICE_STRING_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[String]]
          StringChoiceStructDef(choices)
        case StructDefTypes.SEQ_CHOICE_INT_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[Int]]
          IntSeqChoiceStructDef(choices)
        case StructDefTypes.SEQ_CHOICE_STRING_TYPE =>
          val choices = fields(CHOICES_KEY).convertTo[Seq[String]]
          StringSeqChoiceStructDef(choices)
        case StructDefTypes.NESTED_TYPE =>
          val types = fields(FIELDS_KEY).convertTo[Seq[FieldDef]]
          val conditionalFieldsSeq = fields(CONDITIONAL_FIELDS_SEQ_KEY).convertTo[Seq[ConditionalFields]]
          NestedFieldSeqStructDef(types, conditionalFieldsSeq)
        case StructDefTypes.MAP_TYPE =>
          val keyFormat = fields(KEY_FORMAT_KEY).convertTo[StructDef[String]]
          val valueFormat = fields(VALUE_FORMAT_KEY).convertTo[StructDef[_]]
          MapStructDef(keyFormat, valueFormat)
        case StructDefTypes.EITHER_OF_TYPE =>
          val formats = fields(FORMATS_KEY).convertTo[Seq[StructDef[_]]]
          EitherOfStructDef(formats)
        case StructDefTypes.GENERIC_SEQ_FORMAT_TYPE =>
          val format = fields(PER_ELEMENT_FORMAT_KEY).convertTo[StructDef[_]]
          GenericSeqStructDef(format)

      }

    }

    override def write(obj: StructDef[_]): JsValue = obj match {
      case IntStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(INT_TYPE)
      ))
      case StringStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(STRING_TYPE)
      ))
      case DoubleStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(DOUBLE_TYPE)
      ))
      case FloatStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(FLOAT_TYPE)
      ))
      case BooleanStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(BOOLEAN_TYPE)
      ))
      case IntSeqStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(INT_SEQ_TYPE)
      ))
      case StringSeqStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(STRING_SEQ_TYPE),
      ))
      case RegexStructDef(regex) => new JsObject(Map(
        TYPE_KEY -> JsString(REGEX_TYPE),
        REGEX_KEY -> JsString(regex.toString()),
      ))
      case f: StringConstantStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(STRING_CONSTANT_TYPE),
        VALUE_KEY -> JsString(f.value)
      ))
      case SeqRegexStructDef(regex) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_REGEX_TYPE),
        REGEX_KEY -> JsString(regex.toString()),
      ))
      case f: IntChoiceStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(CHOICE_INT_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsNumber(x)).toVector),
      ))
      case f: StringChoiceStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(CHOICE_STRING_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsString(x)).toVector),
      ))
      case f: IntSeqChoiceStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_CHOICE_INT_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsNumber(x)).toVector),
      ))
      case f: StringSeqChoiceStructDef => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_CHOICE_STRING_TYPE),
        CHOICES_KEY -> new JsArray(f.choices.map(x => JsString(x)).toVector),
      ))
      case IntMinMaxStructDef(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(MIN_MAX_INT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case FloatMinMaxStructDef(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(MIN_MAX_FLOAT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case DoubleMinMaxStructDef(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(MIN_MAX_DOUBLE_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case IntSeqMinMaxStructDef(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_MIN_MAX_INT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case FloatSeqMinMaxStructDef(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_MIN_MAX_FLOAT_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case DoubleSeqMinMaxStructDef(min, max) => new JsObject(Map(
        TYPE_KEY -> JsString(SEQ_MIN_MAX_DOUBLE_TYPE),
        MIN_KEY -> JsNumber(min),
        MAX_KEY -> JsNumber(max)
      ))
      case NestedFieldSeqStructDef(fields, conditionalFieldsSeq) => new JsObject(Map(
        TYPE_KEY -> JsString(NESTED_TYPE),
        FIELDS_KEY -> new JsArray(fields.map(x => {
          new JsObject(Map(
            NAME_FORMAT_KEY -> write(x.nameFormat),
            REQUIRED_KEY -> JsBoolean(x.required),
            VALUE_FORMAT_KEY -> write(x.valueFormat),
            DESCRIPTION_KEY -> JsString(x.description)
          ))
        }).toVector),
        CONDITIONAL_FIELDS_SEQ_KEY -> new JsArray(conditionalFieldsSeq.map(x => {
          new JsObject(Map(
            CONDITION_FIELD_ID_KEY -> JsString(x.conditionFieldId),
            CONDITIONAL_MAPPING_KEY -> new JsObject(x.mapping.map(entry => {
              (entry._1, new JsArray(entry._2.map(fieldDef => {
                new JsObject(Map(
                  NAME_FORMAT_KEY -> write(fieldDef.nameFormat),
                  REQUIRED_KEY -> JsBoolean(fieldDef.required),
                  VALUE_FORMAT_KEY -> write(fieldDef.valueFormat),
                  DESCRIPTION_KEY -> JsString(fieldDef.description)
                ))
              }).toVector))
            }))
          ))
        }).toVector)
      ))
      case MapStructDef(keyFormat, valueFormat) => new JsObject(Map(
        TYPE_KEY -> JsString(MAP_TYPE),
        KEY_FORMAT_KEY -> write(keyFormat),
        VALUE_FORMAT_KEY -> write(valueFormat)
      ))
      case EitherOfStructDef(formats) => new JsObject(Map(
        TYPE_KEY -> JsString(EITHER_OF_TYPE),
        FORMATS_KEY -> JsArray(formats.map(format => write(format)).toVector)
      ))
      case GenericSeqStructDef(format) => new JsObject(Map(
        TYPE_KEY -> JsString(GENERIC_SEQ_FORMAT_TYPE),
        PER_ELEMENT_FORMAT_KEY -> write(format),
      ))

      case _ => throw new IllegalArgumentException(s"no json conversion defined for object '$obj'")
    }
  }

}
