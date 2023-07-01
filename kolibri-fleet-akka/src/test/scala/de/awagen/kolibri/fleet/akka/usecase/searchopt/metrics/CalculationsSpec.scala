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


package de.awagen.kolibri.fleet.akka.usecase.searchopt.metrics

import akka.actor.ActorRef
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import de.awagen.kolibri.definitions.directives.{Resource, ResourceDirectives, ResourceType}
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.resources.{ResourceAlreadyExists, ResourceOK}
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys._
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations._
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.ComputeResultFunctions.{booleanPrecision, countValues, findFirstValue}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricRowFunctions.throwableToMetricRowResponse
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.PlainMetricValueFunctions._
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{IRMetricFunctions, JudgementHandlingStrategy, Metric, MetricsCalculation}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.utils.MathUtils
import de.awagen.kolibri.datatypes.values.Calculations.{ComputeResult, ResultRecord}
import de.awagen.kolibri.datatypes.values.RunningValues
import de.awagen.kolibri.fleet.akka.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ResourceToJobMappingClusterStateManagerActor
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ResourceToJobMappingClusterStateManagerActor.{ProcessResourceDirectives, ProcessedResourceDirectives}
import de.awagen.kolibri.fleet.akka.cluster.ClusterNodeObj
import de.awagen.kolibri.fleet.akka.config.AppConfig.filepathToJudgementProvider
import de.awagen.kolibri.fleet.akka.usecase.searchopt.metrics.CalculationsTestHelper.{NDCG10_NAME, NDCG2_NAME, NDCG5_NAME, PRODUCT_IDS_KEY, requestTemplateForQuery}
import de.awagen.kolibri.fleet.akka.utils.JudgementInfoTestHelper.judgementsToSuccessJudgementInfo
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


class CalculationsSpec extends KolibriTestKitNoCluster
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


  /**
   * TODO: unify this with FlowSpec, where judgement resources are loaded the same way
   */
  def prepareJudgementResource(): Unit = {
    val judgementSupplier = new SerializableSupplier[JudgementProvider[Double]] {
      override def apply(): JudgementProvider[Double] = {
        filepathToJudgementProvider("data/calculations_test_judgements.txt")
      }
    }
    val judgementResourceDirective: ResourceDirectives.ResourceDirective[JudgementProvider[Double]] = ResourceDirectives.getDirective(
      judgementSupplier,
      Resource(ResourceType.JUDGEMENT_PROVIDER, "judgements1")
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



  "JudgementBasedMetricsCalculation" must {
    implicit val ec: ExecutionContext = global

    "correctly calculate metrics" in {
      // given
      prepareJudgementResource()
      val calculation = JudgementsFromResourceIRMetricsCalculations(
        PRODUCT_IDS_KEY,
        REQUEST_TEMPLATE_STORAGE_KEY.name,
        "q",
        Resource(ResourceType.JUDGEMENT_PROVIDER, "judgements1"),
        MetricsCalculation(
          Seq(
            Metric(NDCG5_NAME, MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5))),
            Metric(NDCG10_NAME, MetricFunction(MetricType.NDCG, 10, IRMetricFunctions.ndcgAtK(10))),
            Metric(NDCG2_NAME, MetricFunction(MetricType.NDCG, 2, IRMetricFunctions.ndcgAtK(2)))
          ),
          JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
        ),
        ClusterNodeObj
      )
      val inputData: WeaklyTypedMap[String] = BaseWeaklyTypedMap(mutable.Map.empty)
      inputData.put(REQUEST_TEMPLATE_STORAGE_KEY.name, requestTemplateForQuery("q0"))
      // p4 does not exist in the judgement list and as per above judgement handling strategy
      // is treated as 0.0
      inputData.put(PRODUCT_IDS_KEY, Seq("p0", "p3", "p2", "p1", "p4"))
      // when

      val calcResult: Seq[ResultRecord[_]] = calculation.calculation.apply(inputData)
      val ndcg5Result: ComputeResult[_] = calcResult.find(x => x.name == NDCG5_NAME).get.value
      val ndcg10Result: ComputeResult[_] = calcResult.find(x => x.name == NDCG10_NAME).get.value
      val ndcg2Result: ComputeResult[_] = calcResult.find(x => x.name == NDCG2_NAME).get.value
      val expectedNDCG2Result: ComputeResult[Double] = IRMetricFunctions.ndcgAtK(2).apply(
        judgementsToSuccessJudgementInfo(Seq(0.10, 0.4, 0.3, 0.2, 0.0)))
      val expectedNDCG5Result: ComputeResult[Double] = IRMetricFunctions.ndcgAtK(5).apply(
        judgementsToSuccessJudgementInfo(Seq(0.10, 0.4, 0.3, 0.2, 0.0)))
      // then
      MathUtils.equalWithPrecision[Double](
        ndcg2Result.getOrElse[Any](-10).asInstanceOf[Double],
        expectedNDCG2Result.getOrElse[Any](-1.0).asInstanceOf[Double], 0.0001) mustBe true
      MathUtils.equalWithPrecision[Double](
        ndcg5Result.getOrElse[Any](-10).asInstanceOf[Double],
        expectedNDCG5Result.getOrElse[Any](-1.0).asInstanceOf[Double], 0.0001) mustBe true
      MathUtils.equalWithPrecision[Double](ndcg5Result.getOrElse[Any](-10).asInstanceOf[Double],
        ndcg10Result.getOrElse[Any](-1).asInstanceOf[Double], 0.0001) mustBe true
    }
  }

}
