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
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder.logger
import de.awagen.kolibri.base.utils.IterableUtils
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import org.slf4j.{Logger, LoggerFactory}

import java.util.Objects
import scala.collection.immutable


object RequestTemplateBuilder {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

}

class RequestTemplateBuilder extends KolibriSerializable {

  private[request] var contextPath: String = ""
  private[request] var parameters: Map[String, Seq[String]] = Map.empty
  private[request] var headers: Seq[HttpHeader] = Seq.empty
  private[request] var bodyContentType: ContentType = ContentTypes.`application/json`
  private[request] var bodyString: String = ""
  // string replacements on the set body value,
  // those are only set in the pre-build phase and only applied
  // on the string value of the body on the build() call, without
  // changing the state of the builder itself
  private[request] var bodyValueReplacementMap: immutable.Map[String, String] = immutable.Map.empty
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

  def withBody(bodyString: String, contentType: ContentType = ContentTypes.`application/json`): RequestTemplateBuilder = {
    this.bodyContentType = contentType
    this.bodyString = bodyString
    this
  }

  def addBodyReplaceValues(replaceMap: immutable.Map[String, String]): RequestTemplateBuilder = {
    this.bodyValueReplacementMap = this.bodyValueReplacementMap ++ replaceMap
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

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[RequestTemplateBuilder]) false
    else {
      val other = obj.asInstanceOf[RequestTemplateBuilder]
      contextPath == other.contextPath &&
        parameters == other.parameters &&
        headers == other.headers &&
        bodyString == other.bodyString &&
        bodyValueReplacementMap == other.bodyValueReplacementMap &&
        bodyContentType == other.bodyContentType &&
        httpMethod == other.httpMethod &&
        protocol == other.protocol
    }
  }

  /**
   * Using the current string value of the body to send,
   * apply all set string replacements on that body value and
   * return the resulting update.
   * Note: does not change any state, purely applies on input value
   * and returns result
   *
   * @return
   */
  private[request] def applyReplacementsToBodyAndReturnNewValue(): String = {
    if (Objects.isNull(this.bodyString)) {
      logger.warn(s"Could not apply replacement of body values" +
        s" since body has not yet been set")
      this.bodyString
    }
    else {
      var modifiedBody = this.bodyString
      this.bodyValueReplacementMap.foreach(paramNameAndValue => {
        modifiedBody = modifiedBody.replace(paramNameAndValue._1, paramNameAndValue._2)
      })
      modifiedBody
    }
  }

  def build(): RequestTemplate = {
    var newBody = HttpEntity.Empty
    if (!Objects.isNull(this.bodyString) && this.bodyString.nonEmpty) {
      val newBodyValue = applyReplacementsToBodyAndReturnNewValue()
      newBody = HttpEntity(this.bodyContentType, newBodyValue.getBytes)
    }
    new RequestTemplate(
      contextPath = contextPath,
      parameters = parameters,
      headers = headers,
      body = newBody,
      bodyReplaceParameters = this.bodyValueReplacementMap,
      httpMethod = httpMethod,
      protocol = protocol
    )
  }

}
