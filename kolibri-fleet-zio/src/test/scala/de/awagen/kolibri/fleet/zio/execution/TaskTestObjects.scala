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

import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableConsumer, SerializableSupplier}
import de.awagen.kolibri.datatypes.types.{ClassTyped, JsonTypeCast}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.definitions.directives.{ExpirePolicy, Resource, ResourceDirectives, ResourceType}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.HttpMethod
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.processing.JobMessages.SearchEvaluationDefinition
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues.{ParameterValuesDefinition, ValueSeqGenDefinition, ValueType}
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.{BaseTaggingConfiguration, EitherThrowableOrTaggedWeaklyTypedMapStore, TaggedMetricRowStore, TaggedRequestTemplateStore}
import de.awagen.kolibri.definitions.resources.ResourceProvider
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics._
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.PlainPathSelector
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors._
import de.awagen.kolibri.definitions.usecase.searchopt.parse.{JsonSelectors, ParsingConfig}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.fleet.zio.execution.JobMessagesImplicits.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.{CalculateMetricsTask, RequestJsonAndParseValuesTask}
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
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

  def taggerForRequestAndParseTask: SerializableConsumer[ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)]] = msg => {
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

  def metricsCalculation: MetricsCalculation = {
    MetricsCalculation(
      Seq(
        Metric("ndcg_5", MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5)))
      ),
      JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
    )
  }

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
          metricsCalculation = metricsCalculation,
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

  // TODO: move the below part to JobDefinitionTestObjects or the like

  def initTaggingConfig(queryParam: String): SerializableConsumer[TaggedRequestTemplateStore] = TaggingConfigurations.requestByParameterTagger(queryParam, AGGREGATION, _ => true, extend = false)

  val processedTaggerConfig: SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] = _ => ()
  val resultTaggerConfig: SerializableConsumer[TaggedMetricRowStore] = _ => ()

  def taggingConfiguration(queryParam: String): BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow] =
    BaseTaggingConfiguration(
      initTagger = initTaggingConfig(queryParam),
      processedTagger = processedTaggerConfig,
      resultTagger = resultTaggerConfig
    )

  def searchEvaluationDefinition: SearchEvaluationDefinition = {
    val judgementFileResourcePath: String = "/data/test_judgements.txt"
    val judgementResourceDirective = TaskTestObjects.judgementResourceDirective(judgementFileResourcePath)
    val taggerConfig: Option[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] =
      Some(
        taggingConfiguration("q")
      )
    val requestParams: Seq[ValueSeqGenDefinition[_]] = Seq(
      ParameterValuesDefinition("q", ValueType.URL_PARAMETER,
        () => ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("q1", "q2", "q3", "q4"))),
      ParameterValuesDefinition("otherParam", ValueType.URL_PARAMETER,
        () => ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("g1", "g2", "g3", "g4")))
    )
    SearchEvaluationDefinition(
      jobName = "testJob",
      requestTasks = 5,
      fixedParams = Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("v3")),
      contextPath = "search",
      httpMethod = HttpMethod.GET,
      connections = Seq(
        Connection("testConn1.de", 81, useHttps = false, None)
      ),
      resourceDirectives = Seq(judgementResourceDirective),
      requestParameters = requestParams,
      batchByIndex = 0,
      parsingConfig = ParsingConfig(Seq(
        TypedJsonSeqSelector(
          "productIds",
          JsonSelectors.PlainAndRecursiveSelector("product_id", "response", "docs"),
          JsonTypeCast.STRING
        )
      )),
      excludeParamColumns = Seq.empty,
      calculations = Seq(
        Calculations.JudgementsFromResourceIRMetricsCalculations(
          "productIds",
          REQUEST_TEMPLATE_STORAGE_KEY.name,
          "q",
          judgementResourceDirective.resource,
          MetricsCalculation(
            Seq(
              Metric("ndcg_5", MetricFunction(MetricType.NDCG, 5, IRMetricFunctions.ndcgAtK(5)))
            ),
            JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
          ),
          NodeResourceProvider
        )
      ),
      metricNameToAggregationTypeMapping = Map(
        "ndcg_5" -> AggregationType.DOUBLE_AVG
      ),
      taggingConfiguration = taggerConfig,
      wrapUpFunction = None
    )
  }

}
