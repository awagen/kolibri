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

import de.awagen.kolibri.datatypes.immutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormatHelper.getMetricValueFromTypeAndSample
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.types.{ClassTyped, NamedClassTyped}
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, ResultRecord}
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.RunningValue
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.domain.jobdefinitions.provider.Credentials
import de.awagen.kolibri.definitions.http.HttpMethod
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricRowFunctions.throwableToMetricRowResponse
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.execution.JobMessagesImplicits.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.fleet.zio.http.client.request.RequestTemplateImplicits.RequestTemplateToZIOHttpRequest
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker.INITIAL_DATA_KEY
import play.api.libs.json.Json
import zio.http.ZClient.Config
import zio.http.netty.NettyConfig
import zio.http.{Client, DnsResolver}
import zio.{Task, Trace, ZIO, ZLayer}

import java.util.Objects
import java.util.concurrent.TimeUnit

object TaskFactory {

  object RequestJsonAndParseValuesTask {

    val requestTemplateBuilderModifierKey = NamedClassTyped[RequestTemplateBuilderModifier](INITIAL_DATA_KEY)
    val requestTemplateKey = NamedClassTyped[RequestTemplate](REQUEST_TEMPLATE_STORAGE_KEY.name)

    val HTTPS_URL_PREFIX = "https"
    val HTTP_URL_PREFIX = "http"
    val HTTP_1_1_PROTOCOL_ID = "HTTP/1.1"

  }

  /**
   * Task for executing a request defined by modifier on RequestTemplateBuilder,
   * contextPath and some fixed parameters. Also places the RequestTemplate in the resulting map.
   * Fields are extracted from the response according to the
   * ParsingConfig.
   */
  case class RequestJsonAndParseValuesTask(parsingConfig: ParsingConfig,
                                           taggingConfig: RequestAndParsingResultTaggerConfig,
                                           connectionSupplier: () => Connection,
                                           contextPath: String,
                                           fixedParams: Map[String, Seq[String]],
                                           httpMethod: String = HttpMethod.GET.toString,
                                           // default value of http client utilizes dynamic connection pool
                                           httpClient: ZLayer[Any, Throwable, Client] = {
                                             implicit val trace: Trace = Trace.empty
                                             (
                                               ZLayer.succeed(Config.default.withDynamicConnectionPool(10, 100, zio.Duration(10, TimeUnit.MINUTES))) ++
                                                 ZLayer.succeed(NettyConfig.default) ++
                                                 DnsResolver.default
                                               ) >>> Client.live
                                           },
                                           successKeyName: String = "parsedValueMap",
                                           failKeyName: String = "parseFail") extends ZIOTask[WeaklyTypedMap[String]] {

    import RequestJsonAndParseValuesTask._

    override def prerequisiteKeys: Seq[ClassTyped[Any]] = Seq(requestTemplateBuilderModifierKey)

    override def successKey: ClassTyped[ProcessingMessage[WeaklyTypedMap[String]]] = NamedClassTyped[ProcessingMessage[WeaklyTypedMap[String]]](successKeyName)

    override def failKey: ClassTyped[ProcessingMessage[TaskFailType]] = NamedClassTyped[ProcessingMessage[TaskFailType]](failKeyName)

    private def initRequestTemplateBuilder: RequestTemplateBuilder = new RequestTemplateBuilder()
      .withContextPath(contextPath)
      .withHttpMethod(httpMethod)
      .withProtocol(HTTP_1_1_PROTOCOL_ID)
      .withParams(fixedParams)

    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = {
      // compose the request template
      val modifier: RequestTemplateBuilderModifier = map.get(requestTemplateBuilderModifierKey).get
      val requestTemplate: RequestTemplate = modifier.apply(initRequestTemplateBuilder).build()
      // wrap in processing message and tag
      val requestTemplateProcessingMessage: ProcessingMessages.Corn[RequestTemplate] = ProcessingMessages.Corn(requestTemplate)
      taggingConfig.requestTagger.apply(requestTemplateProcessingMessage)
      // prepare the request and parsing effect
      val connection = connectionSupplier.apply()
      val basicAuthOpt: Option[Credentials] = connection.credentialsProvider.map(x => x.getCredentials)
      val protocolPrefix = if (connection.useHttps) HTTPS_URL_PREFIX else HTTP_URL_PREFIX
      val requestAndParseEffect: ZIO[Any, Throwable, WeaklyTypedMap[String]] = (for {
        res <- requestTemplate.toZIOHttpRequest(
          s"$protocolPrefix://${connection.host}:${connection.port}",
          basicAuthOpt
        )
        data <- res.body.asString.map(x => Json.parse(x))
        parsed <- ZIO.attempt(parsingConfig.jsValueToTypeTaggedMap.apply(data))
      } yield parsed)
        .retryN(AppProperties.config.maxRetriesPerBatchTask)
        .provide(httpClient)
      requestAndParseEffect.either.map(value => {
        // wrap in processing message and apply tagger
        val parseResultMessage = requestTemplateProcessingMessage.map(_ => (value, requestTemplate))
        taggingConfig.parsingResultTagger.apply(parseResultMessage)
        parseResultMessage.data match {
          case (Left(throwable), _) =>
            val errorResult = ProcessingMessages.BadCorn(TaskFailType.FailedByException(throwable))
            errorResult.takeOverTags(parseResultMessage)
            map.put(failKey, errorResult)._2
          case (Right(parseResultMap), _) =>
            parseResultMap.put(REQUEST_TEMPLATE_STORAGE_KEY.name, requestTemplate)
            val resultMsg = ProcessingMessages.Corn(parseResultMap)
            resultMsg.takeOverTags(parseResultMessage)
            map.put(successKey, resultMsg)._2
        }
      })
    }
  }

  /**
   * Metrics calculation task.
   * Note that a failure during metric calculation is mapped to a MetricRow with fail entry
   * rather than letting task fail by setting value for the fail-key. The latter only happens
   * in case the mapping to a MetricRow indicating the failure was not successful.
   *
   * @param requestAndParseSuccessKey          - The key in the type tagged map where the parsed results are stored
   * @param requestTemplateKey                 - key within the parsed results where the requestTemplate is stored
   * @param calculations                       - calculations to be executed based on the parsed data
   * @param metricNameToAggregationTypeMapping - for each metric name mapping to its data type to ensure correct handling of aggregations and such
   * @param excludeParamsFromMetricRow         - parameters to be excluded from the final result data structure.
   * @param tagger                             - tagger for the result. To provide tag to be taken into account for per-tag aggregations, add tag with type AGGREGATION
   *                                           (NOTE: the tags from previous steps are taken over, so if e.g tagging by query-param happened before, no need to do it again here)
   * @param successKeyName                     - the key of the processing message containing successfully calculated result
   * @param failKeyName                        - the name of the processing message containing the fail reason
   */
  case class CalculateMetricsTask(requestAndParseSuccessKey: ClassTyped[ProcessingMessage[WeaklyTypedMap[String]]],
                                  requestTemplateKey: String,
                                  calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                  metricNameToAggregationTypeMapping: Map[String, AggregationType],
                                  excludeParamsFromMetricRow: Seq[String],
                                  tagger: ProcessingMessage[MetricRow] => ProcessingMessage[MetricRow] = identity,
                                  successKeyName: String = "metricsRow",
                                  failKeyName: String = "metricsCalculationFail") extends ZIOTask[MetricRow] {
    override def prerequisiteKeys: Seq[ClassTyped[Any]] = Seq(requestAndParseSuccessKey)

    override def successKey: ClassTyped[ProcessingMessage[MetricRow]] = NamedClassTyped[ProcessingMessage[MetricRow]](successKeyName)

    override def failKey: ClassTyped[ProcessingMessage[TaskFailType]] = NamedClassTyped[ProcessingMessage[TaskFailType]](failKeyName)

    private def calculateMetrics(parsedFields: ProcessingMessage[WeaklyTypedMap[String]]): Seq[MetricValue[Any]] = {
      calculations
        .flatMap(x => {
          // ResultRecord is only combination of name and Either[Seq[ComputeFailReason], T]
          val values: Seq[ResultRecord[Any]] = x.calculation.apply(parsedFields.data)
          // we use all result records for which we have a AggregationType mapping
          values.map(value => {
            val metricAggregationType: Option[AggregationType] = metricNameToAggregationTypeMapping.get(value.name)
            metricAggregationType.map(aggregationType => {
              getMetricValueFromTypeAndSample(aggregationType, value)
            }).orNull
          })
            .filter(x => Objects.nonNull(x))
        })
    }

    private def metricValuesToMetricRow(metricValues: Seq[MetricValue[_]],
                                        metricRowParams: Map[String, Seq[String]]): MetricRow = {
      MetricRow.emptyForParams(params = metricRowParams)
        .addFullMetricsSampleAndIncreaseSampleCount(metricValues: _*)
    }

    /**
     * Note that some calculations pick data out of the WeaklyTypedMap, thus make sure all those keys needed
     * are filled with data (see respective metrics)
     */
    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = {
      val parsedFieldsOpt: Option[ProcessingMessage[WeaklyTypedMap[String]]] = map.get(requestAndParseSuccessKey)
      val requestTemplateOpt: Option[RequestTemplate] = parsedFieldsOpt.flatMap(x => x.data.get[RequestTemplate](requestTemplateKey))
      val metricRowParamsOpt: Option[Map[String, Seq[String]]] = requestTemplateOpt.map(x => Map(x.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _ *))
      val computeResult = for {
        parsedFields <- ZIO.attempt(parsedFieldsOpt.get)
        currentTags <- ZIO.attempt(parsedFields.getTagsForType(TagType.AGGREGATION))
        metricRowParams <- ZIO.attempt(metricRowParamsOpt.get)
        singleResults <- ZIO.attempt(calculateMetrics(parsedFields))
        _ <- ZIO.logDebug(s"single results: $singleResults")
        metricRow <- ZIO.attempt(metricValuesToMetricRow(singleResults, metricRowParams))
        _ <- ZIO.logDebug(s"computed metric values for aggregation tags '$currentTags': '$metricRow'")
        processingMessageResult <- ZIO.attempt(tagger.apply(ProcessingMessages.Corn(metricRow).withTags(TagType.AGGREGATION, currentTags)))
      } yield processingMessageResult
      computeResult
        .tap(result => ZIO.logDebug(s"calculated metrics: ${result.data}"))
        .retryN(AppProperties.config.maxRetriesPerBatchTask)
        .map(value => map.put(successKey, value)._2)
        // try to map the throwable to a MetricRow result containing info about the failure
        .catchAll(throwable =>
          ZIO.logError(s"metric calculation failed, exception:\n${throwable.getStackTrace.mkString("\n")}") *>
            wrapThrowableInMetricRowResult(map, throwable, calculations, metricRowParamsOpt.get)
              // if the mapping to a MetricRow object with the failure information fails,
              // we declare the task as failed
              .catchAll(throwable =>
                ZIO.logWarning(s"metric calculation failed, exception:\n$throwable") *>
                  ZIO.succeed(map.put(failKey, ProcessingMessages.BadCorn(TaskFailType.FailedByException(throwable)))._2)
              )
        )
    }


    private def wrapThrowableInMetricRowResult(map: TypeTaggedMap,
                                               throwable: Throwable,
                                               calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                               metricRowParams: Map[String, Seq[String]]): Task[TypeTaggedMap] = {
      ZIO.attempt {
        val parsedFieldsOpt: Option[ProcessingMessage[WeaklyTypedMap[String]]] = map.get(requestAndParseSuccessKey)
        // need to add paramNames here to set the fail reasons for each
        val allParamNames: Set[String] = calculations.flatMap(x => x.names).toSet
        // check if we know rhe right running value mapping, otherwise need to ignore the metric
        val validParamNameEmptyRunningValueMap: Map[String, RunningValue[_]] = allParamNames.map(name => {
          (name, metricNameToAggregationTypeMapping.get(name)
            .map(aggType => aggType.emptyRunningValueSupplier.apply())
            .orNull)
        })
          .filter(x => Objects.nonNull(x._2)).toMap
        val metricRow = throwableToMetricRowResponse(throwable, validParamNameEmptyRunningValueMap, metricRowParams)
        val result: ProcessingMessage[MetricRow] = tagger.apply(
          ProcessingMessages.Corn(metricRow)
            .withTags(
              TagType.AGGREGATION,
              parsedFieldsOpt.map(x => x.getTagsForType(TagType.AGGREGATION)).getOrElse(Set.empty)
            ))
        map.put(successKey, result)._2
      }
    }

  }


}
