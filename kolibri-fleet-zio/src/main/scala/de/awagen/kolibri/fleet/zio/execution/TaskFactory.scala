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
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.{ClassTyped, NamedClassTyped}
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, ResultRecord}
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.RunningValue
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricRowFunctions.throwableToMetricRowResponse
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.fleet.zio.http.client.request.RequestTemplateImplicits.RequestTemplateToZIOHttpRequest
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl.TaskWorker.INITIAL_DATA_KEY
import play.api.libs.json.Json
import zio.{Task, Trace, ZIO, ZLayer}
import zio.http.{Client, DnsResolver}
import zio.http.ZClient.Config
import zio.http.netty.NettyConfig

import java.util.Objects
import java.util.concurrent.TimeUnit

object TaskFactory {

  object RequestJsonAndParseValuesTask {

    val requestTemplateBuilderModifierKey = NamedClassTyped[RequestTemplateBuilderModifier](INITIAL_DATA_KEY)
    val requestTemplateKey = NamedClassTyped[RequestTemplate](REQUEST_TEMPLATE_STORAGE_KEY.name)
  }

  /**
   * Task for executing a request defined by modifier on RequestTemplateBuilder,
   * contextPath and some fixed parameters. Also places the RequestTemplate in the resulting map.
   * Fields are extracted from the response according to the
   * ParsingConfig.
   * TODO: we would need to apply the initial tagger already
   */
  case class RequestJsonAndParseValuesTask(parsingConfig: ParsingConfig,
                                           connectionSupplier: () => Connection,
                                           contextPath: String,
                                           fixedParams: Map[String, Seq[String]],
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
                                           failKeyName: String = "parseFail") extends ZIOTask[ProcessingMessage[WeaklyTypedMap[String]]] {

    import RequestJsonAndParseValuesTask._

    override def prerequisites: Seq[ClassTyped[Any]] = Seq(requestTemplateBuilderModifierKey)

    override def successKey: ClassTyped[ProcessingMessage[WeaklyTypedMap[String]]] = NamedClassTyped[ProcessingMessage[WeaklyTypedMap[String]]](successKeyName)

    override def failKey: ClassTyped[TaskFailType] = NamedClassTyped[TaskFailType](failKeyName)

    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = {
      val requestTemplateBuilder = new RequestTemplateBuilder()
        .withContextPath(contextPath)
        .withHttpMethod("GET")
        .withProtocol("HTTP/1.1")
        .withParams(fixedParams)
      val modifier: RequestTemplateBuilderModifier = map.get(requestTemplateBuilderModifierKey).get
      val requestTemplate: RequestTemplate = modifier.apply(requestTemplateBuilder).build()
      // adding the request template to the map
      val updatedMap = map.put(requestTemplateKey, requestTemplate)._2
      val connection = connectionSupplier()
      val protocolPrefix = if (connection.useHttps) "https" else "http"
      val compute = (for {
        // TODO: connection credentials are not yet taken into account
        res <- requestTemplate.toZIOHttpRequest(s"$protocolPrefix://${connection.host}:${connection.port}")
        data <- res.body.asString.map(x => Json.parse(x))
        parsed <- ZIO.attempt(parsingConfig.jsValueToTypeTaggedMap.apply(data))
      } yield parsed).provide(httpClient)
      compute.map(value => updatedMap.put(successKey, ProcessingMessages.Corn(value))._2)
        .catchAll(throwable => ZIO.succeed(updatedMap.put(failKey, TaskFailType.FailedByException(throwable))._2))
    }
  }

  /**
   * Metrics calculation task.
   * Note that a failure during metric calculation is mapped to a MetricRow with fail entry
   * rather than letting task fail by setting value for the fail-key. The latter only happens
   * in case the mapping to a MetricRow indicating the failure was not successful.
   */
  case class CalculateMetricsTask(requestAndParseSuccessKey: ClassTyped[ProcessingMessage[WeaklyTypedMap[String]]],
                                  calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                  metricNameToAggregationTypeMapping: Map[String, AggregationType],
                                  excludeParamsFromMetricRow: Seq[String],
                                  successKeyName: String = "metricsRow",
                                  failKeyName: String = "metricsCalculationFail") extends ZIOTask[ProcessingMessage[MetricRow]] {
    override def prerequisites: Seq[ClassTyped[Any]] = Seq(requestAndParseSuccessKey)

    override def successKey: ClassTyped[ProcessingMessage[MetricRow]] = NamedClassTyped[ProcessingMessage[MetricRow]](successKeyName)

    override def failKey: ClassTyped[TaskFailType] = NamedClassTyped[TaskFailType](failKeyName)

    private def calculateMetrics(parsedFields: ProcessingMessage[WeaklyTypedMap[String]]): Seq[MetricValue[Any]] = {
      calculations
        .flatMap(x => {
          // ResultRecord is only combination of name and Either[Seq[ComputeFailReason], T]
          val values: Seq[ResultRecord[Any]] = x.calculation.apply(parsedFields.data)
          // we use all result records for which we have a AggregationType mapping
          // TODO: allow more than one aggregation type mapping per result record name
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


    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = {
      val parsedFieldsOpt: Option[ProcessingMessage[WeaklyTypedMap[String]]] = map.get(requestAndParseSuccessKey)
      val requestTemplateOpt: Option[RequestTemplate] = parsedFieldsOpt.flatMap(x => x.data.get[RequestTemplate](RequestJsonAndParseValuesTask.requestTemplateKey.name))
      val metricRowParamsOpt: Option[Map[String, Seq[String]]] = requestTemplateOpt.map(x => Map(x.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _ *))
      val compute = for {
        processingMessageResult <- ZIO.attempt({
          val parsedFields = parsedFieldsOpt.get
          val metricRowParams = metricRowParamsOpt.get
          val singleResults: Seq[MetricValue[Any]] = calculateMetrics(parsedFields)
          // create metric row from single metric values
          val metricRow = metricValuesToMetricRow(singleResults, metricRowParams)
          // take over the tags from the original processing message
          val originalTags: Set[Tag] = parsedFields.getTagsForType(TagType.AGGREGATION)
          ProcessingMessages.Corn(metricRow).withTags(TagType.AGGREGATION, originalTags)
        })
      } yield processingMessageResult
      compute.map(value => map.put(successKey, value)._2)
        // try to map the throwable to a MetricRow result containing info about the failure
        .catchAll(throwable => ZIO.attempt {
          // need to add paramNames here to set the fail reasons for each
          val allParamNames: Set[String] = calculations.flatMap(x => x.names).toSet
          // check if we know rhe right running value mapping, otherwise need to ignore the metric
          val validParamNameEmptyRunningValueMap: Map[String, RunningValue[_]] = allParamNames.map(name => {
            (name, metricNameToAggregationTypeMapping.get(name)
              .map(aggType => aggType.emptyRunningValueSupplier.apply())
              .orNull)
          })
            .filter(x => Objects.nonNull(x._2)).toMap
          val metricRowParams = metricRowParamsOpt.get
          val metricRow = throwableToMetricRowResponse(throwable, validParamNameEmptyRunningValueMap, metricRowParams)
          val result: ProcessingMessage[MetricRow] = ProcessingMessages.Corn(metricRow)
          val originalTags: Set[Tag] = parsedFieldsOpt.get.getTagsForType(TagType.AGGREGATION)
          result.addTags(TagType.AGGREGATION, originalTags)
          map.put(successKey, result)._2
        } // if the mapping to a MetricRow object with the failure information fails,
          // we declare the task as failed
          .catchAll(throwable => ZIO.succeed(map.put(failKey, TaskFailType.FailedByException(throwable))._2))
        )
    }
  }

}
