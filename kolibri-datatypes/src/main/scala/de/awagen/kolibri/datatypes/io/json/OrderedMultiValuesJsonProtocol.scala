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

import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol._
import de.awagen.kolibri.datatypes.multivalues
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, GridOrderedMultiValuesBatch, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.OrderedValues
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, RootJsonFormat}


object OrderedMultiValuesJsonProtocol extends DefaultJsonProtocol {

  val GRID_FROM_VALUES_SEQ_TYPE = "GRID_FROM_VALUES_SEQ_TYPE"
  val GRID_BATCH_FROM_VALUES_SEQ_TYPE = "GRID_BATCH_FROM_VALUES_SEQ_TYPE"
  val TYPE_KEY = "type"
  val VALUES_KEY = "values"
  val MULTI_VALUES_KEY = "multiValues"
  val BATCH_SIZE_KEY = "batchSize"
  val BATCH_NR_KEY = "batchNr"

  implicit object OrderedMultiValuesAnyFormat extends JsonFormat[OrderedMultiValues] {
    override def read(json: JsValue): OrderedMultiValues = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case GRID_FROM_VALUES_SEQ_TYPE =>
          val values: Seq[OrderedValues[_]] = fields(VALUES_KEY).convertTo[Seq[OrderedValues[_]]]
          multivalues.GridOrderedMultiValues(values)
        case GRID_BATCH_FROM_VALUES_SEQ_TYPE =>
          multivalues.GridOrderedMultiValuesBatch(multivalues.GridOrderedMultiValues(fields(MULTI_VALUES_KEY).asJsObject.getFields(VALUES_KEY).head.convertTo[Seq[OrderedValues[_]]]),
            fields(BATCH_SIZE_KEY).convertTo[Int], fields(BATCH_NR_KEY).convertTo[Int])
        case e =>  throw DeserializationException(s"Expected a valid type for OrderedMultiValues but got $e")
      }
      case e =>  throw DeserializationException(s"Expected a value for OrderedMultiValues but got $e")
    }

    override def write(obj: OrderedMultiValues): JsValue = obj match {
      case e: GridOrderedMultiValues => gridOrderedMultiValuesFormat.write(e)
      case e: GridOrderedMultiValuesBatch => gridOrderedMultiValuesBatchFormat.write(e)
    }
  }


  implicit def gridOrderedMultiValuesFormat: RootJsonFormat[GridOrderedMultiValues] =
    jsonFormat(GridOrderedMultiValues, VALUES_KEY)

  implicit def gridOrderedMultiValuesBatchFormat: RootJsonFormat[GridOrderedMultiValuesBatch] =
    jsonFormat(GridOrderedMultiValuesBatch, MULTI_VALUES_KEY, BATCH_SIZE_KEY, BATCH_NR_KEY)

}
