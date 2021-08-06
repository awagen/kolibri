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
import de.awagen.kolibri.datatypes.collections.generators.{IndexedGenerator, OneAfterAnotherIndexedGenerator, PermutatingIndexedGenerator}


object ModifierMappers {

  trait ModifierMapper[T] {

    val map: Map[String, IndexedGenerator[T]]

    def getValuesForKey(key: String): Option[IndexedGenerator[T]] = map.get(key)

    def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]]

  }

  object ParamsMapper {

    def empty: ParamsMapper = BaseParamsMapper(Map.empty, replace = false)

  }

  trait ParamsMapper extends ModifierMapper[Map[String, Seq[String]]] {

    val replace: Boolean

    def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] =
      getValuesForKey(key).map(generator =>
        generator.mapGen(value => RequestParameterModifier(value, replace)))
  }

  object HeadersMapper {

    def empty: HeadersMapper = BaseHeadersMapper(Map.empty, replace = false)

  }

  trait HeadersMapper extends ModifierMapper[Map[String, String]] {

    val replace: Boolean

    override def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      getValuesForKey(key).map(generator =>
        generator.mapGen(values => {
          val headers: Seq[RawHeader] = values.toSeq.map(value => RawHeader(value._1, value._2))
          HeaderModifier(headers, replace)
        }))
    }
  }

  object BodyMapper {

    def empty: BodyMapper = BaseBodyMapper(Map.empty)

  }

  trait BodyMapper extends ModifierMapper[String] {
    val contentType: ContentType

    override def getModifiersForKey(key: String): Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      getValuesForKey(key).map(generator =>
        generator.mapGen(value => {
          BodyModifier(HttpEntity.Strict(contentType, ByteString(value)))
        }))
    }
  }

  case class BaseParamsMapper(map: Map[String, IndexedGenerator[Map[String, Seq[String]]]], replace: Boolean) extends ParamsMapper

  case class BaseHeadersMapper(map: Map[String, IndexedGenerator[Map[String, String]]], replace: Boolean) extends HeadersMapper

  case class BaseBodyMapper(map: Map[String, IndexedGenerator[String]], contentType: ContentType = ContentTypes.`application/json`) extends BodyMapper


  /**
    * Given a generator of key values, generate the correct generators that permutate over all given key-specific combination
    * of mapper values
    *
    * @param keyGen        - the generator generating the keys that are mapped to yield the modifiers
    * @param paramsMapper  - a mapper to yield parameter modifiers
    * @param headersMapper -  a mapper to yield header modifiers
    * @param bodyMapper    - a mapper to yield body modifiers
    */
  case class MappingModifier(keyGen: IndexedGenerator[String],
                             paramsMapper: ParamsMapper,
                             headersMapper: HeadersMapper,
                             bodyMapper: BodyMapper) {
    def modifiers: IndexedGenerator[Modifier[RequestTemplateBuilder]] = {
      var perKeyPermutations: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = Seq.empty
      keyGen.iterator.foreach(key => {
        val paramsOpt: Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = paramsMapper.getModifiersForKey(key)
        val headersOpt: Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = headersMapper.getModifiersForKey(key)
        val bodiesOpt: Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = bodyMapper.getModifiersForKey(key)
        var generators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = Seq.empty
        paramsOpt.foreach(x => generators = generators :+ x)
        headersOpt.foreach(x => generators = generators :+ x)
        bodiesOpt.foreach(x => generators = generators :+ x)
        if (generators.nonEmpty) {
          val permutations: IndexedGenerator[Modifier[RequestTemplateBuilder]] = PermutatingIndexedGenerator(generators).mapGen(x => CombinedModifier(x))
          perKeyPermutations = perKeyPermutations :+ permutations
        }
      })
      OneAfterAnotherIndexedGenerator(perKeyPermutations)
    }
  }

}
