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


package de.awagen.kolibri.fleet.zio.http.client.request

import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import zio.{Chunk, ZIO}
import zio.http.{Body, Client, Header, Headers, Method, Path, QueryParams, Request, Response, URL, Version}

object RequestTemplateImplicits {

  implicit class RequestTemplateToZIOHttpRequest(template: RequestTemplate) {

    // TODO: template.protocol not taken into account yet
    def toZIOHttpRequest(host: String): ZIO[Client, Throwable, Response] = {
      for {
        httpClient <- ZIO.service[Client]
        response <- httpClient.request(
          Request(
            body = Body.fromCharSequence(template.body),
            headers = Headers((template.getContextHeaders ++ template.headers).map(x => Header.Custom(x._1, x._2))),
            method = Method.fromString(template.httpMethod),
            url = new URL(
              path = Path.decode(s"$host/${template.query}"),
              queryParams = QueryParams(template.parameters.map(x => (x._1, Chunk.fromIterable(x._2))))
            ),
            version = Version.`HTTP/1.1`,
            remoteAddress = None
          ))
      } yield response
    }

  }

}
