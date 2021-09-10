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


package de.awagen.kolibri.base.usecase.searchopt.http.client.flows

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpProtocols, HttpRequest, HttpResponse, StatusCode}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.RequestTemplate

import scala.concurrent.ExecutionContext.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}
import scala.concurrent.duration._

object FlowTestHelper {

  implicit val ec: ExecutionContext = global

  def parseResponse[T](response: HttpResponse, parseFunc: String => T)(implicit mat: Materializer): Future[Either[Throwable, T]] = {
    parseHttpEntity(response.entity, parseFunc)
  }

  def parseHttpEntity[T](entity: HttpEntity, parseFunc: String => T)(implicit mat: Materializer): Future[Either[Throwable, T]] = {
    entity.toStrict(3.seconds) flatMap { e =>
      e.dataBytes
        .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
        .map(x => {
          val result = x.decodeString("UTF-8")
          Right(parseFunc(result))
        })
    }
  }

  def createHttpResponse(content: String): HttpResponse = {
    new HttpResponse(
      status = StatusCode.int2StatusCode(200),
      headers = Seq.empty,
      attributes = Map.empty,
      entity = HttpEntity(content),
      protocol = HttpProtocols.`HTTP/1.1`
    )
  }

  def singleRequestProcessingFlow(parsingFunc: HttpResponse => Future[Either[Throwable, String]])(implicit mat: Materializer, ac: ActorSystem): Flow[ProcessingMessages.ProcessingMessage[RequestTemplate], ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] = {
    RequestProcessingFlows.singleRequestFlow(
      x => {
        val parsedRequestEntity: Future[Either[Throwable, String]] = parseHttpEntity[String](x.entity, identity)
        parsedRequestEntity.map({
          case Right(value) => createHttpResponse(value)
          case Left(e) => createHttpResponse(e.getClass.getName)
        })
      },
      Connection("testhost", 0, useHttps = false, None),
      parsingFunc
    )
  }

  def connectionPoolRequestFlow(parsingFunc: HttpResponse => Future[Either[Throwable, String]])(implicit mat: Materializer, as: ActorSystem): Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] = {
    RequestProcessingFlows.connectionPoolFlow[String](
      connectionToRequestFlowFunc = _ => {
        Flow.fromFunction[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate])](
          x => {
            val parsedRequestEntity: Future[Either[Throwable, String]] = parseHttpEntity[String](x._1.entity, identity)
            val httpResponseFut = parsedRequestEntity.map({
              case Right(value) => createHttpResponse(value)
              case Left(e) => createHttpResponse(e.getClass.getName)
            })
            (Success(Await.result(httpResponseFut, 1 second)), x._2)
          })
      },
      connection = Connection("testhost", 0, useHttps = false, None),
      parsingFunc = parsingFunc
    )
  }

}
