/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.akka.http.client.request

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate

object RequestTemplateImplicits {

  implicit class RequestTemplateToAkkaHttpRequest(template: RequestTemplate) {

    /**
     * Converting RequestTemplate into akka HttpRequest
     * @return
     */
    def toHttpRequest: HttpRequest = {
      HttpRequest(
        uri = Uri(template.query),
        method = HttpMethod.custom(template.httpMethod),
        protocol = HttpProtocols.getForKey(template.protocol).getOrElse(HttpProtocols.`HTTP/1.1`),
        headers = (template.getContextHeaders ++ template.headers).map(header => RawHeader(header._1, header._2)),
        entity = HttpEntity(ContentType.parse(template.bodyContentType).getOrElse(ContentTypes.`application/json`),
          template.body.getBytes)
      )
    }

  }

}
