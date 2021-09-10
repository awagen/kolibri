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
import akka.http.scaladsl.model.{HttpEntity, HttpProtocols, HttpRequest, HttpResponse, StatusCode}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class RequestProcessingFlowsSpec extends KolibriTestKitNoCluster
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with MockFactory {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  implicit val ec: ExecutionContext = global

  def parseResponse[T](response: HttpResponse, parseFunc: String => T): Future[Either[Throwable, T]] = {
    parseHttpEntity(response.entity, parseFunc)
  }

  def parseHttpEntity[T](entity: HttpEntity, parseFunc: String => T): Future[Either[Throwable, T]] = {
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

  "RequestProcessingFlows" should {

    "correctly react to exception in singleRequestFlow" in {
      // given
      val httpResponse: HttpResponse = createHttpResponse("")
      val singleRequestValueFlow: Flow[ProcessingMessages.ProcessingMessage[RequestTemplate], ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] = RequestProcessingFlows.singleRequestFlow(
        _ => Future.successful(httpResponse),
        Connection("testhost", 0, useHttps = false, None),
        _ => throw new RuntimeException("")
      )
      // when
      val resultsFut: Future[Seq[ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)]]] = Source.single(Corn(RequestTemplate("/", Map.empty, Seq.empty)))
        .via(singleRequestValueFlow)
        .runWith(Sink.seq)
      val result: (Either[Throwable, String], RequestTemplate) = Await.result(resultsFut, 1 second).head.data
      val isCorrectExceptionType = result._1 match {
        case Left(e) if e.isInstanceOf[RuntimeException] => true
        case _ => false
      }
      // then
      isCorrectExceptionType mustBe true
    }

    "correctly process elements in singleRequestFlow" in {
      // given
      val singleRequestValueFlow: Flow[ProcessingMessages.ProcessingMessage[RequestTemplate], ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] = RequestProcessingFlows.singleRequestFlow(
        x => {
          val parsedRequestEntity: Future[Either[Throwable, String]] = parseHttpEntity[String](x.entity, identity)
          parsedRequestEntity.map({
            case Right(value) => createHttpResponse(value)
            case Left(e) => createHttpResponse(e.getClass.getName)
          })
        },
        Connection("testhost", 0, useHttps = false, None),
        x => parseResponse[String](x, x => s"${x}Post")
      )
      // when
      val resultsFut: Future[Seq[ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)]]] =
        Source.apply(Seq(
          Corn(RequestTemplate("/", Map.empty, Seq.empty, body = HttpEntity("request1"))),
          Corn(RequestTemplate("/", Map.empty, Seq.empty, body = HttpEntity("request2")))
        ))
        .via(singleRequestValueFlow)
        .runWith(Sink.seq)
      val result: Seq[Either[Throwable, String]] = Await.result(resultsFut, 1 second).map(x => x.data._1)
      // then
      Set(result:_*) mustBe Set(Right("request1Post"), Right("request2Post"))
    }

    "correctly react to exception in connectionPoolFlow" in {
      // given
      val requestTemplate = RequestTemplate("/", Map.empty, Seq.empty)
      val flow: Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] = RequestProcessingFlows.connectionPoolFlow[String](
        connectionToRequestFlowFunc = _ => {
          Flow.fromFunction[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate])](
            _ => (Success(createHttpResponse("")), Corn(requestTemplate)))
        },
        connection = Connection("testhost", 0, useHttps = false, None),
        parsingFunc = _ => throw new RuntimeException("")
      )
      // when
      val resultFuture: Future[ProcessingMessage[(Either[Throwable, String], RequestTemplate)]] = Source.single(Corn(requestTemplate))
        .via(flow)
        .runWith(Sink.seq).map(x => x.head)
      val result: Either[Throwable, String] = Await.result(resultFuture, 3 second).data._1
      val isCorrectException: Boolean = result match {
        case Left(e) if e.isInstanceOf[RuntimeException] => true
        case _ => false
      }
      // then
      isCorrectException mustBe true
    }

    "correctly process requests in connectionPoolFlow" in {
      // given
      val flow: Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] = RequestProcessingFlows.connectionPoolFlow[String](
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
        parsingFunc = resp => parseResponse[String](resp, content => s"${content}Post")
      )
      // when
      val resultFuture: Future[Seq[ProcessingMessage[(Either[Throwable, String], RequestTemplate)]]] = Source.apply(Seq(
        Corn(RequestTemplate("/", Map.empty, Seq.empty, body = HttpEntity("request1"))),
        Corn(RequestTemplate("/", Map.empty, Seq.empty, body = HttpEntity("request2")))
      ))
        .via(flow)
        .runWith(Sink.seq)
      val result: Seq[Either[Throwable, String]] = Await.result(resultFuture, 3 second).map(x => x.data._1)
      // then
      Set(result:_*) mustBe Set(Right("request1Post"), Right("request2Post"))
    }
  }


}
