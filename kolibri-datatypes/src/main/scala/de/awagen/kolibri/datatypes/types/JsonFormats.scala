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

import de.awagen.kolibri.datatypes.types.JsonFormats.Validations.{matchesJsObject, matchesOneOfChoices, matchesRegex, seqMatchesOneOfChoices, seqMatchesRegex, seqWithinMinMax, withinMinMax}
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, IntJsonFormat, RootJsObjectFormat, StringJsonFormat, immSeqFormat}
import spray.json.{JsObject, JsValue, JsonReader}

import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.matching.Regex

object JsonFormats {

  object Validations {

    def matchesRegex(regex: Regex): String => Boolean = x => regex.matches(x)

    def seqMatchesRegex(regex: Regex): Seq[String] => Boolean = values => values.forall(matchesRegex(regex))

    def matchesOneOfChoices[T](choices: Seq[T]): T => Boolean = value => choices.contains(value)

    def seqMatchesOneOfChoices[T](choices: Seq[T]): Seq[T] => Boolean = values => values.forall(matchesOneOfChoices(choices))

    def withinMinMax[T: Numeric](min: T, max: T): T => Boolean = value => value >= min && value <= max

    def seqWithinMinMax[T: Numeric](min: T, max: T): Seq[T] => Boolean = values => values.forall(withinMinMax(min, max))

    def matchesJsObject(fields: Seq[FieldType[Any]]): JsObject => Boolean = jsObj => {
      fields.forall(field => {
        try {
          if (!(field.required || jsObj.fields.contains(field.name))) true
          else {
            field.format.cast(jsObj.fields(field.name))
            true
          }
        }
        catch {
          case _: Exception => false
        }
      })
    }
  }

  /**
   * Format for type T. Provides cast function to cast JsValue to specific type
   *
   * @param ev - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class Format[T](val isValid: T => Boolean)(implicit ev: JsonReader[T]) {

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
  case class RegexFormat(regex: Regex) extends Format[String](matchesRegex(regex))

  case class SeqRegexFormat(regex: Regex) extends Format[Seq[String]](seqMatchesRegex(regex))

  /**
   * Format only allowing values within predefined choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  case class ChoiceFormat[T](choices: Seq[T])(implicit ev: JsonReader[T]) extends Format[T](matchesOneOfChoices(choices))

  /**
   * format for sequence of values of type T, where each element must be contained within the passed choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  case class SeqChoiceFormat[T](choices: Seq[T])(implicit ev: JsonReader[Seq[T]]) extends Format[Seq[T]](seqMatchesOneOfChoices(choices))

  /**
   * Format requiring value (must be numeric) to be within range defined by min and max value
   *
   * @param min - minimum allowed value
   * @param max - maximum allowed value
   * @param ev  - json reader for specified type T
   * @param evR - implicit json reader for type T
   * @tparam T - type to cast to
   */
  case class MinMaxFormat[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[T]) extends Format[T](withinMinMax(min, max))

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
  case class SeqMinMaxFormat[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[Seq[T]]) extends Format[Seq[T]](seqWithinMinMax(min, max))

  object IntFormat extends Format[Int](_ => true)

  object StringFormat extends Format[String](_ => true)

  object DoubleFormat extends Format[Double](_ => true)

  object FloatFormat extends Format[Float](_ => true)

  object BooleanFormat extends Format[Boolean](_ => true)

  object IntSeqFormat extends Format[Seq[Int]](_ => true)

  object StringSeqFormat extends Format[Seq[String]](_ => true)

  object DoubleSeqFormat extends Format[Seq[Double]](_ => true)

  object FloatSeqFormat extends Format[Seq[Float]](_ => true)

  object BoolSeqFormat extends Format[Seq[Boolean]](_ => true)

  case class FieldType[T](name: String, format: Format[T], required: Boolean)

  case class NestedFormat(fields: Seq[FieldType[Any]]) extends Format[JsObject](matchesJsObject(fields))

}
