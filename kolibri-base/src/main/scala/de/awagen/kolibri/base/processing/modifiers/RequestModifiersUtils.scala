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
import akka.http.scaladsl.model.{ContentType, ContentTypes}
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{BodyModifier, BodyReplaceModifier, HeaderModifier, HeaderValueReplaceModifier, RequestParameterModifier, UrlParameterReplaceModifier}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues

import scala.collection.immutable

object RequestModifiersUtils {

  /**
   * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
   * Specifically RequestParameterModifiers, setting the defined request parameter values when applied.
   *
   * @param multiValues - the distinct values to take into account. One generator per single value name.
   * @return
   */
  def multiValuesToRequestParamModifiers(multiValues: OrderedMultiValues, replace: Boolean): Seq[IndexedGenerator[RequestParameterModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          RequestParameterModifier(
            params = immutable.Map(x.name -> Seq(y.toString)),
            replace = replace)
        )
      ))
  }

  /**
   * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
   * Specifically BodyReplaceModifier, replacing the key values that appear in the body with the mapped values when applied.
   *
   * @param multiValues - the distinct values to take into account. One generator per single value name.
   * @return
   */
  def multiValuesToBodyReplaceModifiers(multiValues: OrderedMultiValues): Seq[IndexedGenerator[BodyReplaceModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          BodyReplaceModifier(
            params = immutable.Map(x.name -> y.toString)
          )
        )
      ))
  }

  /**
   * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
   * Specifically HeaderValueReplaceModifier, replacing the values occurring in header values (any of the headers)
   * with the mapped values when applied.
   *
   * @param multiValues - the distinct values to take into account. One generator per single value name.
   * @return
   */
  def multiValuesToHeaderValueReplaceModifiers(multiValues: OrderedMultiValues): Seq[IndexedGenerator[HeaderValueReplaceModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          HeaderValueReplaceModifier(
            params = immutable.Map(x.name -> y.toString)
          )
        )
      ))
  }

  /**
   * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
   * Specifically UrlParameterReplaceModifier, replacing the values matching a key in any parameter value
   * with the mapped values when applied.
   *
   * @param multiValues - the distinct values to take into account. One generator per single value name.
   * @return
   */
  def multiValuesToUrlParameterReplaceModifiers(multiValues: OrderedMultiValues): Seq[IndexedGenerator[UrlParameterReplaceModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          UrlParameterReplaceModifier(
            params = immutable.Map(x.name -> y.toString)
          )
        )
      ))
  }

  /**
   * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
   * Specifically HeaderModifiers, setting the defined header values when applied.
   *
   * @param multiValues - the distinct values to take into account. One generator per single value name.
   * @return
   */
  def multiValuesToHeaderModifiers(multiValues: OrderedMultiValues, replace: Boolean): Seq[IndexedGenerator[HeaderModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          HeaderModifier(
            headers = Seq(RawHeader(x.name, y.toString)),
            replace = replace
          )
        )
      ))
  }

  /**
   * Given sequence of bodies of given content type, create generator of BodyModifiers.
   * When applied, those modifiers set the respective body for the RequestTemplateBuilder.
   *
   * @param bodies : Seq of the distinct body values
   * @return
   */
  def bodiesToBodyModifier(bodies: Seq[String], contentType: ContentType = ContentTypes.`application/json`): IndexedGenerator[BodyModifier] = {
    ByFunctionNrLimitedIndexedGenerator.createFromSeq(bodies).mapGen(bodyValue => {
      BodyModifier(bodyValue, contentType)
    })
  }

}
