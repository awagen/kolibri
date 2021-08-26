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


package de.awagen.kolibri.base.processing.modifiers

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity}
import akka.util.ByteString
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{BodyModifier, CombinedModifier, HeaderModifier, RequestParameterModifier}
import de.awagen.kolibri.datatypes.collections.generators.{IndexedGenerator, PermutatingIndexedGenerator}


object ModifierMappers {

  trait ModifierMapper[T] {

    val map: Map[String, T]

    def getValuesForKey(key: String): Option[T] = map.get(key)

    def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]]

  }

  object ParamsMapper {

    def empty: ParamsMapper = BaseParamsMapper(Map.empty, replace = false)

  }

  trait ParamsMapper extends ModifierMapper[Map[String, IndexedGenerator[Seq[String]]]] {

    val replace: Boolean

    def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      val singleModifiers: Seq[IndexedGenerator[RequestParameterModifier]] = getValuesForKey(key).map(mapping =>
        mapping.keys
          .map(paramName => mapping(paramName)
            .mapGen(paramVariant => RequestParameterModifier(Map(paramName -> paramVariant), replace = replace))
          )).getOrElse(Seq.empty).toSeq
      if (singleModifiers.isEmpty) None
      else Some(PermutatingIndexedGenerator(singleModifiers)
        .mapGen(modifierSeq => CombinedModifier(modifierSeq))
      )
    }
  }

  object HeadersMapper {

    def empty: HeadersMapper = BaseHeadersMapper(Map.empty, replace = false)

  }

  trait HeadersMapper extends ModifierMapper[Map[String, IndexedGenerator[String]]] {

    val replace: Boolean

    override def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      val modifierGenerators: Seq[IndexedGenerator[HeaderModifier]] = getValuesForKey(key).map(headerKeyValueMapping =>
        headerKeyValueMapping.map(headerNameAndValue => {
          val headerName = headerNameAndValue._1
          headerNameAndValue._2.mapGen(headerValue => HeaderModifier(Seq(RawHeader(headerName, headerValue)), replace))
        })).getOrElse(Seq.empty).toSeq
      if (modifierGenerators.isEmpty) None
      else Some(PermutatingIndexedGenerator(modifierGenerators).mapGen(headerSeq => CombinedModifier(headerSeq)))
    }
  }

  object BodyMapper {

    def empty: BodyMapper = BaseBodyMapper(Map.empty)

  }

  trait BodyMapper extends ModifierMapper[IndexedGenerator[String]] {
    val contentType: ContentType

    override def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      getValuesForKey(key).map(bodyValuesGen =>
        bodyValuesGen.mapGen(value => BodyModifier(HttpEntity.Strict(contentType, ByteString(value)))))
    }
  }

  case class BaseParamsMapper(map: Map[String, Map[String, IndexedGenerator[Seq[String]]]], replace: Boolean) extends ParamsMapper

  case class BaseHeadersMapper(map: Map[String, Map[String, IndexedGenerator[String]]], replace: Boolean) extends HeadersMapper

  case class BaseBodyMapper(map: Map[String, IndexedGenerator[String]], contentType: ContentType = ContentTypes.`application/json`) extends BodyMapper

}
