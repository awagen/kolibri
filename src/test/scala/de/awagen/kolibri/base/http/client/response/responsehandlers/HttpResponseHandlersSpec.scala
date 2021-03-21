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

package de.awagen.kolibri.base.http.client.response.responsehandlers

import akka.http.scaladsl.model._
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.JsValue

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}


class HttpResponseHandlersSpec extends KolibriTestKitNoCluster
  with ImplicitSender //required for expectMsg
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  val testJson: String = "{\"response\": [{\"product_id\": \"p1\"}, {\"product_id\": \"p2\"}, {\"product_id\": \"p3\"}, {\"product_id\": \"p4\"}]}"

  def retrieveProductIds(response: JsValue): Seq[String] = {
    (response \ "response" \\ "product_id").map(_.as[String]).toSeq
  }

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "HttpResponseHandler" must {

    "correctly generate JSValue parser" in {
      // given

      val httpResponse = HttpResponse(
        status = StatusCode.int2StatusCode(200),
        headers = Nil,
        entity = HttpEntity(ContentType(MediaType.applicationWithFixedCharset("json", HttpCharsets.`UTF-8`, "json")),
          testJson)
      )
      // when
      val resultFuture: Future[Either[Throwable, Seq[String]]] = HttpResponseHandlers.doJSValueParse(
        response = httpResponse,
        isValidFunc = _ => true,
        parseFunc = retrieveProductIds
      )
      val result: Either[Throwable, Seq[String]] = Await.result(resultFuture, 1 minute)
      // then
      result mustBe Right(Seq("p1", "p2", "p3", "p4"))
    }

  }

}
