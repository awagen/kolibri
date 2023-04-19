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


package de.awagen.kolibri.fleet.akka.io.json

import de.awagen.kolibri.base.io.json.{IndexedGeneratorJsonProtocol, MappingSupplierJsonProtocol}
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers._
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import spray.json._


/**
 * Json read/write protocols to write/read ModifierMappers to/from json definition
 */
object ModifierMappersJsonProtocol {

  val REPLACE_KEY: String = "replace"
  val VALUES_KEY: String = "values"
}

case class ModifierMappersJsonProtocol(generatorJsonProtocol: IndexedGeneratorJsonProtocol,
                                       mappingSupplierJsonProtocol: MappingSupplierJsonProtocol) {
  import spray.json.DefaultJsonProtocol.{StringJsonFormat, BooleanJsonFormat, mapFormat}
  import ModifierMappersJsonProtocol._
  import generatorJsonProtocol._
  import mappingSupplierJsonProtocol._

  implicit object ParamsMapperJsonProtocol extends JsonFormat[ParamsMapper] {
    override def read(json: JsValue): ParamsMapper = json match {
      case spray.json.JsObject(fields) =>
        val replace: Boolean = fields(REPLACE_KEY).convertTo[Boolean]
        val values: () => Map[String, Map[String, IndexedGenerator[Seq[String]]]] = fields(VALUES_KEY).convertTo[() => Map[String, Map[String, IndexedGenerator[Seq[String]]]]]
        BaseParamsMapper(values.apply(), replace)
      case e => throw DeserializationException(s"Expected a value of type ParamsMapper  but got value $e")
    }

    override def write(obj: ParamsMapper): JsValue = """{}""".toJson
  }

  implicit object HeaderMapperJsonProtocol extends JsonFormat[HeadersMapper] {
    override def read(json: JsValue): HeadersMapper = json match {
      case spray.json.JsObject(fields) =>
        val replace: Boolean = fields("replace").convertTo[Boolean]
        val valuesGen: () => Map[String, Map[String, IndexedGenerator[String]]] = fields("values").convertTo[() => Map[String, Map[String, IndexedGenerator[String]]]]
        BaseHeadersMapper(valuesGen.apply(), replace = replace)
      case e => throw DeserializationException(s"Expected a value of type HeadersMapper  but got value $e")
    }

    override def write(obj: HeadersMapper): JsValue = """{}""".toJson
  }

  implicit object BodyMapperJsonProtocol extends JsonFormat[BodyMapper] {
    override def read(json: JsValue): BodyMapper = json match {
      case spray.json.JsObject(fields) =>
        val values: Map[String, IndexedGenerator[String]] = fields("values").convertTo[Map[String, IndexedGenerator[String]]]
        BaseBodyMapper(values)
      case e => throw DeserializationException(s"Expected a value of type BodyMapper but got value $e")
    }

    override def write(obj: BodyMapper): JsValue = """{}""".toJson
  }

  implicit object MappedModifierMapperJsonProtocol extends JsonFormat[MappedModifierMapper[_]] {
    override def read(json: JsValue): MappedModifierMapper[_] = json match {
      case spray.json.JsObject(fields) =>
        val mapper: ModifierMapper[_] = fields("type").convertTo[String] match {
          case "PARAMS_MAPPER" => fields("mapper").convertTo[ParamsMapper]
          case "HEADER_MAPPER" => fields("mapper").convertTo[HeadersMapper]
          case "BODY_MAPPER" => fields("mapper").convertTo[BodyMapper]
          case e => throw DeserializationException(s"Expected a value of valid mapper type, but got value $e")
        }
        val keyMapping = fields("keyMapping").convertTo[Map[String, String]]
        MappedModifierMapper(keyMapping, mapper)
      case e => throw DeserializationException(s"Expected a value of type MappedModifierMapper[_] but got value $e")
    }

    override def write(obj: MappedModifierMapper[_]): JsValue = """{}""".toJson
  }

}
