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
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
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

  }

  /**
   * Task for executing a request defined by modifier on RequestTemplateBuilder,
   * contextPath and some fixed parameters.
   * Fields are extracted from the response according to the
   * ParsingConfig.
   * TODO: we would need to apply the initial tagger already
   * TODO: might consider mapping a throwable to a MetricRow with fail values (see metricsCalc function
   * in the akka variant) instead of letting the calculation fail as is done in TaskWorker if
   * fail key is detected
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
                                           failKeyName: String = "parseFail") extends ZIOTask[ProcessingMessage[(WeaklyTypedMap[String], RequestTemplate)]] {

    import RequestJsonAndParseValuesTask._

    override def prerequisites: Seq[ClassTyped[Any]] = Seq(requestTemplateBuilderModifierKey)

    override def successKey: ClassTyped[ProcessingMessage[(WeaklyTypedMap[String], RequestTemplate)]] = NamedClassTyped[ProcessingMessage[(WeaklyTypedMap[String], RequestTemplate)]](successKeyName)

    override def failKey: ClassTyped[TaskFailType] = NamedClassTyped[TaskFailType](failKeyName)

    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = {
      val requestTemplateBuilder = new RequestTemplateBuilder()
        .withContextPath(contextPath)
        .withHttpMethod("GET")
        .withProtocol("HTTP/1.1")
        .withParams(fixedParams)
      val modifier: RequestTemplateBuilderModifier = map.get(requestTemplateBuilderModifierKey).get
      val requestTemplate: RequestTemplate = modifier.apply(requestTemplateBuilder).build()
      val connection = connectionSupplier()
      val protocolPrefix = if (connection.useHttps) "https" else "http"
      val compute = (for {
        // TODO: connection credentials are not yet taken into account
        res <- requestTemplate.toZIOHttpRequest(s"$protocolPrefix://${connection.host}:${connection.port}")
        data <- res.body.asString.map(x => Json.parse(x))
        parsed <- ZIO.attempt(parsingConfig.jsValueToTypeTaggedMap.apply(data))
      } yield parsed).provide(httpClient)
      compute.map(value => map.put(successKey, ProcessingMessages.Corn((value, requestTemplate)))._2)
        .catchAll(throwable => ZIO.succeed(map.put(failKey, TaskFailType.FailedByException(throwable))._2))
    }
  }

  case class CalculateMetricsTask(requestAndParseSuccessKey: ClassTyped[ProcessingMessage[(WeaklyTypedMap[String], RequestTemplate)]],
                                  calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                  metricNameToAggregationTypeMapping: Map[String, AggregationType],
                                  excludeParamsFromMetricRow: Seq[String],
                                  successKeyName: String = "metricsRow",
                                  failKeyName: String = "metricsCalculationFail") extends ZIOTask[ProcessingMessage[MetricRow]] {
    override def prerequisites: Seq[ClassTyped[Any]] = Seq(requestAndParseSuccessKey)

    override def successKey: ClassTyped[ProcessingMessage[MetricRow]] = NamedClassTyped[ProcessingMessage[MetricRow]](successKeyName)

    override def failKey: ClassTyped[TaskFailType] = NamedClassTyped[TaskFailType](failKeyName)

    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = {
      val compute = for {
        processingMessageResult <- ZIO.attempt({
          val parsedFields: ProcessingMessage[(WeaklyTypedMap[String], RequestTemplate)] = map.get(requestAndParseSuccessKey).get
          val metricRowParams: Map[String, Seq[String]] = Map(parsedFields.data._2.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*)
          val singleResults: Seq[MetricValue[Any]] = calculations
            .flatMap(x => {
              // ResultRecord is only combination of name and Either[Seq[ComputeFailReason], T]
              val values: Seq[ResultRecord[Any]] = x.calculation.apply(parsedFields.data._1)
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
          // add all in MetricRow
          var metricRow = MetricRow.emptyForParams(params = metricRowParams)
          metricRow = metricRow.addFullMetricsSampleAndIncreaseSampleCount(singleResults: _*)
          val originalTags: Set[Tag] = parsedFields.getTagsForType(TagType.AGGREGATION)
          ProcessingMessages.Corn(metricRow).withTags(TagType.AGGREGATION, originalTags)
        })
      } yield processingMessageResult
      compute.map(value => map.put(successKey, value)._2)
        .catchAll(throwable => ZIO.succeed(map.put(failKey, TaskFailType.FailedByException(throwable))._2))
    }
  }

}
