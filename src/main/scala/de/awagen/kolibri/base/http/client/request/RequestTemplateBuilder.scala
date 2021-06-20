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

import akka.http.scaladsl.model.{HttpEntity, HttpHeader, HttpMethod, HttpMethods, HttpProtocol, HttpProtocols, MessageEntity}
import de.awagen.kolibri.base.utils.IterableUtils


class RequestTemplateBuilder {

  private[request] var contextPath: String = ""
  private[request] var parameters: Map[String, Seq[String]] = Map.empty
  private[request] var headers: Seq[HttpHeader] = Seq.empty
  private[request] var body: MessageEntity = HttpEntity.Empty
  private[request] var httpMethod: HttpMethod = HttpMethods.GET
  private[request] var protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`

  def withContextPath(path: String): RequestTemplateBuilder = {
    this.contextPath = if (path.startsWith("/")) path else s"/$path"
    this
  }

  def withParams(params: Map[String, Seq[String]], replace: Boolean = false): RequestTemplateBuilder = {
    this.parameters = IterableUtils.combineMaps(this.parameters, params, replace = replace)
    this
  }

  def withHeaders(headers: Seq[HttpHeader], replace: Boolean = false): RequestTemplateBuilder = {
    this.headers = if (replace) headers else (Set(this.headers: _*) ++ Set(headers: _*)).toSeq
    this
  }

  def withBody(body: MessageEntity): RequestTemplateBuilder = {
    this.body = body
    this
  }

  def withHttpMethod(method: HttpMethod): RequestTemplateBuilder = {
    this.httpMethod = method
    this
  }

  def withProtocol(protocol: HttpProtocol): RequestTemplateBuilder = {
    this.protocol = protocol
    this
  }

  def build(): RequestTemplate = {
    new RequestTemplate(
      contextPath = contextPath,
      parameters = parameters,
      headers = headers,
      body = body,
      httpMethod = httpMethod,
      protocol = protocol
    )
  }

}
