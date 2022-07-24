/**
  * Copyright 2021 Andreas Wagenmann
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

import de.awagen.kolibri.datatypes.io.json.JsonUtils.enrichJsonWithType
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import spray.json.{DefaultJsonProtocol, JsArray, JsNumber, JsString, JsValue, JsonFormat, RootJsonFormat}

object OrderedValuesJsonProtocol extends DefaultJsonProtocol {

  val NAME_KEY = "name"
  val VALUES_KEY = "values"
  val START_KEY = "start"
  val END_KEY = "end"
  val STEP_SIZE_KEY = "stepSize"
  val TYPE_KEY = "type"
  val DISTINCT_VALUES_TYPE = "DISTINCT_VALUES"
  val RANGE_VALUES_TYPE = "RANGE_VALUES"

  /**
    * Format for OrderedValues. Note that currently takes only numerical (Double/Float) and
    * String into account. If doesnt match any of those types, String used.
    */
  // TODO: update the example json definitions, since I changed this format to make use of type parameter
  implicit object OrderedValuesAnyFormat extends JsonFormat[OrderedValues[Any]] with WithStructDef {
    override def read(json: JsValue): OrderedValues[Any] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case DISTINCT_VALUES_TYPE => fields(VALUES_KEY).convertTo[JsArray].elements.head match {
          case JsNumber(n) if n.isInstanceOf[Double] => DistinctValues(fields(NAME_KEY).convertTo[String], fields(VALUES_KEY).convertTo[Seq[Double]])
          case JsNumber(n) if n.isInstanceOf[Float] => DistinctValues(fields(NAME_KEY).convertTo[String], fields(VALUES_KEY).convertTo[Seq[Float]])
          case JsNumber(n) if n.isInstanceOf[BigDecimal] => DistinctValues(fields(NAME_KEY).convertTo[String], fields(VALUES_KEY).convertTo[Seq[Double]])
          case _: JsString => DistinctValues(fields(NAME_KEY).convertTo[String], fields(VALUES_KEY).convertTo[Seq[String]])
          case _ => DistinctValues(fields(NAME_KEY).convertTo[String], fields(VALUES_KEY).convertTo[Seq[String]])
        }
        case RANGE_VALUES_TYPE =>
          RangeValues(fields(NAME_KEY).convertTo[String], fields(START_KEY).convertTo[Double], fields(END_KEY).convertTo[Double],
            fields(STEP_SIZE_KEY).convertTo[Double])
      }
    }

    override def write(obj: OrderedValues[Any]): JsValue = obj match {
      case e: DistinctValues[_] =>
        val valueWithoutType = e.seq.head match {
          case _: String => distinctValuesFormat[String].write(e.asInstanceOf[DistinctValues[String]])
          case _: Double => distinctValuesFormat[Double].write(e.asInstanceOf[DistinctValues[Double]])
          case _: Float => distinctValuesFormat[Float].write(e.asInstanceOf[DistinctValues[Float]])
        }
        enrichJsonWithType(DISTINCT_VALUES_TYPE, valueWithoutType.asJsObject)
      case e: RangeValues[_] =>
        val valueWithoutType = rangeValuesFormat[Double].write(e.asInstanceOf[RangeValues[Double]])
        enrichJsonWithType(RANGE_VALUES_TYPE, valueWithoutType.asJsObject)
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            RegexStructDef(".+".r),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(DISTINCT_VALUES_TYPE, RANGE_VALUES_TYPE)),
            required = true
          )
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              DISTINCT_VALUES_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(VALUES_KEY),
                  DoubleSeqStructDef,
                  required = true
                )
              ),
              RANGE_VALUES_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(START_KEY),
                  DoubleStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(END_KEY),
                  DoubleStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(STEP_SIZE_KEY),
                  DoubleStructDef,
                  required = true
                )
              )
            )
          )
        )
      )
    }
  }

  implicit def distinctValuesFormat[A: JsonFormat]: RootJsonFormat[DistinctValues[A]] =
    jsonFormat(DistinctValues[A], NAME_KEY, VALUES_KEY)

  implicit def rangeValuesFormat[A: JsonFormat](implicit v: Fractional[A]): RootJsonFormat[RangeValues[A]] =
    jsonFormat(RangeValues[A], NAME_KEY, START_KEY, END_KEY, STEP_SIZE_KEY)
}
