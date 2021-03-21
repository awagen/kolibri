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

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import de.awagen.kolibri.datatypes.io.BaseIO
import play.api.libs.json.JsValue

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


object HttpResponseHandlers extends BaseIO {

  def discardingConsume(httpResponse: HttpResponse)(implicit actorSystem: ActorSystem, mat: Materializer): Future[Done] = {
    httpResponse.entity.dataBytes
      .runWith(Sink.ignore)
  }

  /**
    * Assuming response to be json, generate parser given response, validation function, parsing function
    *
    * @param response    : HttpResponse
    * @param isValidFunc : validation function on the JsValue
    * @param parseFunc   : parsing function on the JsValue
    * @param actorSystem : implicit
    * @param mat         : implicit Materializer
    * @param ec          : implicit ExecutionContext
    * @tparam T : type the response is to be parsed to
    * @return
    */
  def doJSValueParse[T](response: HttpResponse,
                        isValidFunc: JsValue => Boolean,
                        parseFunc: JsValue => T)
                       (implicit actorSystem: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[Either[Throwable, T]] = {
    val strictEntity: Future[HttpEntity.Strict] = response.entity.toStrict(3.seconds)
    strictEntity flatMap { e =>
      e.dataBytes
        .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
        .map(x => {
          try {
            val json: JsValue = jsonStringToJsValue(x.decodeString("UTF-8"))
            if (!isValidFunc(json)) {
              throw new RuntimeException(s"invalid response: $json")
            }
            val product_ids: T = parseFunc.apply(json)
            Right(product_ids)
          }
          catch {
            case e: Throwable =>
              Left(e)
          }
        })
    }
  }

}
