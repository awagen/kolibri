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

import de.awagen.kolibri.datatypes.io.json.AnyJsonProtocol.AnyJsonFormat
import de.awagen.kolibri.datatypes.types.JsonFormats.Validations.FunctionConversions._
import de.awagen.kolibri.datatypes.types.JsonFormats.Validations.{jsObjectFulfillsKeyAndValueFormat, logger, matchesJsObject, matchesOneOfChoices, matchesRegex, seqMatchesOneOfChoices, seqMatchesRegex, seqWithinMinMax, withinMinMax}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, IntJsonFormat, RootJsObjectFormat, StringJsonFormat, immSeqFormat}
import spray.json.{JsObject, JsValue, JsonReader}

import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.matching.Regex


object JsonFormats {

  object Validations {

    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    object FunctionConversions {

      def toAnyInput[T](func: T => Boolean): Any => Boolean = {
        x => {
          x match {
            case t: T => func.apply(t)
            case _ => false
          }
        }
      }

    }

    implicit class StringTypedAnyCheck(func: String => Boolean) {

      def toAnyInput: Any => Boolean = FunctionConversions.toAnyInput(func)

    }



    def matchesRegex(regex: Regex): String => Boolean = x => regex.matches(x)

    def seqMatchesRegex(regex: Regex): Seq[String] => Boolean = values => values.forall(matchesRegex(regex))

    def matchesOneOfChoices[T](choices: Seq[T]): T => Boolean = value => choices.contains(value)

    def seqMatchesOneOfChoices[T](choices: Seq[T]): Seq[T] => Boolean = values => values.forall(matchesOneOfChoices(choices))

    def withinMinMax[T: Numeric](min: T, max: T): T => Boolean = value => value >= min && value <= max

    def seqWithinMinMax[T: Numeric](min: T, max: T): Seq[T] => Boolean = values => values.forall(withinMinMax(min, max))

    def getJsObjKeyForFormat(field: FieldType, jsObject: JsObject): Option[String] = {
      jsObject.fields.keys.find(x => field.nameFormat.isValid(x))
    }

    def canBeCastToCorrectType(jsValue: JsValue, format: Format[_]): Boolean = {
      try {
        format.cast(jsValue)
        true
      }
      catch {
        case _: Exception =>
          logger.warn(s"json value '$jsValue' did not pass validation defined by format '$format'")
          false
      }
    }

    def jsObjectContainsKeyWithValidValue(field: FieldType, jsObject: JsObject): Boolean = {
      val found = getJsObjKeyForFormat(field, jsObject)
      found.exists(foundField => canBeCastToCorrectType(jsObject.fields(foundField), field.format))
    }

    def matchesJsObject(fields: Seq[FieldType]): JsObject => Boolean = jsObj => {
      fields.forall(field => {
        val matchingJsObjKey = getJsObjKeyForFormat(field, jsObj)
        try {
          if (!field.required) {
            if (matchingJsObjKey.nonEmpty){
              canBeCastToCorrectType(jsObj.fields(matchingJsObjKey.get), field.format)
            }
            else true
          }
          else {
            jsObjectContainsKeyWithValidValue(field, jsObj)
          }
        }
        catch {
          case _: Exception =>
            logger.warn(s"field '$field' did not pass validation for given object '$jsObj'")
            false
        }
      })
    }

    def jsObjectFulfillsKeyAndValueFormat(keyFormat: Format[String], valueFormat: Format[_], jsObj: JsObject): Boolean = {
      val failedKeyValidations: Iterable[String] = jsObj.fields.keys.filter(key => !keyFormat.isValid(key))
      val failedValueValidations: Iterable[JsValue] = jsObj.fields.values.filter(value => {
        !canBeCastToCorrectType(value, valueFormat)
      })
      failedKeyValidations.foreach(x => {
        logger.warn(s"wrong format for key '$x' ")
      })
      failedValueValidations.foreach(x => {
        logger.warn(s"wrong format for value '$x'")
      })
      failedValueValidations.isEmpty && failedValueValidations.isEmpty
    }
  }

  /**
   * Format for type T. Provides cast function to cast JsValue to specific type
   *
   * @param ev - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  abstract class Format[+T](val isValidFunc: Any => Boolean)(implicit ev: JsonReader[T]) {

    def isValid(el: Any): Boolean = isValidFunc.apply(el)

    def cast(value: JsValue): T = {
      value.convertTo[T] match {
        case e if !isValid(e) =>
          throw new IllegalArgumentException(s"value '$e' is not valid")
        case e => e
      }
    }
  }

  /**
   * Format
   * @param regex
   */
  case class RegexFormat(regex: Regex) extends Format[String](toAnyInput(matchesRegex(regex)))

  case class SeqRegexFormat(regex: Regex) extends Format[Seq[String]](toAnyInput(seqMatchesRegex(regex)))

  /**
   * Format only allowing values within predefined choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class ChoiceFormat[T](choices: Seq[T])(implicit ev: JsonReader[T]) extends Format[T](matchesOneOfChoices(choices))
  case class IntChoiceFormat(choices: Seq[Int]) extends ChoiceFormat[Int](choices)
  case class StringChoiceFormat(choices: Seq[String]) extends ChoiceFormat[String](choices)

  /**
   * format for sequence of values of type T, where each element must be contained within the passed choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class SeqChoiceFormat[T](choices: Seq[T])(implicit ev: JsonReader[Seq[T]]) extends Format[Seq[T]](toAnyInput(seqMatchesOneOfChoices(choices)))
  case class IntSeqChoiceFormat(choices: Seq[Int]) extends SeqChoiceFormat[Int](choices)
  case class StringSeqChoiceFormat(choices: Seq[String]) extends SeqChoiceFormat[String](choices)

  /**
   * Format requiring value (must be numeric) to be within range defined by min and max value
   *
   * @param min - minimum allowed value
   * @param max - maximum allowed value
   * @param ev  - json reader for specified type T
   * @param evR - implicit json reader for type T
   * @tparam T - type to cast to
   */
  class MinMaxFormat[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[T]) extends Format[T](toAnyInput(withinMinMax(min, max)))
  case class IntMinMaxFormat(min: Int, max: Int) extends MinMaxFormat[Int](min, max)
  case class FloatMinMaxFormat(min: Float, max: Float) extends MinMaxFormat[Float](min, max)
  case class DoubleMinMaxFormat(min: Double, max: Double) extends MinMaxFormat[Double](min, max)

  /**
   * Format for sequence of numerical values where each needs to be within the defined min and max values
   * Throws IllegalArgumentException in case any element does not match criteria
   *
   * @param min - minimum allowed value
   * @param max - maximum allowed value
   * @param ev  - evidence for numeric type T
   * @param evR - implicit reader for Seq[T]
   * @tparam T - type of the elements
   */
  class SeqMinMaxFormat[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[Seq[T]]) extends Format[Seq[T]](toAnyInput(seqWithinMinMax(min, max)))
  case class IntSeqMinMaxFormat(min: Int, max: Int) extends SeqMinMaxFormat[Int](min, max)
  case class FloatSeqMinMaxFormat(min: Float, max: Float) extends SeqMinMaxFormat[Float](min, max)
  case class DoubleSeqMinMaxFormat(min: Double, max: Double) extends SeqMinMaxFormat[Double](min, max)


  object IntFormat extends Format[Int](_ => true)

  object StringFormat extends Format[String](_ => true)

  object DoubleFormat extends Format[Double](_ => true)

  object FloatFormat extends Format[Float](_ => true)

  object BooleanFormat extends Format[Boolean](_ => true)

  object IntSeqFormat extends Format[Seq[Int]](_ => true)

  object StringSeqFormat extends Format[Seq[String]](_ => true)

  /**
   * Format accepting elements that is validated by any of the formats
   * (uses the first one thatr works)
   */
  case class EitherOfFormat(formats: Seq[Format[_]]) extends Format[Any](x => formats.exists(format => format.isValid(x))) {

    def isValidFormat(format: Format[_], element: JsValue): Boolean = {
      try {
        val castValue = format.cast(element)
        format.isValid(castValue)
      }
      catch {
        case _: Exception =>
          false
      }
    }

    override def cast(value: JsValue): Any = {
      val format: Option[Format[_]] = formats.find(x => isValidFormat(x, value))
      val castValueOpt: Option[Any] = format.map(x => x.cast(value))
      if (castValueOpt.isEmpty) throw new IllegalArgumentException(s"value '$value' can not be validated by any available format $formats")
      castValueOpt.get
    }

  }

  object DoubleSeqFormat extends Format[Seq[Double]](_ => true)

  object FloatSeqFormat extends Format[Seq[Float]](_ => true)

  object BoolSeqFormat extends Format[Seq[Boolean]](_ => true)

  case class StringConstantFormat(value: String) extends Format[String](x => x.equals(value))

  case class FieldType(nameFormat: Format[String], format: Format[_], required: Boolean)

  /**
   * Suitable if a nested format shall be specified where specific key values are not specified
   * but are subjected to the same format check
   */
  case class MapFormat(keyFormat: Format[String], valueFormat: Format[_]) extends Format[JsObject](toAnyInput(x => jsObjectFulfillsKeyAndValueFormat(keyFormat, valueFormat, x)))

  /**
   * Format for nested structure where field names are known and each name/value pair have their
   * own formats (separately for key and value, see FieldType)
   */
  case class NestedFormat(fields: Seq[FieldType]) extends Format[JsObject](toAnyInput(matchesJsObject(fields)))

}
