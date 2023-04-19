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

import de.awagen.kolibri.base.io.json.{IndexedGeneratorJsonProtocol, OrderedMultiValuesJsonProtocol}
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers.{BodyMapper, HeadersMapper, ParamsMapper}
import de.awagen.kolibri.base.processing.modifiers.RequestPermutations.{MappingModifier, ModifierGeneratorProvider, RequestPermutation}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import spray.json.{DeserializationException, JsValue, JsonFormat, RootJsonFormat, enrichAny}

object ModifierGeneratorProviderJsonProtocol {
}

case class ModifierGeneratorProviderJsonProtocol(generatorJsonProtocol: IndexedGeneratorJsonProtocol,
                                                 multiValuesProtocol: OrderedMultiValuesJsonProtocol,
                                                 modifierMappersJsonProtocol: ModifierMappersJsonProtocol) {

  import generatorJsonProtocol._
  import modifierMappersJsonProtocol._
  import multiValuesProtocol._

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
      case e =>
        throw DeserializationException(s"Expected a value of type MappingModifier but got value $e")
    }

    // TODO
    override def write(obj: MappingModifier): JsValue = """{}""".toJson
  }

  def stringToContentType(contentType: String): String = contentType match {
    case "json" => "application/json"
    case "plain_utf8" => "text/plain(UTF-8)"
    case "csv" => "text/csv(UTF-8)"
    case "xml" => "text/xml(UTF-8)"
    case "html" => "text/html(UTF-8)"
    case _ => "application/json"
  }

  implicit val requestPermutationFormat: RootJsonFormat[RequestPermutation] = jsonFormat(
    (
      params: Option[OrderedMultiValues],
      headers: Option[OrderedMultiValues],
      bodies: Option[Seq[String]],
      bodyReplacements: Option[OrderedMultiValues],
      headerValueReplacements: Option[OrderedMultiValues],
      urlParameterReplacements: Option[OrderedMultiValues],
      bodyContentType: Option[String]
    ) => RequestPermutation.apply(
      params.getOrElse(GridOrderedMultiValues(Seq.empty)),
      headers.getOrElse(GridOrderedMultiValues(Seq.empty)),
      bodies.getOrElse(Seq.empty),
      bodyReplacements.getOrElse(GridOrderedMultiValues(Seq.empty)),
      headerValueReplacements.getOrElse(GridOrderedMultiValues(Seq.empty)),
      urlParameterReplacements.getOrElse(GridOrderedMultiValues(Seq.empty)),
      stringToContentType(bodyContentType.getOrElse("json"))),
    "params",
    "headers",
    "bodies",
    "bodyReplacements",
    "headerValueReplacements",
    "urlParameterReplacements",
    "bodyContentType"
  )

  implicit object ModifierGeneratorProviderByCaseJsonProtocol extends JsonFormat[ModifierGeneratorProvider] {
    override def read(json: JsValue): ModifierGeneratorProvider = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "MAPPED" =>
          fields("value").convertTo[MappingModifier]
        case "ALL" =>
          fields("value").convertTo[RequestPermutation]
      }
      case e =>
        throw DeserializationException(s"Expected a value of type ModifierGeneratorProvider but got value $e")
    }

    // TODO
    override def write(obj: ModifierGeneratorProvider): JsValue = """{}""".toJson
  }

}
