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

import akka.http.scaladsl.model.{MessageEntity, _}
import akka.http.scaladsl.model.headers.RawHeader
import de.awagen.kolibri.base.actors.tracking.RequestTrackingActor._
import de.awagen.kolibri.datatypes.utils.ParameterUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable


object RequestTemplate {

  def apply(contextPath: String,
            parameters: immutable.Map[String, Seq[String]],
            headers: immutable.Seq[HttpHeader],
            body: MessageEntity = HttpEntity.Empty,
            httpMethod: HttpMethod = HttpMethods.GET,
            protocol: HttpProtocol = HttpProtocols.`HTTP/2.0`): RequestTemplate = {
    new RequestTemplate(contextPath = contextPath,
      parameters = parameters,
      headers = headers,
      body = body,
      httpMethod = httpMethod,
      protocol = protocol)
  }

}


/**
  * Immutable http request that sets its own traffic-specific headers when the HttpRequest is requested from it.
  *
  * @param contextPath - context path used in the query
  * @param parameters  - request parameters. Value is Seq[String] in case of multiple params of same name.
  * @param headers     - headers to set for the request.
  * @param body        - body to set for request
  * @param httpMethod  - http version
  * @param protocol    - protocol to use for the request, e.g Http 1.0, 1.1 or 2.0.
  */
class RequestTemplate(val contextPath: String,
                      val parameters: immutable.Map[String, Seq[String]],
                      val headers: immutable.Seq[HttpHeader],
                      val body: MessageEntity,
                      val httpMethod: HttpMethod,
                      val protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`)
  extends HttpRequestProvider {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val fullQueryString: String = ParameterUtils.queryStringFromParameterNamesAndValues(parameters)
  val buffer: StringBuffer = new StringBuffer("")
    .append(if (contextPath.startsWith("/")) contextPath else s"/$contextPath")
  if (fullQueryString.nonEmpty) buffer.append(s"?$fullQueryString")
  val query: String = buffer.toString

  /**
    * we dont know when the request will within the flow be requested,
    * thus we just set it when requested and directly set the relevant headers which are used for
    * tracking
    */
  private[this] var request: HttpRequest = _

  /**
    * Start timestamp and hash with project header prefix. When tracking data about requests,
    * can be used to calculate the request hash based on the request with headers filtered to only contain the
    * project headers (e.g akka sets some headers before sending the request, thus without filtering
    * headers we could not match the request hash, e.g when signalling a request complete).
    *
    * @return
    */
  private[this] def getContextHeaders: immutable.Seq[HttpHeader] = {
    immutable.Seq(RawHeader(PROJECT_HEADER_PREFIX + "START", System.currentTimeMillis().toString),
      RawHeader(PROJECT_HEADER_PREFIX + "HASH", this.hashCode().toString))
  }

  def getRequest: HttpRequest = {
    if (request != null) return request
    request = HttpRequest(
      uri = Uri(s"$query"),
      method = httpMethod,
      protocol = protocol,
      headers = getContextHeaders,
      entity = body
    )
    logger.debug(s"request: $request")
    request
  }

  def getParameter(name: String): Option[Seq[String]] = {
    parameters.get(name)
  }

  def getHeader(name: String): Option[HttpHeader] = {
    headers.find(x => x.name() == name)
  }

  def copy(contextPath: String = contextPath,
           parameters: immutable.Map[String, Seq[String]] = parameters,
           headers: immutable.Seq[HttpHeader] = headers,
           body: MessageEntity = body,
           httpMethod: HttpMethod,
           protocol: HttpProtocol = protocol) =
    new RequestTemplate(contextPath, parameters, headers, body, httpMethod, protocol)

}
