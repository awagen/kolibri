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


package de.awagen.kolibri.base.usecase.searchopt.io.json

import de.awagen.kolibri.base.usecase.searchopt.io.json.JsonSelectorJsonProtocol.{PlainSelectorFormat, _}
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{JsValueSeqSelector, PlainSelector}
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.datatypes.JsonTypeCast.JsonTypeCast
import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.namedTypesFormat
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}


object TypedJsonSelectorJsonProtocol extends DefaultJsonProtocol {

  implicit object TypedJsonSeqSelectorFormat extends JsonFormat[TypedJsonSeqSelector] {
    override def read(json: JsValue): TypedJsonSeqSelector = json match {
      case spray.json.JsObject(fields) if fields.contains("castType") =>
        val selector: JsValueSeqSelector = fields("selector").convertTo[JsValueSeqSelector]
        TypedJsonSeqSelector(selector, fields("castType").convertTo[JsonTypeCast])
    }

    // TODO
    override def write(obj: TypedJsonSeqSelector): JsValue = """{}""".toJson
  }

  implicit object TypedJsonSingleValueSelectorFormat extends JsonFormat[TypedJsonSingleValueSelector] {
    override def read(json: JsValue): TypedJsonSingleValueSelector = json match {
      case spray.json.JsObject(fields) if fields.contains("castType") =>
        val selector: PlainSelector = fields("selector").convertTo[PlainSelector]
        val typeCast: JsonTypeCast = fields("castType").convertTo[JsonTypeCast]
        TypedJsonSingleValueSelector(selector, typeCast)
    }

    // TODO
    override def write(obj: TypedJsonSingleValueSelector): JsValue = """{}""".toJson
  }

}
