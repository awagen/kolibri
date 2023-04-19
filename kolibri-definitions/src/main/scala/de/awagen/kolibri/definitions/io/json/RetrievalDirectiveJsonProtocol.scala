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

import de.awagen.kolibri.definitions.directives.Resource
import de.awagen.kolibri.definitions.directives.ResourceType.ResourceType
import de.awagen.kolibri.definitions.directives.RetrievalDirective.{RetrievalDirective, Retrieve}
import de.awagen.kolibri.definitions.io.json.EnumerationJsonProtocol.resourceTypeMapStringGeneratorStringFormat
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef, StructDef}
import de.awagen.kolibri.datatypes.types.WithStructDef
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.{JsValue, JsonFormat, enrichAny}

object RetrievalDirectiveJsonProtocol {

  val NAME_KEY = "name"
  val RESOURCE_TYPE_KEY = "resourceType"
  val RETRIEVAL_DIRECTIVE_KEY = "retrievalDirective"

  implicit object RetrievalDirectiveMapStringGeneratorString extends JsonFormat[RetrievalDirective[Map[String, IndexedGenerator[String]]]] with WithStructDef {

    override def read(json: JsValue): RetrievalDirective[Map[String, IndexedGenerator[String]]] = json match {
      case spray.json.JsObject(fields) =>
        val name = fields(NAME_KEY).convertTo[String]
        val resource = fields(RESOURCE_TYPE_KEY).convertTo[ResourceType[Map[String, IndexedGenerator[String]]]]
        Retrieve(Resource(resource, name))
    }

    override def write(obj: RetrievalDirective[Map[String, IndexedGenerator[String]]]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(NAME_KEY),
          RegexStructDef(".+".r),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(RESOURCE_TYPE_KEY),
          resourceTypeMapStringGeneratorStringFormat.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

  implicit object RetrievalDirectiveGeneratorString extends JsonFormat[RetrievalDirective[IndexedGenerator[String]]] with WithStructDef {

    import EnumerationJsonProtocol.resourceTypeGeneratorStringFormat

    override def read(json: JsValue): RetrievalDirective[IndexedGenerator[String]] = json match {
      case spray.json.JsObject(fields) =>
        val name = fields(NAME_KEY).convertTo[String]
        val resource = fields(RESOURCE_TYPE_KEY).convertTo[ResourceType[IndexedGenerator[String]]]
        Retrieve(Resource(resource, name))
    }

    override def write(obj: RetrievalDirective[IndexedGenerator[String]]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(NAME_KEY),
          RegexStructDef(".+".r),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(RESOURCE_TYPE_KEY),
          resourceTypeGeneratorStringFormat.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }

}
