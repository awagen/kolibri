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


package de.awagen.kolibri.definitions.usecase.searchopt.io.json

import de.awagen.kolibri.definitions.usecase.searchopt.io.json.JsonSelectorJsonProtocol.{PlainSelectorFormat, _}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.{JsValueSeqSelector, PlainSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.{TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.datatypes.types.JsonTypeCast.JsonTypeCast
import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.namedTypesFormat
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}


object TypedJsonSelectorJsonProtocol extends DefaultJsonProtocol {

  val CAST_TYPE_KEY = "castType"
  val NAME_KEY = "name"
  val SELECTOR_KEY = "selector"

  implicit object TypedJsonSeqSelectorFormat extends JsonFormat[TypedJsonSeqSelector] with WithStructDef {
    override def read(json: JsValue): TypedJsonSeqSelector = json match {
      case spray.json.JsObject(fields) if fields.contains(CAST_TYPE_KEY) =>
        val name: String = fields(NAME_KEY).convertTo[String]
        val selector: JsValueSeqSelector = fields(SELECTOR_KEY).convertTo[JsValueSeqSelector]
        TypedJsonSeqSelector(name, selector, fields(CAST_TYPE_KEY).convertTo[JsonTypeCast])
    }

    override def write(obj: TypedJsonSeqSelector): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(CAST_TYPE_KEY),
            de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.namedTypesFormat.structDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            // TODO: refine regex
            RegexStructDef(".*".r),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(SELECTOR_KEY),
            JsonSelectorJsonProtocol.JsValueSeqSelectorFormat.structDef,
            required = true
          )
        ),
        Seq.empty
      )
    }
  }

  implicit object TypedJsonSingleValueSelectorFormat extends JsonFormat[TypedJsonSingleValueSelector] with WithStructDef {
    override def read(json: JsValue): TypedJsonSingleValueSelector = json match {
      case spray.json.JsObject(fields) if fields.contains(CAST_TYPE_KEY) =>
        val name: String = fields(NAME_KEY).convertTo[String]
        val selector: PlainSelector = fields(SELECTOR_KEY).convertTo[PlainSelector]
        val typeCast: JsonTypeCast = fields(CAST_TYPE_KEY).convertTo[JsonTypeCast]
        TypedJsonSingleValueSelector(name, selector, typeCast)
    }

    override def write(obj: TypedJsonSingleValueSelector): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(CAST_TYPE_KEY),
            de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.namedTypesFormat.structDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            // TODO: refine regex
            RegexStructDef(".*".r),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(SELECTOR_KEY),
            PlainSelectorFormat.structDef,
            required = true
          )
        ),
        Seq.empty
      )
    }
  }

}
