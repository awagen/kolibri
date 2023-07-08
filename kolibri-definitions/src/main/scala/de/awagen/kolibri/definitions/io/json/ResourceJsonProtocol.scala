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

import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, StringConstantStructDef, StringStructDef, StructDef}
import de.awagen.kolibri.definitions.directives.{Resource, ResourceType}
import de.awagen.kolibri.definitions.io.json.EnumerationJsonProtocol.{resourceTypeGeneratorStringFormat, resourceTypeJudgementProviderFormat, resourceTypeMapStringDoubleFormat, resourceTypeMapStringGeneratorStringFormat}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import spray.json.DefaultJsonProtocol.{StringJsonFormat, jsonFormat2}
import spray.json._

object ResourceJsonProtocol {

  implicit val resourceStringValuesFormat: RootJsonFormat[Resource[IndexedGenerator[String]]] = jsonFormat2(Resource[IndexedGenerator[String]])
  implicit val resourceMapStringDoubleFormat: RootJsonFormat[Resource[Map[String, Double]]] = jsonFormat2(Resource[Map[String, Double]])
  implicit val resourceJudgementProviderFormat: RootJsonFormat[Resource[JudgementProvider[Double]]] = jsonFormat2(Resource[JudgementProvider[Double]])
  implicit val resourceMapStringStringValuesFormat: RootJsonFormat[Resource[Map[String, IndexedGenerator[String]]]] = jsonFormat2(Resource[Map[String, IndexedGenerator[String]]])

  implicit object AnyResourceFormat extends JsonFormat[Resource[Any]] {

    val RESOURCE_TYPE_KEY = "resourceType"
    val IDENTIFIER_KEY = "identifier"

    override def read(json: JsValue): Resource[Any] = json match {
      case spray.json.JsObject(fields) if fields.contains(RESOURCE_TYPE_KEY) => fields(RESOURCE_TYPE_KEY).convertTo[String] match {
        case "STRING_VALUES" =>
          Resource(ResourceType.STRING_VALUES, fields(IDENTIFIER_KEY).convertTo[String])
        case "MAP_STRING_TO_DOUBLE_VALUE" =>
          Resource(ResourceType.MAP_STRING_TO_DOUBLE_VALUE, fields(IDENTIFIER_KEY).convertTo[String])
        case "MAP_STRING_TO_STRING_VALUES" =>
          Resource(ResourceType.MAP_STRING_TO_STRING_VALUES, fields(IDENTIFIER_KEY).convertTo[String])
        case "JUDGEMENT_PROVIDER" =>
          Resource(ResourceType.JUDGEMENT_PROVIDER, fields(IDENTIFIER_KEY).convertTo[String])
      }
    }

    override def write(obj: Resource[Any]): JsValue = JsObject(Map(
      RESOURCE_TYPE_KEY -> JsString(obj.resourceType.toString().toUpperCase),
      IDENTIFIER_KEY -> JsString(obj.identifier)
    ))

  }

  object StructDefs {

    val RESOURCE_TYPE_KEY = "resourceType"
    val IDENTIFIER_TYPE_KEY = "identifier"

    val RESOURCE_STRING_VALUES_STRUCT_DEF: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_TYPE_KEY),
          resourceTypeGeneratorStringFormat.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(IDENTIFIER_TYPE_KEY),
          StringStructDef,
          required = true
        )
      ),
      Seq.empty
    )

    val RESOURCE_MAP_STRING_DOUBLE_STRUCT_DEF: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_TYPE_KEY),
          resourceTypeMapStringDoubleFormat.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(IDENTIFIER_TYPE_KEY),
          StringStructDef,
          required = true
        )
      ),
      Seq.empty
    )

    val RESOURCE_JUDGEMENT_PROVIDER_STRUCT_DEF: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_TYPE_KEY),
          resourceTypeJudgementProviderFormat.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(IDENTIFIER_TYPE_KEY),
          StringStructDef,
          required = true
        )
      ),
      Seq.empty
    )

    val RESOURCE_MAP_STRING_STRING_VALUES_STRUCT_DEF: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(RESOURCE_TYPE_KEY),
          resourceTypeMapStringGeneratorStringFormat.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(IDENTIFIER_TYPE_KEY),
          StringStructDef,
          required = true
        )
      ),
      Seq.empty
    )


  }

}