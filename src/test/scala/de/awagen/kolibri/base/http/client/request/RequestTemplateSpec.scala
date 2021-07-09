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

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.model.headers.RawHeader
import de.awagen.kolibri.base.actors.tracking.RequestTrackingActor._
import de.awagen.kolibri.base.testclasses.UnitTestSpec


class RequestTemplateSpec extends UnitTestSpec {


  private[this] def getContext: RequestTemplate = {
    RequestTemplate(contextPath = "testpath", parameters = Map("debug" -> Seq("false"), "echoParams" -> Seq("none")), headers = Seq.empty, body = "", httpMethod = HttpMethods.GET)
  }


  "HttpRequestContext" must {

    "correctly build query string with context path and encode parameters" in {
      //given
      val context = getContext
      //when
      val query: String = context.getRequest.uri.toString()
      //then
      query mustBe "/testpath?debug=false&echoParams=none"
    }
    //
    "correctly set headers required for identifying request for tracking and set start time" +
      "just when the request is requested from context" in {
      //given
      val context = getContext
      // when
      val currentTimeInMs = System.currentTimeMillis()
      Thread.sleep(200)
      val request: HttpRequest = context.getRequest
      //then
      val startTimeValue = request.headers.filter(x => x.name() == PROJECT_HEADER_PREFIX + "START").toList.head.value().toLong
      startTimeValue - currentTimeInMs >= 200 mustBe true
      request.headers.contains(RawHeader(PROJECT_HEADER_PREFIX + "HASH", request.hashCode().toString))
    }
  }

}
