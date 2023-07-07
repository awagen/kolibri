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


package de.awagen.kolibri.fleet.akka.usecase.searchopt.jobdefinitions.parts

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.HttpRequest
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestKit
import akka.util.Timeout
import de.awagen.kolibri.definitions.directives.{Resource, ResourceDirectives, ResourceType}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.definitions.processing.modifiers.Modifier
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestParameterModifier
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.TaggingConfiguration
import de.awagen.kolibri.definitions.resources.{ResourceAlreadyExists, ResourceOK}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations.FromMapCalculation
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{IRMetricFunctions, Metric}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.fleet.akka.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ResourceToJobMappingClusterStateManagerActor
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ResourceToJobMappingClusterStateManagerActor.{ProcessResourceDirectives, ProcessedResourceDirectives}
import de.awagen.kolibri.fleet.akka.cluster.ClusterNodeObj
import de.awagen.kolibri.fleet.akka.config.AppConfig.filepathToJudgementProvider
import de.awagen.kolibri.fleet.akka.usecase.searchopt.jobdefinitions.parts.Flows.{fullProcessingFlow, metricsCalc, processingMsgToRequestTuple}
import de.awagen.kolibri.fleet.akka.usecase.searchopt.metrics.CalculationsTestHelper._
import de.awagen.kolibri.fleet.akka.utils.JudgementInfoTestHelper.judgementsToSuccessJudgementInfo
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FlowsSpec extends KolibriTestKitNoCluster
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  var localResourceManagerActor: ActorRef = _

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  override protected def beforeAll(): Unit = {
    // creating without subscribeToReplicationMessages to avoid errors due to ClusterNode App not started
    // (we are using a test actor system here)
    localResourceManagerActor = system.actorOf(ResourceToJobMappingClusterStateManagerActor.props(
      ClusterNodeObj.LOCAL_RESOURCES_ACTOR_NAME,
      subscribeToReplicationMessages = false))
  }

  def prepareJudgementResource(): Unit = {
    val judgementSupplier = new SerializableSupplier[JudgementProvider[Double]] {
      override def apply(): JudgementProvider[Double] = {
        filepathToJudgementProvider("data/calculations_test_judgements.txt")
      }
    }
    val judgementResourceDirective: ResourceDirectives.ResourceDirective[JudgementProvider[Double]] = ResourceDirectives.getDirective(
      judgementSupplier,
      Resource(ResourceType.JUDGEMENT_PROVIDER, "test1")
    )
    implicit val timeout: Timeout = 5 seconds
    val resourceAskMsg = ProcessResourceDirectives(Seq(judgementResourceDirective), "testJob1")
    val resourceAsk: Future[Any] = localResourceManagerActor ? resourceAskMsg
    val resourcePrepareResult: ProcessedResourceDirectives = Await.result(resourceAsk, timeout.duration).asInstanceOf[ProcessedResourceDirectives]
    logger.info(s"resource directive processing results: ${resourcePrepareResult.states}")
    val mustStopExecution: Boolean = resourcePrepareResult.states.exists(state => !Seq(ResourceOK, ResourceAlreadyExists).contains(state))
    if (mustStopExecution) {
      throw new RuntimeException("could not load judgement resource")
    }
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
      // prepare the judgement availability to sending resource directive
      prepareJudgementResource()
      // define the calculations
      val calculation = getJudgementBasedMetricsCalculation(
        "test1",
        Seq(
          Metric(NDCG5_NAME, MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5))),
          Metric(NDCG10_NAME, MetricFunction(MetricType.NDCG, 10, IRMetricFunctions.ndcgAtK(10))),
          Metric(NDCG2_NAME, MetricFunction(MetricType.NDCG, 2, IRMetricFunctions.ndcgAtK(2)))
        )
      )
      // when
      val calc: ProcessingMessages.ProcessingMessage[MetricRow] = metricsCalc(
        processingMessage,
        Seq(
          calculation,
          FromMapCalculation[Seq[Int], Double](Set("metric1"), "key1", x => Right(x.sum))
        ),
        Map(
          "metric1" -> AggregationType.DOUBLE_AVG,
          NDCG5_NAME -> AggregationType.DOUBLE_AVG,
          NDCG10_NAME -> AggregationType.DOUBLE_AVG,
          NDCG2_NAME -> AggregationType.DOUBLE_AVG
        ),
        Seq(QUERY_PARAM)
      )
      val result: MetricRow = calc.data
      // then
      val idealJudgementOrder = Seq(0.4, 0.3, 0.2, 0.1)
      val expectedValue: Double = IRMetricFunctions.dcgAtK(5)
        .apply(judgementsToSuccessJudgementInfo(Seq(0.1, 0.0, 0.4, 0.3))).getOrElse(-1.0) /
        IRMetricFunctions.dcgAtK(5).apply(judgementsToSuccessJudgementInfo(idealJudgementOrder)).getOrElse(-1.0)

      result.metrics.keySet mustBe Set("metric1", NDCG5_NAME, NDCG10_NAME, NDCG2_NAME)
      result.metrics("metric1").biValue.value2.value mustBe 9
      result.metrics(NDCG5_NAME).biValue.value2.value mustBe expectedValue

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
      // prepare the judgement availability to sending resource directive
      prepareJudgementResource()
      val irCalculation = getJudgementBasedMetricsCalculation(
        "test1",
        Seq(
          Metric(NDCG5_NAME, MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5))),
          Metric(NDCG10_NAME, MetricFunction(MetricType.NDCG, 10, IRMetricFunctions.ndcgAtK(10))),
          Metric(NDCG2_NAME, MetricFunction(MetricType.NDCG, 2, IRMetricFunctions.ndcgAtK(2)))
        )
      )
      // define calculations
      val calculations = Seq(
        irCalculation,
        FromMapCalculation[Seq[Int], Double](Set("metric1"), "key1", x => Right(x.sum))
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
        calculations = calculations,
        Map(
          "metric1" -> AggregationType.DOUBLE_AVG,
          NDCG5_NAME -> AggregationType.DOUBLE_AVG,
          NDCG10_NAME -> AggregationType.DOUBLE_AVG,
          NDCG2_NAME -> AggregationType.DOUBLE_AVG
        )
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

      val idealDCG1: Double = IRMetricFunctions.dcgAtK(5).apply(
        judgementsToSuccessJudgementInfo(Seq(0.4, 0.3, 0.2, 0.1))).getOrElse(-1.0)
      val idealDCG2: Double = IRMetricFunctions.dcgAtK(5).apply(
        judgementsToSuccessJudgementInfo(Seq(0.4, 0.2, 0.1, 0.0))).getOrElse(-1.0)
      val currentDCG1: Double = IRMetricFunctions.dcgAtK(5).apply(
        judgementsToSuccessJudgementInfo(Seq(0.1, 0.0, 0.4, 0.3))).getOrElse(-1)
      val currentDCG2: Double = IRMetricFunctions.dcgAtK(5).apply(
        judgementsToSuccessJudgementInfo(Seq(0.4, 0.0, 0.0, 0.1))).getOrElse(-1)

      result.head.metrics(NDCG5_NAME).biValue.value2.value mustBe (currentDCG1 / idealDCG1)
      result.head.metrics("metric1").biValue.value2.value mustBe 9
      // we changed the query parameter via modifier going through flow,
      // thus the metric value changes since other judgements hold for
      // the other query. "metric1" remains the same
      result(1).metrics(NDCG5_NAME).biValue.value2.value mustBe (currentDCG2 / idealDCG2)
      result(1).metrics("metric1").biValue.value2.value mustBe 9
    }
  }

}
