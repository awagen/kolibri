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

import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder

import scala.collection.immutable

object RequestTemplateBuilderModifiers {

  type RequestTemplateBuilderModifier = Modifier[RequestTemplateBuilder]

  /**
    * Modifier to add request parameters
    *
    * @param params
    * @param replace
    */
  case class RequestParameterModifier(params: immutable.Map[String, Seq[String]], replace: Boolean) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withParams(params, replace)
    }

  }

  /**
    * Modifier to change context path of the request
    *
    * @param contextPath
    */
  case class ContextPathModifier(contextPath: String) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withContextPath(contextPath)
    }

  }

  /**
    * Modifier to add headers
    *
    * @param headers
    * @param replace
    */
  case class HeaderModifier(headers: Seq[(String, String)], replace: Boolean) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withHeaders(headers, replace)
    }

  }

  /**
    * Modifier to set body
    *
    * @param body
    */
  case class BodyModifier(bodyString: String, contentType: String = "application/json") extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withBody(bodyString, contentType)
    }

  }

  case class BodyReplaceModifier(params: immutable.Map[String, String]) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.addBodyReplaceValues(params)
    }

  }

  case class UrlParameterReplaceModifier(params: immutable.Map[String, String]) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.addUrlParameterReplaceValues(params)
    }

  }

  case class HeaderValueReplaceModifier(params: immutable.Map[String, String]) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.addHeaderReplaceValues(params)
    }

  }

  /**
    * Modifier consisting of multiple other ones. Apply-call will apply all contained modifiers in sequence
    *
    * @param modifiers
    * @tparam T
    */
  case class CombinedModifier[T](modifiers: Seq[Modifier[T]]) extends Modifier[T] {

    def apply(a: T): T = {
      var current = a
      modifiers.foreach(x => {
        current = x.apply(current)
      })
      current
    }
  }

}
