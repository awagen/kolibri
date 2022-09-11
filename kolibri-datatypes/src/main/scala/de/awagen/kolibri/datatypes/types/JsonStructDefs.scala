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
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.StructDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.Validations.FunctionConversions._
import de.awagen.kolibri.datatypes.types.JsonStructDefs.Validations.{canBeCastAndIsValidFormat, jsObjectFulfillsKeyAndValueFormat, logger, matchesOneOfChoices, matchesRegex, seqMatchesOneOfChoices, seqMatchesRegex, seqWithinMinMax, withinMinMax}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, IntJsonFormat, StringJsonFormat, immSeqFormat, mapFormat}
import spray.json.{JsArray, JsObject, JsValue, JsonReader}

import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.matching.Regex


object FieldDefinitions {
  /**
   * Trait for definition of fields. Only value format mandatory since this covers single fields that come with
   * nameFormat and multi-fields that contain multiple single field definitions that need to occur in a nested
   * structure (e.g JsObject)
   */
  case class FieldDef(nameFormat: StructDef[String],
                      valueFormat: StructDef[_],
                      required: Boolean,
                      description: String = "")
}


object JsonStructDefs {

  object Validations {

    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    trait ValidationResult

    case class ValidationFail(fieldName: String, reason: String) extends ValidationResult

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

    def getJsObjKeyForFormat(field: FieldDef, jsObject: JsObject): Option[String] = {
      jsObject.fields.keys.find(x => field.nameFormat.castValueIsValid(x))
    }

    def getMapKeyForFormat(field: FieldDef, map: Map[String, _]): Option[String] = {
      map.keys.find(x => field.nameFormat.castValueIsValid(x))
    }

    def canBeCastAndIsValidFormat(format: StructDef[_], element: JsValue): Boolean = {
      try {
        val castValue = format.cast(element)
        format.castValueIsValid(castValue)
      }
      catch {
        case _: Exception =>
          false
      }
    }

    def jsValueCanBeCast(jsValue: JsValue, format: StructDef[_]): Boolean = {
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

    def mapContainsKeyWithValidValue(field: FieldDef, map: Map[String, _]): Boolean = {
      val found = getMapKeyForFormat(field, map)
      found.exists(foundField => field.valueFormat.castValueIsValid(map(foundField)))
    }

    def matchesValueMap(fields: Seq[FieldDef]): Map[String, _] => Boolean = map => {
      fields.forall(field => {
        val matchingKey: Option[String] = getMapKeyForFormat(field, map)
        try {
          if (!field.required) {
            if (matchingKey.nonEmpty) {
              field.valueFormat.castValueIsValid(map(matchingKey.get))
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

    def jsObjectFulfillsKeyAndValueFormat(keyFormat: StructDef[String], valueFormat: StructDef[_], jsObj: JsObject): Boolean = {
      val failedKeyValidations: Iterable[String] = jsObj.fields.keys.filter(key => !keyFormat.castValueIsValid(key))
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

  def castIfMatchingFormatExistsElseThrowException(formats: Seq[StructDef[_]], value: JsValue): Any = {
    val format: Option[StructDef[_]] = formats.find(x => canBeCastAndIsValidFormat(x, value))
    val castValueOpt: Option[Any] = format.map(x => x.cast(value))
    if (castValueOpt.isEmpty) throw new IllegalArgumentException(s"value '$value' can not be validated by any available format $formats")
    castValueOpt.get
  }

  /**
   * Basic Format casting to specified type and checking validity of the value.
   * Note that cast is intended to happen before and isValid is then executed on the cast value
   * to check validity
   */
  trait StructDef[+T] {

    def castValueIsValid(el: Any): Boolean

    def cast(value: JsValue): T

  }

  /**
   * Format for type T. Provides cast function to cast JsValue to specific type
   *
   * @param ev - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  abstract class BaseStructDef[+T](val isValidFunc: Any => Boolean = _ => true)(implicit ev: JsonReader[T]) extends StructDef[T] {

    def castValueIsValid(el: Any): Boolean = isValidFunc.apply(el)

    def cast(value: JsValue): T = {
      value.convertTo[T] match {
        case e if !castValueIsValid(e) =>
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
  trait NestedStructDef[+T] extends StructDef[Map[String, T]] {

    def fields: Seq[FieldDef]

  }

  /**
   * Format
   *
   * @param regex
   */
  case class RegexStructDef(regex: Regex) extends BaseStructDef[String](toAnyInput(matchesRegex(regex)))

  case class SeqRegexStructDef(regex: Regex) extends BaseStructDef[Seq[String]](toAnyInput(seqMatchesRegex(regex)))

  /**
   * Format only allowing values within predefined choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class ChoiceStructDef[T](choices: Seq[T])(implicit ev: JsonReader[T]) extends BaseStructDef[T](matchesOneOfChoices(choices))

  case class IntChoiceStructDef(choices: Seq[Int]) extends ChoiceStructDef[Int](choices)

  case class StringChoiceStructDef(choices: Seq[String]) extends ChoiceStructDef[String](choices)

  /**
   * format for sequence of values of type T, where each element must be contained within the passed choices
   *
   * @param choices - allowed values
   * @param ev      - implicit json reader for specified type T
   * @tparam T - type to cast to
   */
  class SeqChoiceStructDef[T](choices: Seq[T])(implicit ev: JsonReader[Seq[T]]) extends BaseStructDef[Seq[T]](toAnyInput(seqMatchesOneOfChoices(choices)))

  case class IntSeqChoiceStructDef(choices: Seq[Int]) extends SeqChoiceStructDef[Int](choices)

  case class StringSeqChoiceStructDef(choices: Seq[String]) extends SeqChoiceStructDef[String](choices)

  /**
   * Format requiring value (must be numeric) to be within range defined by min and max value
   *
   * @param min - minimum allowed value
   * @param max - maximum allowed value
   * @param ev  - json reader for specified type T
   * @param evR - implicit json reader for type T
   * @tparam T - type to cast to
   */
  class MinMaxStructDef[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[T]) extends BaseStructDef[T](toAnyInput(withinMinMax(min, max)))

  case class IntMinMaxStructDef(min: Int, max: Int) extends MinMaxStructDef[Int](min, max)

  case class FloatMinMaxStructDef(min: Float, max: Float) extends MinMaxStructDef[Float](min, max)

  case class DoubleMinMaxStructDef(min: Double, max: Double) extends MinMaxStructDef[Double](min, max)

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
  class SeqMinMaxStructDef[T](min: T, max: T)(implicit ev: Numeric[T], evR: JsonReader[Seq[T]]) extends BaseStructDef[Seq[T]](toAnyInput(seqWithinMinMax(min, max)))

  case class IntSeqMinMaxStructDef(min: Int, max: Int) extends SeqMinMaxStructDef[Int](min, max)

  case class FloatSeqMinMaxStructDef(min: Float, max: Float) extends SeqMinMaxStructDef[Float](min, max)

  case class DoubleSeqMinMaxStructDef(min: Double, max: Double) extends SeqMinMaxStructDef[Double](min, max)


  object IntStructDef extends BaseStructDef[Int](_ => true)

  object StringStructDef extends BaseStructDef[String](_ => true)

  object DoubleStructDef extends BaseStructDef[Double](_ => true)

  object FloatStructDef extends BaseStructDef[Float](_ => true)

  object BooleanStructDef extends BaseStructDef[Boolean](_ => true)

  object IntSeqStructDef extends BaseStructDef[Seq[Int]](_ => true)

  object StringSeqStructDef extends BaseStructDef[Seq[String]](_ => true)

  /**
   * Format accepting elements that is validated by any of the formats
   * (uses the first one that works)
   */
  case class EitherOfStructDef(formats: Seq[StructDef[_]]) extends BaseStructDef[Any](x => formats.exists(format => format.castValueIsValid(x))) {

    override def cast(value: JsValue): Any = castIfMatchingFormatExistsElseThrowException(formats, value)

  }

  object DoubleSeqStructDef extends BaseStructDef[Seq[Double]](_ => true)

  object FloatSeqStructDef extends BaseStructDef[Seq[Float]](_ => true)

  object BoolSeqStructDef extends BaseStructDef[Seq[Boolean]](_ => true)

  case class StringConstantStructDef(value: String) extends BaseStructDef[String](x => x.equals(value))

  /**
   * Suitable if a nested format shall be specified where specific key values are not specified
   * but are subjected to the same format check
   */
  case class MapStructDef(keyFormat: StructDef[String], valueFormat: StructDef[_]) extends BaseStructDef[Map[String, _]](toAnyInput(x => jsObjectFulfillsKeyAndValueFormat(keyFormat, valueFormat, x)))

  /**
   * expect JsValue to be JsArray with JsValue elements where each one needs to adhere
   * to the passed format
   *
   * @param perElementFormat - format that needs to hold for each element
   */
  case class GenericSeqStructDef(perElementFormat: StructDef[_]) extends BaseStructDef[Seq[_]](_ => true) {

    override def castValueIsValid(el: Any): Boolean = {
      el.isInstanceOf[Seq[_]] && el.asInstanceOf[Seq[_]].forall(el => perElementFormat.castValueIsValid(el))
    }

    override def cast(value: JsValue): Seq[_] = {
      value match {
        case e: JsArray =>
          val castValueSeq = e.elements.map(x => perElementFormat.cast(x))
          if (castValueSeq.forall(x => castValueIsValid(x))) castValueSeq
          else throw new IllegalArgumentException(s"not all elements in cast values '$castValueSeq' are valid for format '$perElementFormat'")
        case _ =>
          throw new IllegalArgumentException(s"value '$value' can not be cast to Sequence of elements valid for format '$perElementFormat'")
      }

    }

  }

  case class ConditionalFields(conditionFieldId: String, mapping: Map[String, Seq[FieldDef]]) {

    def fieldsForConditionValue(conditionFieldValue: String): Seq[FieldDef] = {
      mapping(conditionFieldValue)
    }

  }

  /**
   * Format for nested structure where field names are known and each name/value pair have their
   * own formats (separately for key and value, see FieldType).
   * Takes care of "standaloneFields", that is fields that are not conditioned on other fields and
   * "conditionalFields", whose valid format depends on the value of another field (the field values
   * on which conditionalFields are conditioned are limited to String type for now)
   */
  case class NestedFieldSeqStructDef(fields: Seq[FieldDef], conditionalFieldsSeq: Seq[ConditionalFields]) extends NestedStructDef[Any] {

    private[types] def retrieveConditionalFields(conditionValues: Map[String, _]): Either[String, Seq[FieldDef]] = {
      val conditionalFieldOptSeq = conditionalFieldsSeq.map(conditionalFields => {
        conditionValues
          .get(conditionalFields.conditionFieldId)
          .map(key => conditionalFields.fieldsForConditionValue(key.asInstanceOf[String]))
      })
      val missingFields: Seq[ConditionalFields] = conditionalFieldOptSeq.indices.filter(index => conditionalFieldOptSeq(index).isEmpty).map(index => conditionalFieldsSeq(index))
      if (missingFields.nonEmpty) {
        val errorMsg = (Seq(s"current values: $conditionValues") ++
          missingFields.map(x => s"current values do not match conditions - conditionField: ${x.conditionFieldId}, conditionValues: ${x.mapping.keys.mkString(" / ")}"))
          .mkString("\n")
        Left(errorMsg)
      }
      else {
        val availableFieldDefs: Set[FieldDef] = conditionalFieldOptSeq.filter(x => x.nonEmpty).flatMap(x => x.get).toSet
        Right(availableFieldDefs.toSeq)
      }
    }

    private[types] def extractValuesForConditionFields(conditionFields: Seq[String], values: Map[String, JsValue]): Map[String, String] = {
      conditionFields
        .map(x => (x, values.get(x).map(y => y.convertTo[String])))
        .filter(x => x._2.nonEmpty)
        .map(x => (x._1, x._2.get))
        .toMap
    }

    /**
     * executing validation on all relevant fields (the normal fields and conditionals)
     * @param el
     * @return
     */
    override def castValueIsValid(el: Any): Boolean = {
      val allValues = el.asInstanceOf[Map[String, _]]
      // determine fields belonging to current conditional value states and add to fields to be checked
      val conditionalValues: Either[String, Seq[FieldDef]] = retrieveConditionalFields(allValues)
      if (conditionalValues.isLeft) {
        logger.warn(conditionalValues.swap.getOrElse(""))
        false
      }
      else {
        // check all fields for occurrence
        (conditionalValues.getOrElse(Seq.empty) ++ fields).forall(fieldDef => {
          allValues.find(x => fieldDef.nameFormat.castValueIsValid(x._1))
            .exists(field => fieldDef.valueFormat.castValueIsValid(field._2))
        })
      }
    }

    /**
     * retrieve normal and conditional fields (depending on their condition values) and extract their values to Map
     * @param value
     * @return
     */
    override def cast(value: JsValue): Map[String, Any] = {
      val allFields: Map[String, JsValue] = value.asJsObject.fields
      val allConditionValues = extractValuesForConditionFields(conditionalFieldsSeq.map(x => x.conditionFieldId), allFields)
      val conditionalFieldsOrError: Either[String, Seq[FieldDef]] = retrieveConditionalFields(allConditionValues)
      if (conditionalFieldsOrError.isLeft) {
        val errorMsg = conditionalFieldsOrError.swap.getOrElse("")
        throw new IllegalArgumentException(errorMsg)
      }
      val conditionalFields = conditionalFieldsOrError.getOrElse(Seq.empty)

      // retrieve unconditional single fields
      (conditionalFields ++ fields).map(standaloneField => {
        allFields.find(x => standaloneField.nameFormat.castValueIsValid(x._1))
          .map(x => (x._1, standaloneField.valueFormat.cast(x._2)))
          .get
      }).toMap
    }
  }

}
