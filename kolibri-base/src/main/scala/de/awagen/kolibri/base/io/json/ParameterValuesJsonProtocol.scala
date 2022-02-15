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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.io.json.EnumerationJsonProtocol._
import de.awagen.kolibri.base.io.json.OrderedValuesJsonProtocol._
import de.awagen.kolibri.base.processing.modifiers.ParameterValues._
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.values.OrderedValues
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object ParameterValuesJsonProtocol extends DefaultJsonProtocol {

  val TYPE_KEY = "type"
  val VALUES_TYPE_KEY = "values_type"
  val NAME_KEY = "name"
  val VALUES_KEY = "values"
  val FROM_MULTIVALUES_TYPE = "FROM_MULTIVALUES"
  val PARAMETER_VALUES_TYPE_KEY = "PARAMETER_VALUES_TYPE"
  val MAPPED_VALUES_TYPE_KEY = "MAPPED_VALUES_TYPE"

  implicit object ParameterValuesFormat extends JsonFormat[ParameterValues] {
    override def read(json: JsValue): ParameterValues = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_MULTIVALUES_TYPE =>
          val values = fields(VALUES_KEY).convertTo[OrderedValues[String]]
          val parameterValuesType = fields(PARAMETER_VALUES_TYPE_KEY).convertTo[ValueType.Value]
          ParameterValues(
            values.name,
            parameterValuesType,
            ByFunctionNrLimitedIndexedGenerator.createFromSeq(values.getAll)
          )
      }
    }

    override def write(obj: ParameterValues): JsValue = """{}""".toJson
  }

  implicit object MappedParameterValuesFormat extends JsonFormat[MappedParameterValues] {
    override def read(json: JsValue): MappedParameterValues = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case "" =>
          val mappings = fields(VALUES_KEY).convertTo[Map[String, Seq[String]]]
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          MappedParameterValues(name, valueType, mappings.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2))))
      }
    }

    override def write(obj: MappedParameterValues): JsValue = """{}""".toJson
  }

  implicit object ParameterValueMappingFormat extends JsonFormat[ParameterValueMapping] {
    override def read(json: JsValue): ParameterValueMapping = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case "" =>
          null
      }
    }

    override def write(obj: ParameterValueMapping): JsValue = """{}""".toJson
  }

}
