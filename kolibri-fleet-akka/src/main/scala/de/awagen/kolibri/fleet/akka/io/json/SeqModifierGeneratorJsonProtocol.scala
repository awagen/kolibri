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

import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestPermutations.{MappingModifier, RequestPermutation}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.fleet.akka.io.json.ModifierGeneratorProviderJsonProtocol.{MappingModifierJsonProtocol, requestPermutationFormat}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}

object SeqModifierGeneratorJsonProtocol extends DefaultJsonProtocol {

  implicit object SeqModifierGeneratorFormat extends JsonFormat[Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]]] {
    override def read(json: JsValue): Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "MAPPING" =>
          val mappingModifier: MappingModifier = fields("value").convertTo[MappingModifier]
          mappingModifier.modifiers
        case "REQUEST_PERMUTATION" =>
          val requestPermutation: RequestPermutation = fields("value").convertTo[RequestPermutation]
          requestPermutation.modifiers
      }
      case e => throw DeserializationException(s"Expected a value from Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] but got value $e")
    }

    // TODO
    override def write(obj: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]]): JsValue = """{}""".toJson
  }


}
