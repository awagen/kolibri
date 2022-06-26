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
import de.awagen.kolibri.datatypes.types.JsonFormats.Validations.{canBeCastAndIsValidFormat, jsObjectFulfillsKeyAndValueFormat, matchesOneOfChoices, matchesRegex, matchesValueMap, seqMatchesOneOfChoices, seqMatchesRegex, seqWithinMinMax, withinMinMax}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, IntJsonFormat, StringJsonFormat, immSeqFormat, mapFormat}
import spray.json.{JsArray, JsObject, JsValue, JsonReader}

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

    def getMapKeyForFormat(field: FieldType, map: Map[String, _]): Option[String] = {
      map.keys.find(x => field.nameFormat.isValid(x))
    }

    def canBeCastAndIsValidFormat(format: Format[_], element: JsValue): Boolean = {
      try {
        val castValue = format.cast(element)
        format.isValid(castValue)
      }
      catch {
        case _: Exception =>
          false
      }
    }

    def jsValueCanBeCast(jsValue: JsValue, format: Format[_]): Boolean = {
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

    def mapContainsKeyWithValidValue(field: FieldType, map: Map[String, _]): Boolean = {
      val found = getMapKeyForFormat(field, map)
      found.exists(foundField => field.valueFormat.isValid(map(foundField)))
    }

    def matchesValueMap(fields: Seq[FieldType]): Map[String, _] => Boolean = map => {
      fields.forall(field => {
        val matchingKey: Option[String] = getMapKeyForFormat(field, map)
        try {
          if (!field.required) {
            if (matchingKey.nonEmpty){
              field.valueFormat.isValid(map(matchingKey.get))
            }
            else true
          }
          else {
            mapContainsKeyWithValidValue(field, map)
          }
        }
        catch {
          case _: Exception =>
            logger.warn(s"field '$field' did not pass validation for given object '$map'")
            false
        }
      })
    }

    def jsObjectFulfillsKeyAndValueFormat(keyFormat: Format[String], valueFormat: Format[_], jsObj: JsObject): Boolean = {
      val failedKeyValidations: Iterable[String] = jsObj.fields.keys.filter(key => !keyFormat.isValid(key))
      val failedValueValidations: Iterable[JsValue] = jsObj.fields.values.filter(value => {
        !jsValueCanBeCast(value, valueFormat)
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

  def castIfMatchingFormatExistsElseThrowException(formats: Seq[Format[_]], value: JsValue): Any = {
    val format: Option[Format[_]] = formats.find(x => canBeCastAndIsValidFormat(x, value))
    val castValueOpt: Option[Any] = format.map(x => x.cast(value))
    if (castValueOpt.isEmpty) throw new IllegalArgumentException(s"value '$value' can not be validated by any available format $formats")
    castValueOpt.get
  }

  /**
   * Basic Format casting to specified type and checking validity of the value.
   * Note that cast is intended to happen before and isValid is then executed on the cast value
   * to check validity
   */
  trait Format[+T] {

    def isValid(el: Any): Boolean

    def cast(value: JsValue): T

  }

  /**
   * Format for type T. Provides cast function to cast JsValue to specific type
   *
   * @param ev - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  abstract class BaseFormat[+T](val isValidFunc: Any => Boolean = _ => true)(implicit ev: JsonReader[T]) extends Format[T] {

    def isValid(el: Any): Boolean = isValidFunc.apply(el)

    def cast(value: JsValue): T = {
      value.convertTo[T] match {
        case e if !isValid(e) =>
          throw new IllegalArgumentException(s"value '$e' is not valid")
        case e =>
          e
      }
    }
  }

  /**
   * Format type indicating that it contains information about multiple fields instead of a single one,
   * representing a nested structure (e.g a JsObject)
   */
  trait NestedFormat[+T] extends Format[Map[String, T]] {

    def fields: Seq[FieldType]

  }

  /**
   * Conditional format that changes applied format depending on the value of the field defined as condition.
   * Note that this is limited to string keys right now, so main usage is some type selector that
   * causes conditional format switch depending on its currently selected value.
   * Mainly serves to avoid subsequent requests to backend, while all conditional info can be
   * consistently submitted to frontend to apply first line of validations and suggestions of possible values
   * there.
   * Note that the right isValid methods have to be invoked, and main place of invoking will be within the
   * NestedFormat, since other formats mainly have single fields in scope, while here we need the
   * conditioned-on field as well
   */
  trait ConditionalFormat[+T] extends Format[T] {

    def conditionFieldId: String

    def conditionFieldValuesToFormat: Map[String, Format[T]]

    def isValid(conditionValue: String, conditionedValue: Any): Boolean

    def cast(conditionValue: String, value: JsValue): T

  }

  /**
   * Format
   * @param regex
   */
  case class RegexFormat(regex: Regex) extends BaseFormat[String](toAnyInput(matchesRegex(regex)))

  case class SeqRegexFormat(regex: Regex) extends BaseFormat[Seq[String]](toAnyInput(seqMatchesRegex(regex)))

  /**
   * Format only allowing values within predefined choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class ChoiceFormat[T](choices: Seq[T])(implicit ev: JsonReader[T]) extends BaseFormat[T](matchesOneOfChoices(choices))
  case class IntChoiceFormat(choices: Seq[Int]) extends ChoiceFormat[Int](choices)
  case class StringChoiceFormat(choices: Seq[String]) extends ChoiceFormat[String](choices)

  /**
   * format for sequence of values of type T, where each element must be contained within the passed choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class SeqChoiceFormat[T](choices: Seq[T])(implicit ev: JsonReader[Seq[T]]) extends BaseFormat[Seq[T]](toAnyInput(seqMatchesOneOfChoices(choices)))
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
  class MinMaxFormat[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[T]) extends BaseFormat[T](toAnyInput(withinMinMax(min, max)))
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
  class SeqMinMaxFormat[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[Seq[T]]) extends BaseFormat[Seq[T]](toAnyInput(seqWithinMinMax(min, max)))
  case class IntSeqMinMaxFormat(min: Int, max: Int) extends SeqMinMaxFormat[Int](min, max)
  case class FloatSeqMinMaxFormat(min: Float, max: Float) extends SeqMinMaxFormat[Float](min, max)
  case class DoubleSeqMinMaxFormat(min: Double, max: Double) extends SeqMinMaxFormat[Double](min, max)


  object IntFormat extends BaseFormat[Int](_ => true)

  object StringFormat extends BaseFormat[String](_ => true)

  object DoubleFormat extends BaseFormat[Double](_ => true)

  object FloatFormat extends BaseFormat[Float](_ => true)

  object BooleanFormat extends BaseFormat[Boolean](_ => true)

  object IntSeqFormat extends BaseFormat[Seq[Int]](_ => true)

  object StringSeqFormat extends BaseFormat[Seq[String]](_ => true)

  /**
   * Format accepting elements that is validated by any of the formats
   * (uses the first one that works)
   */
  case class EitherOfFormat(formats: Seq[Format[_]]) extends BaseFormat[Any](x => formats.exists(format => format.isValid(x))) {

    override def cast(value: JsValue): Any = castIfMatchingFormatExistsElseThrowException(formats, value)

  }

  object DoubleSeqFormat extends BaseFormat[Seq[Double]](_ => true)

  object FloatSeqFormat extends BaseFormat[Seq[Float]](_ => true)

  object BoolSeqFormat extends BaseFormat[Seq[Boolean]](_ => true)

  case class StringConstantFormat(value: String) extends BaseFormat[String](x => x.equals(value))

  case class FieldType(nameFormat: Format[String], valueFormat: Format[_], required: Boolean)

  /**
   * Suitable if a nested format shall be specified where specific key values are not specified
   * but are subjected to the same format check
   */
  case class MapFormat(keyFormat: Format[String], valueFormat: Format[_]) extends BaseFormat[Map[String,_]](toAnyInput(x => jsObjectFulfillsKeyAndValueFormat(keyFormat, valueFormat, x)))

  /**
   * expect JsValue to be JsArray with JsValue elements where each one needs to adhere
   * to the passed format
   * @param perElementFormat - format that needs to hold for each element
   */
  case class GenericSeqFormat(perElementFormat: Format[_]) extends BaseFormat[Seq[_]](_ => true) {

    override def isValid(el: Any): Boolean = {
      el.isInstanceOf[Seq[_]] && el.asInstanceOf[Seq[_]].forall(el => perElementFormat.isValid(el))
    }

    override def cast(value: JsValue): Seq[_] = {
      value match {
        case e: JsArray =>
          val castValueSeq = e.elements.map(x => perElementFormat.cast(x))
          if (castValueSeq.forall(x => isValid(x))) castValueSeq
          else throw new IllegalArgumentException(s"not all elements in cast values '$castValueSeq' are valid for format '$perElementFormat'")
        case _ =>
          throw new IllegalArgumentException(s"value '$value' can not be cast to Sequence of elements valid for format '$perElementFormat'")
      }

    }

  }

  /**
   * NOTE: in the validation and cast this format only checks if any of the provided Formats would
   * be able to cast the value. It does not specifically do this for the current conditionFieldId and
   * respective conditionFieldValue, thus the validation based on the actual value needs to happen within the NestedFormat.
   * @param conditionFieldId - key of the value to be used as conditionalFieldValue
   * @param conditionFieldValuesToFormat - mapping of values belonging to field defined by conditionalFieldId to Format
   */
  case class ConditionalFieldValueChoiceFormat[+T](conditionFieldId: String, conditionFieldValuesToFormat: Map[String, Format[T]]) extends BaseFormat[Any](x => conditionFieldValuesToFormat.values.exists(format => format.isValid(x))) with ConditionalFormat[T] {

    override def cast(value: JsValue): T = castIfMatchingFormatExistsElseThrowException(conditionFieldValuesToFormat.values.toSeq, value).asInstanceOf[T]

    override def isValid(conditionValue: String, conditionedValue: Any): Boolean = {
      conditionFieldValuesToFormat.get(conditionValue).exists(format => format.isValid(conditionedValue))
    }

    override def cast(conditionValue: String, value: JsValue): T = {
      conditionFieldValuesToFormat.get(conditionValue)
        .map(format => format.cast(value))
        .get
    }
  }

  /**
   * Format for nested structure where field names are known and each name/value pair have their
   * own formats (separately for key and value, see FieldType).
   * Takes care of "standaloneFields", that is fields that are not conditioned on other fields and
   * "conditionalFields", whose valid format depends on the value of another field (the field values
   * on which conditionalFields are conditioned are limited to String type for now)
   */
  case class NestedFieldSeqFormat(fields: Seq[FieldType]) extends BaseFormat[Map[String,_]](toAnyInput(matchesValueMap(fields.filter(field => !field.valueFormat.isInstanceOf[ConditionalFormat[_]])))) with NestedFormat[Any] {

    val conditionalFields: Seq[FieldType] = fields.filter(field => field.valueFormat.isInstanceOf[ConditionalFormat[_]])
    val standaloneFields: Seq[FieldType] = fields.filter(field => !field.valueFormat.isInstanceOf[ConditionalFormat[_]])

    def isValidForConditionals(el: Any): Boolean = {
      val allValues = el.asInstanceOf[Map[String, _]]
      conditionalFields.forall(conditionalField => {
        val format: ConditionalFormat[_] = conditionalField.valueFormat.asInstanceOf[ConditionalFormat[_]]
        val currentValueOfConditionalField: Any = allValues.find(x => conditionalField.nameFormat.isValid(x._1)).get._2
        allValues.get(format.conditionFieldId).exists(conditionedFieldValue => format.isValid(conditionedFieldValue.asInstanceOf[String], currentValueOfConditionalField))
      })
    }

    override def isValid(el: Any): Boolean = {
      val eachStandaloneFieldIsValid = super.isValid(el)
      val eachConditionalFieldIsValid = isValidForConditionals(el)
      eachStandaloneFieldIsValid && eachConditionalFieldIsValid
    }
  }

}
