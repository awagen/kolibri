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


package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.definitions.directives.ResourceDirectives.{ResourceDirective, getDirective}
import de.awagen.kolibri.definitions.directives.ResourceType.{JUDGEMENT_PROVIDER, MAP_STRING_TO_DOUBLE_VALUE, MAP_STRING_TO_STRING_VALUES, STRING_VALUES}
import de.awagen.kolibri.definitions.directives.{Resource, ResourceType}
import de.awagen.kolibri.definitions.io.json.ResourceJsonProtocol.StructDefs.{RESOURCE_JUDGEMENT_PROVIDER_STRUCT_DEF, RESOURCE_MAP_STRING_DOUBLE_STRUCT_DEF, RESOURCE_MAP_STRING_STRING_VALUES_STRUCT_DEF, RESOURCE_STRING_VALUES_STRUCT_DEF}
import de.awagen.kolibri.definitions.io.json.ResourceJsonProtocol.{resourceJudgementProviderFormat, resourceMapStringDoubleFormat, resourceMapStringStringValuesFormat, resourceStringValuesFormat}
import de.awagen.kolibri.definitions.io.json.SupplierJsonProtocol.MapStringToGeneratorStringFormatStruct.MapStringDoubleFormatStruct
import de.awagen.kolibri.definitions.io.json.SupplierJsonProtocol.{GeneratorStringFormatStruct, JudgementProviderFormatStruct, MapStringToGeneratorStringFormatStruct}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
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

  object JudgementProviderResourceDirectiveFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_JUDGEMENT_PROVIDER_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          JudgementProviderFormatStruct.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  object MapStringDoubleResourceDirectiveFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_MAP_STRING_DOUBLE_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          MapStringDoubleFormatStruct.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  object MapStringGeneratorStringResourceDirectiveFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_MAP_STRING_STRING_VALUES_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          MapStringToGeneratorStringFormatStruct.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  object GeneratorStringResourceDirectiveFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_KEY),
          RESOURCE_STRING_VALUES_STRUCT_DEF,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SUPPLIER_KEY),
          GeneratorStringFormatStruct.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  object GenericResourceDirectiveFormatStruct extends WithStructDef {
    override def structDef: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(
            ResourceType.vals.map(x => x.toString())
          ),
          required = true)
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          JUDGEMENT_PROVIDER.toString() -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), JudgementProviderResourceDirectiveFormatStruct.structDef, required = true)),
          STRING_VALUES.toString() -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), GeneratorStringResourceDirectiveFormatStruct.structDef, required = true)),
          MAP_STRING_TO_DOUBLE_VALUE.toString() -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), MapStringDoubleResourceDirectiveFormatStruct.structDef, required = true)),
          MAP_STRING_TO_STRING_VALUES.toString() -> Seq(FieldDef(StringConstantStructDef(VALUES_KEY), MapStringGeneratorStringResourceDirectiveFormatStruct.structDef, required = true))
        ))
      )
    )
  }

}

case class ResourceDirectiveJsonProtocol(supplierJsonProtocol: SupplierJsonProtocol) {
  import ResourceDirectiveJsonProtocol._

  implicit val generatorStringFormat: JsonFormat[SerializableSupplier[IndexedGenerator[String]]] = supplierJsonProtocol.GeneratorStringFormat
  implicit val judgementProviderFormat: JsonFormat[SerializableSupplier[JudgementProvider[Double]]] = supplierJsonProtocol.JudgementProviderFormat
  implicit val mapStringDoubleFormat: JsonFormat[SerializableSupplier[Map[String, Double]]] = supplierJsonProtocol.MapStringDoubleFormat
  implicit val mapStringToGeneratorStringFormat: JsonFormat[SerializableSupplier[Map[String, IndexedGenerator[String]]]] = supplierJsonProtocol.MapStringToGeneratorStringFormat

  implicit object GenericResourceDirectiveFormat extends JsonFormat[ResourceDirective[_]] {

    override def read(json: JsValue): ResourceDirective[_] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case "STRING_VALUES" => GeneratorStringResourceDirectiveFormat.read(fields(VALUES_KEY))
        case "MAP_STRING_TO_DOUBLE_VALUE" => MapStringDoubleResourceDirectiveFormat.read(fields(VALUES_KEY))
        case "MAP_STRING_TO_STRING_VALUES" => MapStringGeneratorStringResourceDirectiveFormat.read(fields(VALUES_KEY))
        case "JUDGEMENT_PROVIDER" => JudgementProviderResourceDirectiveFormat.read(fields(VALUES_KEY))
      }
    }

    override def write(obj: ResourceDirective[_]): JsValue = """{}""".toJson

  }

  implicit object JudgementProviderResourceDirectiveFormat extends JsonFormat[ResourceDirective[JudgementProvider[Double]]] {
    override def read(json: JsValue): ResourceDirective[JudgementProvider[Double]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[JudgementProvider[Double]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[JudgementProvider[Double]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[JudgementProvider[Double]]): JsValue = """{}""".toJson

  }

  implicit object MapStringDoubleResourceDirectiveFormat extends JsonFormat[ResourceDirective[Map[String, Double]]] {
    override def read(json: JsValue): ResourceDirective[Map[String, Double]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[Map[String, Double]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[Map[String, Double]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[Map[String, Double]]): JsValue = """{}""".toJson

  }

  implicit object MapStringGeneratorStringResourceDirectiveFormat extends JsonFormat[ResourceDirective[Map[String, IndexedGenerator[String]]]] {

    override def read(json: JsValue): ResourceDirective[Map[String, IndexedGenerator[String]]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[Map[String, IndexedGenerator[String]]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[Map[String, IndexedGenerator[String]]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[Map[String, IndexedGenerator[String]]]): JsValue = """{}""".toJson

  }

  implicit object GeneratorStringResourceDirectiveFormat extends JsonFormat[ResourceDirective[IndexedGenerator[String]]] {

    override def read(json: JsValue): ResourceDirective[IndexedGenerator[String]] = json match {
      case spray.json.JsObject(fields) =>
        val resource = fields(RESOURCE_KEY).convertTo[Resource[IndexedGenerator[String]]]
        val supplier = fields(SUPPLIER_KEY).convertTo[SerializableSupplier[IndexedGenerator[String]]]
        getDirective(supplier, resource)
    }

    override def write(obj: ResourceDirective[IndexedGenerator[String]]): JsValue = """{}""".toJson

  }

}
