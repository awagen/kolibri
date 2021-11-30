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

package de.awagen.kolibri.datatypes.io

import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, GridOrderedMultiValuesBatch, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import play.api.libs.json.{JsValue, Json, Writes}

/**
  * Trait providing implicit Writes to transform objects relevant to experiments into Json
  */
trait ValueWrites {

  /**
    * Writes[DistinctStringValues] used to transform DistinctStringValues to Json (e.g Json.toJson(obj))
    */
  implicit val distinctStringValuesWrites: Writes[DistinctValues[String]] = (stringValues: DistinctValues[String]) => Json.obj(
    "name" -> stringValues.name,
    "values" -> stringValues.values
  )

  /**
    * Writes[RangeValues[Float]] used to transform RangeValues[Float] to Json (e.g Json.toJson(obj))
    */
  implicit val rangeValuesFloatWrites: Writes[RangeValues[Float]] = (rangeValues: RangeValues[Float]) => Json.obj(
    "name" -> rangeValues.name,
    "start" -> rangeValues.start,
    "end" -> rangeValues.end,
    "stepSize" -> rangeValues.stepSize
  )

  /**
    * Writes[RangeValues[Double]] used to transform RangeValues[Double] to Json (e.g Json.toJson(obj))
    */
  implicit val rangeValuesDoubleWrites: Writes[RangeValues[Double]] = (rangeValues: RangeValues[Double]) => Json.obj(
    "name" -> rangeValues.name,
    "start" -> rangeValues.start,
    "end" -> rangeValues.end,
    "stepSize" -> rangeValues.stepSize
  )

  /**
    * Currently all range values are casted to RangeValue[Double] and all DistinctValues  to DistinctValues[String].
    * This is not optimal, a type-specific approach needs handling of type erasure,
    * otherwise a RangeValues[T] of any type T will match the first RangeValues case in the match due
    * to type erasure
    */
  implicit object orderedValuesWrites extends Writes[OrderedValues[Any]] {
    def writes(obj: OrderedValues[Any]): JsValue = {
      obj match {
        case o: RangeValues[_] => rangeValuesDoubleWrites.writes(o.asInstanceOf[RangeValues[Double]])
        case o: DistinctValues[_] => distinctStringValuesWrites.writes(o.asInstanceOf[DistinctValues[String]])
        case o => throw new IllegalArgumentException(s"No Writes available for type ${o.getClass}")
      }
    }
  }

  implicit val orderValuesSeqWrites: Writes[Seq[OrderedValues[Any]]] = Writes.seq(orderedValuesWrites)

  /**
    * Writes[GridOrderedMultiValues] used to transform GridOrderedMultiValues to Json
    */
  implicit val gridValuesWrites: Writes[GridOrderedMultiValues] = (gridMultiValues: GridOrderedMultiValues) => Json.obj(
    "params" -> Json.toJson(gridMultiValues.values)
  )

  //TODO: check workings
  implicit val orderedMultiValuesWrites: Writes[OrderedMultiValues] = (_: OrderedMultiValues) => Json.obj()

  /**
    * Writes[GridOrderedMultiValuesBatch] used to transform GridOrderedMultiValuesBatch to Json
    */
  implicit val gridMultiValuesBatchWrites: Writes[GridOrderedMultiValuesBatch] = (gridValuesBatch: GridOrderedMultiValuesBatch) =>
    Json.obj(
      "values" -> gridValuesBatch.multiValues,
      "batchSize" -> gridValuesBatch.batchSize,
      "batchNr" -> gridValuesBatch.batchNr
    )

}
