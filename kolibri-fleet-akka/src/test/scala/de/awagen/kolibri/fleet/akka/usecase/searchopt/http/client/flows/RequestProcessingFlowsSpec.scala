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


package de.awagen.kolibri.fleet.akka.usecase.searchopt.http.client.flows

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{FlowShape, Graph}
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.fleet.akka.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.fleet.akka.usecase.searchopt.http.client.flows.FlowTestHelper._
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

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

  "RequestProcessingFlows" should {

    "correctly react to exception in singleRequestFlow" in {
      val singleRequestValueFlow: Flow[ProcessingMessages.ProcessingMessage[RequestTemplate], ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] =
        singleRequestProcessingFlow(_ => throw new RuntimeException(""))
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
      val singleRequestValueFlow: Flow[ProcessingMessages.ProcessingMessage[RequestTemplate], ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] =
        singleRequestProcessingFlow(x => parseResponse[String](x, x => s"${x}Post")
        )
      // when
      val resultsFut: Future[Seq[ProcessingMessages.ProcessingMessage[(Either[Throwable, String], RequestTemplate)]]] =
        Source.apply(Seq(
          Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request1")),
          Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request2"))
        ))
          .via(singleRequestValueFlow)
          .runWith(Sink.seq)
      val result: Seq[Either[Throwable, String]] = Await.result(resultsFut, 1 second).map(x => x.data._1)
      // then
      Set(result: _*) mustBe Set(Right("request1Post"), Right("request2Post"))
    }

    "correctly react to exception in connectionPoolFlow" in {
      // given
      val flow: Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] =
        connectionPoolRequestFlow(parsingFunc = _ => throw new RuntimeException(""))
      // when
      val resultFuture: Future[ProcessingMessage[(Either[Throwable, String], RequestTemplate)]] =
        Source.single(Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request1")))
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
      val flow: Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, String], RequestTemplate)], NotUsed] =
        connectionPoolRequestFlow(parsingFunc = resp => parseResponse[String](resp, content => s"${content}Post"))
      // when
      val resultFuture: Future[Seq[ProcessingMessage[(Either[Throwable, String], RequestTemplate)]]] = Source.apply(Seq(
        Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request1")),
        Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request2"))
      ))
        .via(flow)
        .runWith(Sink.seq)
      val result: Seq[Either[Throwable, String]] = Await.result(resultFuture, 3 second).map(x => x.data._1)
      // then
      Set(result: _*) mustBe Set(Right("request1Post"), Right("request2Post"))
    }

    "correctly process in requestAndParsingFlow" in {
      // given
      val connections = Seq(
        Connection("testhost1", 80, useHttps = false, None),
        Connection("testhost2", 81, useHttps = false, None)
      )
      // when
      val responseAndParsingFlowGraph: Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, String], RequestTemplate)]], NotUsed] = RequestProcessingFlows.balancingRequestAndParsingFlow[String](
        connections,
        connectionToRequestTemplateProcessingAndParsing
      )
      val resultFut: Future[Seq[ProcessingMessage[(Either[Throwable, String], RequestTemplate)]]] = Source.apply(Seq(
        Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request1")),
        Corn(RequestTemplate("/", Map.empty, Seq.empty, body = "request2"))
      )).via(Flow.fromGraph(responseAndParsingFlowGraph))
        .runWith(Sink.seq)
      val result: Seq[Either[Throwable, String]] = Await.result(resultFut, 2 second).map(x => x.data._1)
      // then
      Set(result: _*) mustBe Set(Right("request1"), Right("request2"))
    }
  }

}
