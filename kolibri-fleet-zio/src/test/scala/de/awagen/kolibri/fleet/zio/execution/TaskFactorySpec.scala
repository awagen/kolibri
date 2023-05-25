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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.immutable.stores.TypedMapStore
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.types.{ClassTyped, JsonTypeCast, NamedClassTyped}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.definitions.directives.{ExpirePolicy, Resource, ResourceDirectives, ResourceType}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers
import de.awagen.kolibri.definitions.resources.{ResourceLoader, ResourceProvider}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics._
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.PlainPathSelector
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.TypedJsonSingleValueSelector
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask.requestTemplateBuilderModifierKey
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.{CalculateMetricsTask, RequestJsonAndParseValuesTask}
import de.awagen.kolibri.fleet.zio.resources.{AtomicResourceStore, NodeResourceProvider}
import de.awagen.kolibri.storage.io.reader.LocalResourceFileReader
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.http.{Client, Request, Response}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object TaskFactorySpec extends ZIOSpecDefault {

  object TestObjects {

    val parsingConfig = ParsingConfig(Seq(
      TypedJsonSingleValueSelector("field1", PlainPathSelector(Seq("results", "field1")), JsonTypeCast.STRING)
    ))
    val connectionSupplier: () => Connection = () => Connection(
      host = "testhost1",
      port = 80,
      useHttps = true,
      credentialsProvider = None)

    val contextPath = "testpath"

    val fixedParams = Map("param1" -> Seq("value1"))

    def httpClientMock: Client = mock[Client]

    def requestAndParseTask(clientMock: Client) = RequestJsonAndParseValuesTask(
      parsingConfig,
      connectionSupplier,
      contextPath,
      fixedParams,
      ZLayer.succeed(clientMock)
    )

    def resourceProvider: ResourceProvider with ResourceLoader = new NodeResourceProvider(
      resourceStore = AtomicResourceStore(),
      waitTimeInSeconds = 5
    )

    def calculateMetricsTask(responseKey: ClassTyped[ProcessingMessage[WeaklyTypedMap[String]]],
                             resourceProviderInst: ResourceProvider) = {
      CalculateMetricsTask(
        requestAndParseSuccessKey = responseKey,
        calculations = Seq(
          Calculations.JudgementsFromResourceIRMetricsCalculations(
            productIdsKey = "productIds",
            queryParamName = "q",
            judgementsResource = Resource(ResourceType.JUDGEMENT_PROVIDER, "judgements"),
            metricsCalculation = MetricsCalculation(
              Seq(
                Metric("dcg_5", MetricFunction(MetricType.DCG, 5, IRMetricFunctions.dcgAtK(5)))
              ),
              JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
            ),
            resourceProvider = resourceProviderInst)
        ),
        metricNameToAggregationTypeMapping = Map("dcg_5" -> AggregationType.DOUBLE_AVG),
        excludeParamsFromMetricRow = Seq.empty,
        successKeyName = "metricRow",
        failKeyName = "metricCalculationFail"
      )
    }

    def requestTemplate = new RequestTemplateBuilder()
      .withParams(Map("q" -> Seq("q1")))
      .build()

    def judgementResourceDirective(judgementFileResourcePath: String): ResourceDirectives.ResourceDirective[JudgementProvider[Double]] = {
      val baseResourceFolder: String = getClass.getResource(judgementFileResourcePath)
        .getPath.stripSuffix(judgementFileResourcePath)
      ResourceDirectives.GenericResourceDirective(
        new Resource[JudgementProvider[Double]](
          ResourceType.JUDGEMENT_PROVIDER,
          "judgements"
        ),
        new SerializableSupplier[JudgementProvider[Double]] {
          override def apply(): JudgementProvider[Double] =
            FileBasedJudgementProvider.createCSVBasedProvider(
              LocalResourceFileReader(
                basePath = baseResourceFolder,
                delimiterAndPosition = None,
                fromClassPath = false
              ),
              judgementFileResourcePath)
        },
        ExpirePolicy.ON_JOB_END
      )
    }
  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TaskFactorySpec")(

    test("execute request and parse response") {
      val clientMock: Client = TestObjects.httpClientMock
      when(clientMock.request(
        ArgumentMatchers.any[Request]
      )(ArgumentMatchers.any, ArgumentMatchers.any))
        .thenReturn(ZIO.attempt(Response.json("""{"results": {"field1": "value1"}}""")))
      val initialMap = TypedMapStore(Map(
        requestTemplateBuilderModifierKey -> RequestTemplateBuilderModifiers
          .RequestParameterModifier(Map("param1" -> Seq("v1")), replace = true)
      ))
      val task = TestObjects.requestAndParseTask(clientMock)
      for {
        value <- task.task(initialMap)
      } yield assertTrue(value.get(task.successKey).get.data.get[String]("field1").get == "value1")
    },

    test("calculate metrics") {
      // given
      val requestTemplate = TestObjects.requestTemplate
      val typedMap = BaseWeaklyTypedMap(mutable.Map(
        "productIds" -> Seq("p5", "p2", "p1", "p4", "p3"),
        // NOTE: metrics calc needs the requestTemplate stored separately under the specific key below
        RequestJsonAndParseValuesTask.requestTemplateKey.name -> requestTemplate
      ))
      val initKey = NamedClassTyped[ProcessingMessage[WeaklyTypedMap[String]]]("initValues")
      val initialMap = TypedMapStore(Map(
        initKey -> ProcessingMessages.Corn(typedMap),
      ))
      val resourceProvider = TestObjects.resourceProvider

      // prepare directive to load judgement data before metrics calculation will retrieve it
      val judgementFileResourcePath: String = "/data/test_judgements.txt"
      val judgementResourceDirective = TestObjects.judgementResourceDirective(judgementFileResourcePath)

      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
      // wait till resource is loaded
      Await.result(resourceProvider.createResource(judgementResourceDirective).future, 10 seconds)
      val metricTask = TestObjects.calculateMetricsTask(initKey, resourceProvider)
      // when, then
      for {
        result <- metricTask.task(initialMap)
        _ <- ZIO.logInfo(s"result: $result")
      } yield assertTrue(result.get(metricTask.successKey).nonEmpty)

    }

  )
}
