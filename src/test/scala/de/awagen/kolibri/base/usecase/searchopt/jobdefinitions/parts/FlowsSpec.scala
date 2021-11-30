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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts

import akka.NotUsed
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestKit
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.Connections.Connection
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestParameterModifier
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.TaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Flows.{fullProcessingFlow, metricsCalc, processingMsgToRequestTuple}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.FromMapCalculation
import de.awagen.kolibri.base.usecase.searchopt.metrics.CalculationsTestHelper._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{Calculations, IRMetricFunctions, Metric}
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.MetricRow
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FlowsSpec extends KolibriTestKitNoCluster
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "Flows" must {

    "correctly apply modifierToProcessingMessage" in {
      // given
      val params: Map[String, Seq[String]] = Map("p1" -> Seq("v1"))
      // when
      val modFunc: Modifier[RequestTemplateBuilder] => ProcessingMessages.ProcessingMessage[RequestTemplate] = Flows.modifierToProcessingMessage("testpath/", params)
      val modifier1: Modifier[RequestTemplateBuilder] = x => x.withContextPath("newContextPath/")
      val modifier2: Modifier[RequestTemplateBuilder] = x => x
      val result1: ProcessingMessages.ProcessingMessage[RequestTemplate] = modFunc.apply(modifier1)
      val result2: ProcessingMessages.ProcessingMessage[RequestTemplate] = modFunc.apply(modifier2)
      // then
      val template1: RequestTemplate = result1.data
      val template2: RequestTemplate = result2.data
      template1.contextPath mustBe "/newContextPath/"
      template2.contextPath mustBe "/testpath/"
      template1.parameters mustBe params
      template2.parameters mustBe params
    }

    "correctly generate processingFlow" in {
      // given
      val params: Map[String, Seq[String]] = Map("p1" -> Seq("v1"))
      val flow: Flow[Modifier[RequestTemplateBuilder], ProcessingMessages.ProcessingMessage[RequestTemplate], NotUsed] = Flows.modifiersToRequestTemplateMessageFlow("/testpath/", params)
      val modifier1: Modifier[RequestTemplateBuilder] = x => x.withContextPath("newContextPath/")
      val modifier2: Modifier[RequestTemplateBuilder] = x => x
      // when
      val resultFut: Future[Seq[ProcessingMessages.ProcessingMessage[RequestTemplate]]] = Source.apply(Seq(modifier1, modifier2))
        .via(flow)
        .runWith(Sink.seq)
      val result: Seq[RequestTemplate] = Await.result(resultFut, 2 seconds)
        .map(x => x.data)
      // then
      result.head.parameters mustBe params
      result.head.contextPath mustBe "/newContextPath/"
      result(1).parameters mustBe params
      result(1).contextPath mustBe "/testpath/"
    }

    "correctly provide processingMsgToRequestTuple" in {
      // given
      val params: Map[String, Seq[String]] = Map("p1" -> Seq("v1"))
      val messages = Seq(
        Corn(RequestTemplate("path1", params, Seq.empty)),
        Corn(RequestTemplate("path2", params, Seq.empty))
      )
      // when
      val result: Seq[(HttpRequest, ProcessingMessages.ProcessingMessage[RequestTemplate])] = Await.result(
        Source.apply(messages)
          .via(processingMsgToRequestTuple)
          .runWith(Sink.seq),
        2 seconds
      )
      // then
      result.size mustBe 2
      result.head._2.data.parameters mustBe params
      result(1)._2.data.parameters mustBe params
      result.head._2.data.contextPath mustBe "path1"
      result(1)._2.data.contextPath mustBe "path2"
      result.head._1 mustBe result.head._2.data.getRequest
      result(1)._1 mustBe result(1)._2.data.getRequest
    }

    "correctly provide metricsCalc" in {
      implicit val ec: ExecutionContext = global
      // given
      val params: Map[String, Seq[String]] = Map(QUERY_PARAM -> Seq("q0"), "p1" -> Seq("v1"))
      val requestTemplate = RequestTemplate("path1", params, Seq.empty)
      val typedMap: WeaklyTypedMap[String] = BaseWeaklyTypedMap(mutable.Map(
        REQUEST_TEMPLATE_KEY -> requestTemplate,
        PRODUCT_IDS_KEY -> Seq("p0", "p5", "p3", "p2"),
        "key1" -> Seq(5, 4)
      ))
      val processingMessage = Corn((Right(typedMap), requestTemplate))
      val calculation: Calculations.JudgementBasedMetricsCalculation =
        getJudgementBasedMetricsCalculation("data/calculations_test_judgements.txt",
          Seq(
            Metric(NDCG5_NAME, IRMetricFunctions.ndcgAtK(5)),
            Metric(NDCG10_NAME, IRMetricFunctions.ndcgAtK(10)),
            Metric(NDCG2_NAME, IRMetricFunctions.ndcgAtK(2))
          )
        )
      // when
      val calcFut: Future[ProcessingMessages.ProcessingMessage[MetricRow]] = metricsCalc(
        processingMessage,
        calculation,
        Seq(
          FromMapCalculation[Seq[Int], Double]("metric1", "key1", x => Right(x.sum))
        ),
        REQUEST_TEMPLATE_KEY,
        Seq(QUERY_PARAM)
      )
      val result: MetricRow = Await.result(calcFut, 2 seconds).data
      // then
      result.metrics.keySet mustBe Set("metric1", NDCG5_NAME, NDCG10_NAME, NDCG2_NAME)
      result.metrics("metric1").biValue.value2.value mustBe 9
      result.metrics(NDCG5_NAME).biValue.value2.value mustBe
        IRMetricFunctions.ndcgAtK(5).apply(Seq(0.1, 0.0, 0.4, 0.3)).getOrElse(-1)
    }

    "correctly provide fullProcessingFlow" in {
      implicit val ec: ExecutionContext = global
      // given
      val connections: Seq[Connection] = Seq(
        Connection("host1", 80, useHttps = false, None),
        Connection("host2", 80, useHttps = false, None)
      )
      val contextPath = "cPath"
      val fixedParams: Map[String, Seq[String]] = Map(QUERY_PARAM -> Seq("q0"), "p1" -> Seq("v1"))
      val excludeParamsFromMetricRow = Seq.empty
      val taggingConfiguration: Option[TaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] = None
      val requestTemplateStorageKey = REQUEST_TEMPLATE_KEY
      val mapFutureMetricRowCalculation =
        getJudgementBasedMetricsCalculation("data/calculations_test_judgements.txt",
          Seq(
            Metric(NDCG5_NAME, IRMetricFunctions.ndcgAtK(5)),
            Metric(NDCG10_NAME, IRMetricFunctions.ndcgAtK(10)),
            Metric(NDCG2_NAME, IRMetricFunctions.ndcgAtK(2))
          )
        )
      val singleMapCalculations = Seq(
        FromMapCalculation[Seq[Int], Double]("metric1", "key1", x => Right(x.sum))
      )
      val requestAndParsingFlow: Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)], NotUsed] = Flow.fromFunction(x => {
        val map: WeaklyTypedMap[String] = BaseWeaklyTypedMap(
          mutable.Map(
            PRODUCT_IDS_KEY -> Seq(),
            REQUEST_TEMPLATE_KEY -> x.data,
            PRODUCT_IDS_KEY -> Seq("p0", "p5", "p3", "p2"),
            "key1" -> Seq(5, 4)
          )
        )
        Corn((Right(map), x.data))
      })
      val fullFlow = fullProcessingFlow(
        connections = connections,
        contextPath = contextPath,
        fixedParams = fixedParams,
        requestAndParsingFlow = requestAndParsingFlow,
        excludeParamsFromMetricRow = excludeParamsFromMetricRow,
        taggingConfiguration = taggingConfiguration,
        requestTemplateStorageKey = requestTemplateStorageKey,
        mapFutureMetricRowCalculation = mapFutureMetricRowCalculation,
        singleMapCalculations = singleMapCalculations
      )
      // when
      val result: Seq[MetricRow] = Await.result(Source.apply(
        Seq(
          RequestParameterModifier(Map(QUERY_PARAM -> Seq("q0")), replace = true),
          RequestParameterModifier(Map(QUERY_PARAM -> Seq("q1")), replace = true)
        )
      ).via(fullFlow)
        .runWith(Sink.seq), 5 seconds).map(x => x.data)
      // then
      result.size mustBe 2
      result.head.metrics.keySet mustBe Set("metric1", NDCG5_NAME, NDCG10_NAME, NDCG2_NAME)
      result.head.metrics(NDCG5_NAME).biValue.value2.value mustBe
        IRMetricFunctions.ndcgAtK(5).apply(Seq(0.1, 0.0, 0.4, 0.3)).getOrElse(-1)
      result.head.metrics("metric1").biValue.value2.value mustBe 9
      // we changed the query parameter via modifier going through flow,
      // thus the metric value changes since other judgements hold for
      // the other query. "metric1" remains the same
      result(1).metrics(NDCG5_NAME).biValue.value2.value mustBe
        IRMetricFunctions.ndcgAtK(5).apply(Seq(0.4, 0.0, 0.0, 0.1)).getOrElse(-1)
      result(1).metrics("metric1").biValue.value2.value mustBe 9
    }
  }

}
