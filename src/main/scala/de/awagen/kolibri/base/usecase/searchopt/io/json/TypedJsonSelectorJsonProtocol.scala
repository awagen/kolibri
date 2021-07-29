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
import de.awagen.kolibri.base.usecase.searchopt.io.json.NamedClassTypedKeyJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{JsValueSeqSelector, PlainSelector}
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.datatypes.NamedClassTyped
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}


object TypedJsonSelectorJsonProtocol extends DefaultJsonProtocol {

  implicit object TypedJsonSeqSelectorFormat extends JsonFormat[TypedJsonSeqSelector[_]] {
    override def read(json: JsValue): TypedJsonSeqSelector[_] = json match {
      case spray.json.JsObject(fields) if fields.contains("type") =>
        val selector: JsValueSeqSelector = fields("selector").convertTo[JsValueSeqSelector]
        fields("type").convertTo[String] match {
          case "STRING" =>
            val namedType: NamedClassTyped[String] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[String]]
            TypedJsonSeqSelector[String](selector, namedType)
          case "DOUBLE" =>
            val namedType: NamedClassTyped[Double] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Double]]
            TypedJsonSeqSelector[Double](selector, namedType)
          case "FLOAT" =>
            val namedType: NamedClassTyped[Float] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Float]]
            TypedJsonSeqSelector[Float](selector, namedType)
          case "BOOLEAN" =>
            val namedType: NamedClassTyped[Boolean] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Boolean]]
            TypedJsonSeqSelector[Boolean](selector, namedType)
        }
    }

    // TODO
    override def write(obj: TypedJsonSeqSelector[_]): JsValue = """{}""".toJson
  }

  implicit object TypedJsonSingleValueSelectorFormat extends JsonFormat[TypedJsonSingleValueSelector[_]] {
    override def read(json: JsValue): TypedJsonSingleValueSelector[_] = json match {
      case spray.json.JsObject(fields) if fields.contains("type") =>
        val selector: PlainSelector = fields("selector").convertTo[PlainSelector]
        fields("type").convertTo[String] match {
          case "STRING" =>
            val namedType: NamedClassTyped[String] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[String]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "DOUBLE" =>
            val namedType: NamedClassTyped[Double] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Double]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "FLOAT" =>
            val namedType: NamedClassTyped[Float] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Float]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "BOOLEAN" =>
            val namedType: NamedClassTyped[Boolean] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Boolean]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "SEQ[STRING]" =>
            val namedType: NamedClassTyped[Seq[String]] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Seq[String]]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "SEQ[DOUBLE]" =>
            val namedType: NamedClassTyped[Seq[Double]] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Seq[Double]]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "SEQ[FLOAT]" =>
            val namedType: NamedClassTyped[Seq[Float]] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Seq[Float]]]
            TypedJsonSingleValueSelector(selector, namedType)
          case "SEQ[BOOLEAN]" =>
            val namedType: NamedClassTyped[Seq[Boolean]] = fields("namedType").convertTo[NamedClassTyped[_]].asInstanceOf[NamedClassTyped[Seq[Boolean]]]
            TypedJsonSingleValueSelector(selector, namedType)
        }
    }

    // TODO
    override def write(obj: TypedJsonSingleValueSelector[_]): JsValue = """{}""".toJson
  }

}
