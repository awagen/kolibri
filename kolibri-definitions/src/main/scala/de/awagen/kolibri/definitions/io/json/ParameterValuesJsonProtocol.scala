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


package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.definitions.io.json.EnumerationJsonProtocol.valueTypeFormat
import de.awagen.kolibri.definitions.io.json.SupplierJsonProtocol.{GeneratorStringFormatStruct, MapStringToGeneratorStringFormatStruct}
import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues._
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat, enrichAny}

object ParameterValuesJsonProtocol {

  object FormatOps {

    def valuesFromLinesJson(valuesType: ValueType.Value, paramIdentifier: String, filePath: String): String = {
      s"""
         |{
         |"type": "FROM_ORDERED_VALUES_TYPE",
         |"values_type": "${valuesType.toString}",
         |"values": {
         |  "type": "FROM_FILES_LINES_TYPE",
         |  "valueName": "$paramIdentifier",
         |  "file": "$filePath"
         |}
         |}
         |""".stripMargin
    }

    def valuesFromCsvMapping(valuesType: ValueType.Value, paramIdentifier: String, filePath: String,
                             delimiter: String): String = {
      s"""{
         |"type": "CSV_MAPPING_TYPE",
         |"name": "$paramIdentifier",
         |"values_type": "${valuesType.toString}",
         |"values": "$filePath",
         |"column_delimiter": "$delimiter",
         |"key_column_index": 0,
         |"value_column_index": 1
         |}
         |""".stripMargin
    }

    def valuesFromJsonMapping(valuesType: ValueType.Value, paramIdentifier: String, filePath: String): String = {
      s"""{
         |"type": "JSON_ARRAY_MAPPINGS_TYPE",
         |"name": "$paramIdentifier",
         |"values_type": "${valuesType.toString}",
         |"values": "$filePath"
         |}
         |""".stripMargin
    }
  }


  val VALUES_KEY = "values"
  val TYPE_KEY = "type"
  val VALUES_TYPE_KEY = "values_type"
  val KEY_VALUES_KEY = "key_values"
  val MAPPED_VALUES_KEY = "mapped_values"
  val KEY_MAPPING_ASSIGNMENTS_KEY = "key_mapping_assignments"
  val NAME_KEY = "name"

  val STANDALONE_TYPE = "STANDALONE"
  val MAPPING_TYPE = "MAPPING"

  object ParameterValuesConfigFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            StringStructDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(VALUES_TYPE_KEY),
            valueTypeFormat.structDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(VALUES_KEY),
            GeneratorStringFormatStruct.structDef,
            required = true
          )
        ),
        Seq.empty
      )
    }
  }

  object MappedParameterValuesFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            StringStructDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(VALUES_TYPE_KEY),
            valueTypeFormat.structDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(VALUES_KEY),
            MapStringToGeneratorStringFormatStruct.structDef,
            required = true
          )
        ),
        Seq.empty
      )
    }
  }

  object ParameterValueMappingConfigFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(KEY_VALUES_KEY), ParameterValuesConfigFormatStruct.structDef, required = true),
          FieldDef(
            StringConstantStructDef(MAPPED_VALUES_KEY),
            GenericSeqStructDef(MappedParameterValuesFormatStruct.structDef),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(KEY_MAPPING_ASSIGNMENTS_KEY),
            GenericSeqStructDef(IntSeqStructDef),
            required = true
          )
        ),
        Seq.empty
      )
    }
  }


  object ValueSeqGenDefinitionFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              STANDALONE_TYPE,
              MAPPING_TYPE
            )),
            required = true
          )
        ),
        Seq(
          ConditionalFields(TYPE_KEY, Map(
            STANDALONE_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                ParameterValuesConfigFormatStruct.structDef,
                required = true
              )
            ),
            MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                ParameterValueMappingConfigFormatStruct.structDef,
                required = true)
            )
          ))
        )
      )

    }
  }

}

case class ParameterValuesJsonProtocol(supplierJsonProtocol: SupplierJsonProtocol) extends DefaultJsonProtocol {
  import ParameterValuesJsonProtocol._

  val generatorStringFormat: JsonFormat[SerializableSupplier[IndexedGenerator[String]]] =
    supplierJsonProtocol.GeneratorStringFormat
  val mapStringToGeneratorStringFormat: JsonFormat[SerializableSupplier[Map[String, IndexedGenerator[String]]]] =
    supplierJsonProtocol.MapStringToGeneratorStringFormat

  /**
   * Reusing below formats for the general trait ValuesSeqGenProvider
   */
  implicit object ValueSeqGenDefinitionFormat extends JsonFormat[ValueSeqGenDefinition[_]] {
    override def read(json: JsValue): ValueSeqGenDefinition[_] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case STANDALONE_TYPE => ParameterValuesConfigFormat.read(fields(VALUES_KEY))
        case MAPPING_TYPE => ParameterValueMappingConfigFormat.read(fields(VALUES_KEY))
      }
    }

    override def write(obj: ValueSeqGenDefinition[_]): JsValue = """{}""".toJson

  }

  /**
   * Allows passing of arbitrary combinations of either single value generators
   * (ParameterValues instances) or mappings (ParameterValueMappings instances),
   * creates overall generator of value sequences
   */
  implicit val parameterValuesGenSeqToValueSeqGeneratorFormat: RootJsonFormat[ParameterValuesGenSeqToValueSeqGenerator] = jsonFormat((values: Seq[ValueSeqGenDefinition[_]]) => {
    ParameterValuesGenSeqToValueSeqGenerator.apply(values.map(x => x.toState))
  }, "values")

  /**
   * Format for creation of ParameterValues (Seq of values for single type and name)
   */
  implicit object ParameterValuesConfigFormat extends JsonFormat[ParameterValuesDefinition] {
    override def read(json: JsValue): ParameterValuesDefinition = {
      json match {
        case spray.json.JsObject(fields) =>
          val name = fields(NAME_KEY).convertTo[String]
          val parameterValuesType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val valueSupplier: SerializableSupplier[IndexedGenerator[String]] = generatorStringFormat.read(fields(VALUES_KEY))
          ParameterValuesDefinition(
            name,
            parameterValuesType,
            valueSupplier
          )
      }
    }

    override def write(obj: ParameterValuesDefinition): JsValue = """{}""".toJson

  }

  /**
   * Format for mappings, defined by the values (Seq[String]) that hold for a given
   * key
   */
  implicit object MappedParameterValuesFormat extends JsonFormat[MappedParameterValues] {
    override def read(json: JsValue): MappedParameterValues = json match {
      case spray.json.JsObject(fields) =>
        val name = fields(NAME_KEY).convertTo[String]
        val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
        val values = fields(VALUES_KEY)
        val mappings = mapStringToGeneratorStringFormat.read(values)
        MappedParameterValues(name, valueType, mappings)
    }

    override def write(obj: MappedParameterValues): JsValue = """{}""".toJson

  }

  /**
   * Uses a key generator and one or multiple MappedParameterValues passed as Seq,
   * where the first in the Seq maps to the key generator and all others can either
   * map to the key generator or any mapped value that occurs before itself in the Seq
   *
   * Here a use-case could be having distinct valid parameter values (such as queries) per
   * userId, in which case userId would be used as key generator and queries would be
   * represented by a mapping
   */
  implicit object ParameterValueMappingConfigFormat extends JsonFormat[ParameterValueMappingDefinition] {
    override def read(json: JsValue): ParameterValueMappingDefinition = json match {
      case spray.json.JsObject(fields) => {
        val keyValues = fields(KEY_VALUES_KEY).convertTo[ParameterValuesDefinition]
        val mappedValues = fields(MAPPED_VALUES_KEY).convertTo[Seq[MappedParameterValues]]
        val keyForMappingAssignments = fields(KEY_MAPPING_ASSIGNMENTS_KEY).convertTo[Seq[(Int, Int)]]
        new ParameterValueMappingDefinition(keyValues, mappedValues, keyForMappingAssignments)
      }
    }

    override def write(obj: ParameterValueMappingDefinition): JsValue = """{}""".toJson

  }

}
