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

import akka.http.scaladsl.model.{ContentType, ContentTypes}
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.RequestModifiersUtils.{bodiesToBodyModifier, multiValuesToHeaderModifiers, multiValuesToRequestParamModifiers}
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{BodyModifier, HeaderModifier, RequestParameterModifier}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues

/**
  * Container for the distinct values to generate Modifier[RequestTemplateBuilder] from. One single parameter name
  * in the case of params and headers a generator is created providing all defined values.
  * For bodies its a single generator providing all distinct body values. bodyContentType determines the format
  * of the passed body
  *
  * @param params
  * @param headers
  * @param bodies
  * @param bodyContentType
  */
case class RequestPermutation(params: OrderedMultiValues,
                              headers: OrderedMultiValues,
                              bodies: Seq[String],
                              bodyContentType: ContentType = ContentTypes.`application/json`) {
  val paramModifierGenerators: Seq[IndexedGenerator[RequestParameterModifier]] = multiValuesToRequestParamModifiers(params, replace = false)
    .filter(gen => gen.size > 0)
  val headerModifierGenerators: Seq[IndexedGenerator[HeaderModifier]] = multiValuesToHeaderModifiers(headers, replace = false)
    .filter(gen => gen.size > 0)
  val bodyModifierGenerator: Option[IndexedGenerator[BodyModifier]] = if (bodies.isEmpty) None else Some(bodiesToBodyModifier(bodies, bodyContentType))

  def getModifierSeq: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
    val combined: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = paramModifierGenerators ++ headerModifierGenerators
    bodyModifierGenerator.map(x => combined :+ x).getOrElse(combined)
  }
}