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

import de.awagen.kolibri.definitions.domain.jobdefinitions.provider.Credentials
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import zio.ZIO
import zio.http._

import java.net.URI

object RequestTemplateImplicits {

  val protocolMapping: Map[String, Version] = Map(
    "HTTP/1.1" ->  Version.`HTTP/1.1`,
    "HTTP/1.0" ->  Version.`HTTP/1.0`
  )

  implicit class RequestTemplateToZIOHttpRequest(template: RequestTemplate) {

    def toZIOHttpRequest(host: String, credentialsOpt: Option[Credentials]): ZIO[Client, Throwable, Response] = {
      for {
        httpClient <- ZIO.service[Client]
        bodyContent <- ZIO.succeed(Body.fromCharSequence(template.body))
        headers <- ZIO.succeed({
          Headers(
            (template.getContextHeaders ++ template.headers).map(x => Header.Custom(x._1, x._2)) ++
              // add basic auth if credentials passed as argument
              credentialsOpt
                .map(cred => Seq(Header.Authorization.Basic(username = cred.username, password = cred.password)))
                .getOrElse(Seq.empty)
          )
        })
        request <- ZIO.succeed(
          Request(
            body = bodyContent,
            headers = headers,
            method = Method.fromString(template.httpMethod),
            url = URL.fromURI(new URI(s"${host.stripSuffix("/")}/${template.query.stripPrefix("/")}")).get,
            version = protocolMapping.getOrElse(template.protocol, Version.`HTTP/1.1`),
            remoteAddress = None
          )
        )
        _ <- ZIO.logDebug(s"request: $request")
        response <- httpClient.request(request).onError(cause => ZIO.logError(s"error when requesting, cause: ${cause.trace}"))
      } yield response
    }

  }

}
