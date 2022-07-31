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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.directives.Resource
import de.awagen.kolibri.base.directives.ResourceDirectives.{ResourceDirective, getDirective}
import de.awagen.kolibri.base.io.json.ResourceJsonProtocol.StructDefs.{RESOURCE_MAP_STRING_DOUBLE_STRUCT_DEF, RESOURCE_MAP_STRING_STRING_VALUES_STRUCT_DEF, RESOURCE_STRING_VALUES_STRUCT_DEF}
import de.awagen.kolibri.base.io.json.ResourceJsonProtocol.{resourceMapStringDoubleFormat, resourceMapStringStringValuesFormat, resourceStringValuesFormat}
import de.awagen.kolibri.base.io.json.SupplierJsonProtocol.{GeneratorStringFormat, MapStringDoubleFormat, MapStringToGeneratorStringFormat}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.{JsValue, JsonFormat, enrichAny}

object ResourceDirectiveJsonProtocol {

  val TYPE_KEY = "type"
  val RESOURCE_KEY = "resource"
  val SUPPLIER_KEY = "supplier"
  val VALUES_KEY = "values"
  val FILE_KEY = "file"
  val FOLDER_KEY = "folder"
  val FILE_SUFFIX_KEY = "file_suffix"
  val COLUMN_DELIMITER_KEY = "column_delimiter"
  val KEY_COLUMN_INDEX_KEY = "keyColumnIndex"
  val VALUE_COLUMN_INDEX_KEY = "valueColumnIndex"
  val KEY_TO_VALUE_FILE_MAP_KEY = "keyToValueFileMap"

  implicit object GenericResourceDirectiveFormat extends JsonFormat[ResourceDirective[_]] with WithStructDef {
    val GENERATOR_STRING_TYPE = "GENERATOR_STRING"
    val MAP_STRING_DOUBLE_TYPE = "MAP_STRING_DOUBLE"
    val MAP_STRING_GENERATOR_STRING_TYPE = "MAP_STRING_GENERATOR_STRING"

    override def read(json: JsValue): ResourceDirective[_] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case GENERATOR_STRING_TYPE => GeneratorStringResourceDirectiveFormat.read(fields(VALUES_KEY))
        case MAP_STRING_DOUBLE_TYPE => MapStringDoubleResourceDirectiveFormat.read(fields(VALUES_KEY))
        case MAP_STRING_GENERATOR_STRING_TYPE => MapStringGeneratorStringResourceDirectiveFormat.read(fields(VALUES_KEY))
      }
    }

    override def write(obj: ResourceDirective[_]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(Seq(
            GENERATOR_STRING_TYPE,
            MAP_STRING_DOUBLE_TYPE,
            MAP_STRING_GENERATOR_STRING_TYPE
          )),
          required = true)
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          GENERATOR_STRING_TYPE -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), GeneratorStringResourceDirectiveFormat.structDef, required = true)),
          MAP_STRING_DOUBLE_TYPE -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), MapStringDoubleResourceDirectiveFormat.structDef, required = true)),
          MAP_STRING_GENERATOR_STRING_TYPE -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), MapStringGeneratorStringResourceDirectiveFormat.structDef, required = true))
        ))
      )
    )
  }

  implicit object MapStringDoubleResourceDirectiveFormat extends JsonFormat[ResourceDirective[Map[String, Double]]] with WithStructDef {
    override def read(json: JsValue): ResourceDirective[Map[String, Double]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[Map[String, Double]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[Map[String, Double]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[Map[String, Double]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_MAP_STRING_DOUBLE_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          SupplierJsonProtocol.MapStringDoubleFormat.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  implicit object MapStringGeneratorStringResourceDirectiveFormat extends JsonFormat[ResourceDirective[Map[String, IndexedGenerator[String]]]] with WithStructDef {

    override def read(json: JsValue): ResourceDirective[Map[String, IndexedGenerator[String]]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[Map[String, IndexedGenerator[String]]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[Map[String, IndexedGenerator[String]]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[Map[String, IndexedGenerator[String]]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_MAP_STRING_STRING_VALUES_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          SupplierJsonProtocol.MapStringToGeneratorStringFormat.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  implicit object GeneratorStringResourceDirectiveFormat extends JsonFormat[ResourceDirective[IndexedGenerator[String]]] with WithStructDef {

    override def read(json: JsValue): ResourceDirective[IndexedGenerator[String]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[IndexedGenerator[String]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[IndexedGenerator[String]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[IndexedGenerator[String]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_STRING_VALUES_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          SupplierJsonProtocol.GeneratorStringFormat.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

}
