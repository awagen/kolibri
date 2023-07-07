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
import de.awagen.kolibri.definitions.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.fleet.zio.http.client.request.RequestTemplateImplicits._
import org.mockito.Mockito.{doReturn, times, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.http.{Client, Request, Response}
import zio.test._
import zio.{Scope, ZIO, ZLayer}

object RequestTemplateImplicitsSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("RequestTemplateImplicitsSpec")(

    test("correctly convert RequestTemplate to request") {
      // given
      val testHost = "https://testurl.com"
      val testContextPath = "test"
      val requestTemplateBuilder = new RequestTemplateBuilder()
        .withHttpMethod("GET")
        .withProtocol("HTTP/1.1")
        .withParams(Map("p1" -> Seq("v1")))
        .withHeaders(Seq(("testHeader1", "testHeaderValue1")))
        .withContextPath(testContextPath)
      val requestTemplate = requestTemplateBuilder.build()
      // mocking
      val clientMock = mock[Client]
      doReturn(ZIO.succeed(null)).when(clientMock).request(ArgumentMatchers.any[Request]())(ArgumentMatchers.any(), ArgumentMatchers.any())

      val requestEffect: ZIO[Client, Throwable, Response] = requestTemplate.toZIOHttpRequest(testHost, Option.empty[Credentials])
      for {
        _ <- requestEffect.provide(ZLayer.succeed(clientMock))
      } yield assert({
        val requestCaptor: ArgumentCaptor[Request] = ArgumentCaptor.forClass(classOf[Request])
        verify(clientMock, times(1)).request(requestCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any())
        requestCaptor.getValue
      })(Assertion.assertion[Request]("correct request")(request => {
        request.url.encode == s"$testHost/$testContextPath?p1=v1" &&
          request.headers.toSeq.exists(x => x.headerName == "testHeader1")
      }))
    }


  )

}
