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

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableConsumer, SerializableSupplier}
import de.awagen.kolibri.datatypes.types.{ClassTyped, JsonTypeCast}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.definitions.directives.{ExpirePolicy, Resource, ResourceDirectives, ResourceType}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.resources.ResourceProvider
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics._
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.PlainPathSelector
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.TypedJsonSingleValueSelector
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.fleet.zio.execution.JobMessagesImplicits.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.{CalculateMetricsTask, RequestJsonAndParseValuesTask}
import de.awagen.kolibri.storage.io.reader.LocalResourceFileReader
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.http.{Client, Request, Response}
import zio.{ZIO, ZLayer}

object TaskTestObjects {

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

  def httpClientMock(response: String): Client = {
    val clientMock = mock[Client]
    when(clientMock.request(
      ArgumentMatchers.any[Request]
    )(ArgumentMatchers.any, ArgumentMatchers.any))
      .thenReturn(ZIO.attempt(Response.json(response)))
    clientMock
  }

  def taggerForRequestAndParseTask: SerializableConsumer[ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)]]  = msg => {
    val requestTemplate = msg.data._2
    val queryValue = requestTemplate.getParameter("q").get.head
    msg.addTag(TagType.AGGREGATION, StringTag(queryValue))
  }

  def requestAndParseTask(clientMock: Client, parsingConfig: ParsingConfig) = RequestJsonAndParseValuesTask(
    parsingConfig,
    RequestAndParsingResultTaggerConfig(_ => (), taggerForRequestAndParseTask),
    connectionSupplier,
    contextPath,
    fixedParams,
    "GET",
    ZLayer.succeed(clientMock),
  )

  def calculateMetricsTask(responseKey: ClassTyped[ProcessingMessage[WeaklyTypedMap[String]]],
                           resourceProviderInst: ResourceProvider) = {
    CalculateMetricsTask(
      requestAndParseSuccessKey = responseKey,
      requestTemplateKey = REQUEST_TEMPLATE_STORAGE_KEY.name,
      calculations = Seq(
        Calculations.JudgementsFromResourceIRMetricsCalculations(
          productIdsKey = "productIds",
          requestTemplateKey = REQUEST_TEMPLATE_STORAGE_KEY.name,
          queryParamName = "q",
          judgementsResource = Resource(ResourceType.JUDGEMENT_PROVIDER, "judgements"),
          metricsCalculation = MetricsCalculation(
            Seq(
              Metric("ndcg_5", MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5)))
            ),
            JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
          ),
          resourceProvider = resourceProviderInst)
      ),
      metricNameToAggregationTypeMapping = Map("ndcg_5" -> AggregationType.DOUBLE_AVG),
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
