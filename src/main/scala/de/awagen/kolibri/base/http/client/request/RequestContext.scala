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

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import de.awagen.kolibri.base.actors.tracking.RequestTrackingActor._
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.utils.ParameterUtils

import scala.collection.immutable


object RequestContext {

  def apply(groupId: String,
            requestSequenceId: Int,
            contextPath: String,
            parameters: Map[String, Seq[String]],
            protocol: HttpProtocol = HttpProtocols.`HTTP/2.0`): RequestContext = {
    new RequestContext(groupId = groupId, requestSequenceId = requestSequenceId, contextPath = contextPath, parameters = parameters, protocol)
  }

}


/**
  * @param groupId           - the groupId of the query. Can be used e.g for marking the request as belonging to a certain job
  * @param requestSequenceId - the sequence id providing some ordering, e.g in combination with the provided groupId
  * @param contextPath       - context path used in the query
  * @param parameters        - request parameters
  * @param protocol          - protocol to use for the request, e.g Http 1.0, 1.1 or 2.0.
  */
class RequestContext(val groupId: String, val requestSequenceId: Int, val contextPath: String, val parameters: Map[String, Seq[String]],
                     val protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`)
  extends TaggedWithType[Tag] with HttpRequestProvider {

  val fullQueryString: String = ParameterUtils.queryStringFromParameterNamesAndValues(parameters)
  val buffer: StringBuffer = new StringBuffer("")
    .append(contextPath)
  if (fullQueryString.nonEmpty) buffer.append(s"?$fullQueryString")
  val query: String = buffer.toString

  /**
    * we dont know when the request will within the flow be requested from the RequestExecutionContext,
    * thus we just set it when requested and directly set the relevant headers which are used for
    * tracking
    */
  private[this] var request: HttpRequest = _

  /**
    * Start timestamp and groupId with project header prefix. When tracking data about requests,
    * can be used to calculate the request hash based on the request with headers filtered to only contain the
    * project headers (e.g akka sets some headers before sending the request, thus without filtering
    * headers we could not match the request hash, e.g when signalling a request complete).
    *
    * @return
    */
  def getContextHeaders: immutable.Seq[HttpHeader] = {
    immutable.Seq(RawHeader(PROJECT_HEADER_PREFIX + "START", System.currentTimeMillis().toString),
      RawHeader(PROJECT_HEADER_PREFIX + "G_ID", groupId))
  }

  def getRequest: HttpRequest = {
    if (request != null) return request
    request = HttpRequest(method = HttpMethods.GET, uri = Uri(s"/$query"), protocol = protocol)
      .withHeaders(getContextHeaders)
    request
  }

  def getParameter(name: String): Option[Seq[String]] = {
    parameters.get(name)
  }

  def copy(groupId: String = groupId,
           requestSequenceId: Int = requestSequenceId,
           contextPath: String = contextPath,
           parameters: Map[String, Seq[String]] = parameters,
           protocol: HttpProtocol = protocol): RequestContext =
    new RequestContext(groupId, requestSequenceId, contextPath, parameters, protocol)

}
