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
import de.awagen.kolibri.base.io.json.EnumerationJsonProtocol.{resourceTypeGeneratorStringFormat, resourceTypeJudgementProviderFormat, resourceTypeMapStringDoubleFormat, resourceTypeMapStringGeneratorStringFormat}
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, StringConstantStructDef, StringStructDef, StructDef}
import spray.json.DefaultJsonProtocol.{StringJsonFormat, jsonFormat2}
import spray.json.RootJsonFormat

object ResourceJsonProtocol {

  implicit val resourceStringValuesFormat: RootJsonFormat[Resource[IndexedGenerator[String]]] = jsonFormat2(Resource[IndexedGenerator[String]])
  implicit val resourceMapStringDoubleFormat: RootJsonFormat[Resource[Map[String, Double]]] = jsonFormat2(Resource[Map[String, Double]])
  implicit val resourceJudgementProviderFormat: RootJsonFormat[Resource[JudgementProvider[Double]]] = jsonFormat2(Resource[JudgementProvider[Double]])
  implicit val resourceMapStringStringValuesFormat: RootJsonFormat[Resource[Map[String, IndexedGenerator[String]]]] = jsonFormat2(Resource[Map[String, IndexedGenerator[String]]])

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