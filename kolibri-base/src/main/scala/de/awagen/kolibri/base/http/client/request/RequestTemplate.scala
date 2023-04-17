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

import de.awagen.kolibri.base.http.client.request.RequestTemplate.PROJECT_HEADER_PREFIX
import de.awagen.kolibri.datatypes.utils.ParameterUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable


object RequestTemplate {

  val PROJECT_HEADER_PREFIX = "X_HPY_"

  def apply(contextPath: String,
            parameters: immutable.Map[String, Seq[String]],
            headers: immutable.Seq[(String, String)],
            body: String = "",
            bodyReplacements: immutable.Map[String, String] = immutable.Map.empty,
            urlParameterReplacements: immutable.Map[String, String] = immutable.Map.empty,
            headerValueReplacements: immutable.Map[String, String] = immutable.Map.empty,
            httpMethod: String = "GET",
            protocol: String = "HTTP/2.0"): RequestTemplate = {
    new RequestTemplate(contextPath = contextPath,
      parameters = parameters,
      headers = headers,
      body = body,
      bodyReplacements = bodyReplacements,
      urlParameterReplacements = urlParameterReplacements,
      headerValueReplacements = headerValueReplacements,
      httpMethod = httpMethod,
      protocol = protocol)

  }
}


/**
 * Immutable representation of details for an http request.
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
                      val headers: immutable.Seq[(String, String)],
                      val body: String,
                      val bodyReplacements: immutable.Map[String, String],
                      val urlParameterReplacements: immutable.Map[String, String],
                      val headerValueReplacements: immutable.Map[String, String],
                      val httpMethod: String = "GET",
                      val protocol: String = "HTTP/1.1",
                      val bodyContentType: String = "application/json") {

  val fullQueryString: String = ParameterUtils.queryStringFromParameterNamesAndValues(parameters)
  val buffer: StringBuffer = new StringBuffer("")
    .append(if (contextPath.startsWith("/")) contextPath else s"/$contextPath")
  if (fullQueryString.nonEmpty) buffer.append(s"?$fullQueryString")
  val query: String = buffer.toString

  /**
   * Start timestamp and hash with project header prefix. When tracking data about requests,
   * can be used to calculate the request hash based on the request with headers filtered to only contain the
   * project headers (e.g akka sets some headers before sending the request, thus without filtering
   * headers we could not match the request hash, e.g when signalling a request complete).
   *
   * @return
   */
  def getContextHeaders: immutable.Seq[(String, String)] = {
    immutable.Seq((PROJECT_HEADER_PREFIX + "START", System.currentTimeMillis().toString),
      (PROJECT_HEADER_PREFIX + "HASH", this.hashCode().toString))
  }

  def getParameter(name: String): Option[Seq[String]] = {
    if (parameters.contains(name)) {
      parameters.get(name)
    }
    else {
      bodyReplacements.get(name).map(x => Seq(x))
    }
  }

  def getHeader(name: String): Option[(String, String)] = {
    headers.find(x => x._1 == name)
  }

  def copy(contextPath: String = contextPath,
           parameters: immutable.Map[String, Seq[String]] = parameters,
           headers: immutable.Seq[(String, String)] = headers,
           body: String = body,
           bodyReplacements: immutable.Map[String, String] = bodyReplacements,
           urlParameterReplacements: immutable.Map[String, String] = urlParameterReplacements,
           headerValueReplacements: immutable.Map[String, String] = headerValueReplacements,
           httpMethod: String = httpMethod,
           protocol: String = protocol,
           bodyContentType: String = bodyContentType): RequestTemplate =
    new RequestTemplate(
      contextPath,
      parameters,
      headers,
      body,
      bodyReplacements,
      urlParameterReplacements,
      headerValueReplacements,
      httpMethod,
      protocol,
      bodyContentType)
}

