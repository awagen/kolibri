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


package de.awagen.kolibri.base.http.client.request

import akka.http.scaladsl.model.{HttpHeader, MessageEntity}
import de.awagen.kolibri.base.types.Modifier

import scala.collection.immutable

object RequestTemplateBuilderModifiers {

  case class RequestParameterModifier(params: immutable.Map[String, Seq[String]], replace: Boolean) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withParams(params, replace)
    }

  }

  case class ContextPathModifier(contextPath: String) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withContextPath(contextPath)
    }

  }

  case class HeaderModifier(headers: Seq[HttpHeader], replace: Boolean) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withHeaders(headers, replace)
    }

  }

  case class BodyModifier(body: MessageEntity) extends Modifier[RequestTemplateBuilder] {

    def apply(a: RequestTemplateBuilder): RequestTemplateBuilder = {
      a.withBody(body)
    }

  }

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
