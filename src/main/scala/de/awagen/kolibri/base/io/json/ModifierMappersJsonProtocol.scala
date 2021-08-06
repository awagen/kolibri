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

import de.awagen.kolibri.base.processing.modifiers.ModifierMappers.{BaseBodyMapper, BaseHeadersMapper, BaseParamsMapper, BodyMapper, HeadersMapper, MappingModifier, ParamsMapper}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}
import IndexedGeneratorJsonProtocol._


object ModifierMappersJsonProtocol extends DefaultJsonProtocol {

  implicit object ParamsMapperJsonProtocol extends JsonFormat[ParamsMapper] {
    override def read(json: JsValue): ParamsMapper = json match {
      case spray.json.JsObject(fields) =>
        val replace: Boolean = fields("replace").convertTo[Boolean]
        val values: Map[String, IndexedGenerator[Map[String, Seq[String]]]] = fields("values").convertTo[Map[String, IndexedGenerator[Map[String, Seq[String]]]]]
        BaseParamsMapper(values, replace = replace)
      case e => throw DeserializationException(s"Expected a value of type ParamsMapper  but got value $e")
    }

    // TODO
    override def write(obj: ParamsMapper): JsValue = """{}""".toJson
  }

  implicit object HeaderMapperJsonProtocol extends JsonFormat[HeadersMapper] {
    override def read(json: JsValue): HeadersMapper = json match {
      case spray.json.JsObject(fields) =>
        val replace: Boolean = fields("replace").convertTo[Boolean]
        val values: Map[String, IndexedGenerator[Map[String, String]]] = fields("values").convertTo[Map[String, IndexedGenerator[Map[String, String]]]]
        BaseHeadersMapper(values, replace = replace)
      case e => throw DeserializationException(s"Expected a value of type HeadersMapper  but got value $e")
    }

    // TODO
    override def write(obj: HeadersMapper): JsValue = """{}""".toJson
  }

  implicit object BodyMapperJsonProtocol extends JsonFormat[BodyMapper] {
    override def read(json: JsValue): BodyMapper = json match {
      case spray.json.JsObject(fields) =>
        val values: Map[String, IndexedGenerator[String]] = fields("values").convertTo[Map[String, IndexedGenerator[String]]]
        BaseBodyMapper(values)
      case e => throw DeserializationException(s"Expected a value of type BodyMapper but got value $e")
    }

    // TODO
    override def write(obj: BodyMapper): JsValue = """{}""".toJson
  }

  implicit object MappingModifierJsonProtocol extends JsonFormat[MappingModifier] {
    override def read(json: JsValue): MappingModifier = json match {
      case spray.json.JsObject(fields) =>
        val paramsMapper = fields.get("paramsMapper").map(x => x.convertTo[ParamsMapper]).getOrElse(ParamsMapper.empty)
        val headersMapper = fields.get("headerMapper").map(x => x.convertTo[HeadersMapper]).getOrElse(HeadersMapper.empty)
        val bodyMapper = fields.get("bodyMapper").map(x => x.convertTo[BodyMapper]).getOrElse(BodyMapper.empty)
        val keyGen = fields("keys").convertTo[IndexedGenerator[String]]
        MappingModifier(
          keyGen = keyGen,
          paramsMapper = paramsMapper,
          headersMapper = headersMapper,
          bodyMapper = bodyMapper
        )
      case e => throw DeserializationException(s"Expected a value of type MappingModifier but got value $e")
    }

    // TODO
    override def write(obj: MappingModifier): JsValue = """{}""".toJson
  }


}
