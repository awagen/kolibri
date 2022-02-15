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

import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import spray.json.{DefaultJsonProtocol, JsArray, JsNumber, JsString, JsValue, JsonFormat, RootJsonFormat}

object OrderedValuesJsonProtocol extends DefaultJsonProtocol {

  /**
   * Format for OrderedValues. Note that currently takes only numerical (Double/Float) and
   * String into account. If doesnt match any of those types, String used.
   */
  implicit object OrderedValuesAnyFormat extends JsonFormat[OrderedValues[Any]] {
    override def read(json: JsValue): OrderedValues[Any] = json match {
      case spray.json.JsObject(fields) if fields.contains("name") && fields.contains("values")
      => fields("values").convertTo[JsArray].elements.head match {
        case JsNumber(n) if n.isInstanceOf[Double] => DistinctValues(fields("name").convertTo[String], fields("values").convertTo[Seq[Double]])
        case JsNumber(n) if n.isInstanceOf[Float] => DistinctValues(fields("name").convertTo[String], fields("values").convertTo[Seq[Float]])
        case JsNumber(n) if n.isInstanceOf[BigDecimal] => DistinctValues(fields("name").convertTo[String], fields("values").convertTo[Seq[Double]])
        case _: JsString => DistinctValues(fields("name").convertTo[String], fields("values").convertTo[Seq[String]])
        case _ => DistinctValues(fields("name").convertTo[String], fields("values").convertTo[Seq[String]])
      }
      case spray.json.JsObject(fields) if fields.contains("name") && fields.contains("start") &&
        fields.contains("end") && fields.contains("stepSize") =>
        RangeValues(fields("name").convertTo[String], fields("start").convertTo[Double], fields("end").convertTo[Double],
          fields("stepSize").convertTo[Double])
    }

    override def write(obj: OrderedValues[Any]): JsValue = obj match {
      case e: DistinctValues[_] =>
        e.seq.head match {
          case _: String => distinctValuesFormat[String].write(e.asInstanceOf[DistinctValues[String]])
          case _: Double => distinctValuesFormat[Double].write(e.asInstanceOf[DistinctValues[Double]])
          case _: Float => distinctValuesFormat[Float].write(e.asInstanceOf[DistinctValues[Float]])
        }
      case e: RangeValues[_] =>
        rangeValuesFormat[Double].write(e.asInstanceOf[RangeValues[Double]])
    }
  }

  implicit def distinctValuesFormat[A: JsonFormat]: RootJsonFormat[DistinctValues[A]] =
    jsonFormat(DistinctValues[A], "name", "values")

  implicit def rangeValuesFormat[A: JsonFormat](implicit v: Fractional[A]): RootJsonFormat[RangeValues[A]] =
    jsonFormat(RangeValues[A], "name", "start", "end", "stepSize")
}
