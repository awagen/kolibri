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
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig.httpModule
import de.awagen.kolibri.base.domain.Connections.{Connection, Host}
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.TaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, ResultRecord}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Functions.{resultRecordToMetricValue, throwableToMetricRowResponse}
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.MetricValue

import scala.concurrent.ExecutionContext
import scala.util.Try

object Flows {

  /**
    * Transform single modifier to ProcessingMessage[RequestTemplate], the actual processing unit
    *
    * @param contextPath - contextPath to set in RequestTemplate
    * @param fixedParams - fixed parameters to set in RequestTemplate
    * @return
    */
  def modifierToProcessingMessage(contextPath: String,
                                  fixedParams: Map[String, Seq[String]]): Modifier[RequestTemplateBuilder] => ProcessingMessage[RequestTemplate] = v1 => {
    val requestTemplateBuilder: RequestTemplateBuilder = RequestTemplatesAndBuilders.getRequestTemplateBuilderSupplier(contextPath, fixedParams).apply()
    val requestTemplate: RequestTemplate = v1.apply(requestTemplateBuilder).build()
    Corn(requestTemplate)
  }

  /**
    * Flow transforming Modifier on RequestTemplateBuilder to ProcessingMessage[RequestTemplate]
    *
    * @param contextPath : contextPath of the request
    * @param fixedParams : mapping of parameter names to possible multiple values
    * @return
    */
  def modifiersToRequestTemplateMessageFlow(contextPath: String,
                                            fixedParams: Map[String, Seq[String]]): Flow[Modifier[RequestTemplateBuilder], ProcessingMessage[RequestTemplate], NotUsed] =
    Flow.fromFunction[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate]](
      modifierToProcessingMessage(contextPath, fixedParams)
    )

  /**
    * This flow only transforms the ProcessingMessage into tuple of HttpRequest and the original ProcessingMessage,
    * as needed by connection pool flow executing the requests
    */
  val processingMsgToRequestTuple: Flow[ProcessingMessage[RequestTemplate], (HttpRequest, ProcessingMessage[RequestTemplate]), NotUsed] =
    Flow.fromFunction(x => (x.data.getRequest, x))

  /**
    * The function to transform single connections to the respective request flow.
    * Depending on useHttps parameter, either constructs https or http connection pool flow
    *
    * @param actorSystem : implicit ActorSystem
    * @return
    */
  def connectionFunc(implicit actorSystem: ActorSystem): Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] = v1 => {
    if (v1.useHttps) {
      httpModule.httpDIModule.getHttpsConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
    }
    else httpModule.httpDIModule.getHttpConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
  }

  /**
   * Definition of the processing flow for parsed request in the form of a WeaklyTypedMap,
   * including the calculations to execute on the parsed data
   * @param processingMessage
   * @param calculations
   * @param requestTemplateStorageKey
   * @param excludeParamsFromMetricRow
   * @param ec
   * @return
   */
  def metricsCalc(processingMessage: ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)],
                  calculations: Seq[Calculation[WeaklyTypedMap[String], Double]],
                  requestTemplateStorageKey: String,
                  excludeParamsFromMetricRow: Seq[String])(implicit ec: ExecutionContext): ProcessingMessage[MetricRow] = {
    val metricRowParams: Map[String, Seq[String]] = Map(processingMessage.data._2.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*)
    processingMessage.data._1 match {
      case e@Left(_) =>
        // need to add paramNames here to set the fail reasons for each
        val allParamNames: Set[String] = calculations.flatMap(x => x.names).toSet
        val metricRow = throwableToMetricRowResponse(e.value, allParamNames, metricRowParams)
        val result: ProcessingMessage[MetricRow] = Corn(metricRow)
        val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
        result.addTags(TagType.AGGREGATION, originalTags)
        result
      case e@Right(_) =>
        // add query parameter
        e.value.put[RequestTemplate](requestTemplateStorageKey, processingMessage.data._2)
        // compute and add single results
        val singleResults: Seq[MetricValue[Double]] = calculations
          .flatMap(x => {
            val values: Seq[ResultRecord[Double]] = x.calculation.apply(e.value)
            values.map(value => {
              resultRecordToMetricValue(value)
            })
          })
        // add all in MetricRow
        val metricRow = MetricRow.emptyForParams(params = metricRowParams)
        metricRow.addFullMetricsSampleAndIncreaseSampleCount(singleResults:_*)
        val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
        Corn(metricRow).withTags(TagType.AGGREGATION, originalTags)
    }
  }

  /**
    * Full flow definition from RequestTemplateBuilderModifier to ProcessingMessage[MetricRow]
    *
    * @param connections                   : connections to be utilized for the requests. Requests will be balanced across all given connections
    * @param contextPath                   : context path to be used for requests
    * @param fixedParams                   : fixed parameters to use for every request
    * @param requestAndParsingFlow         : flow to execute request and parse relevant response parts into WeaklyTypedMap[String]
    * @param excludeParamsFromMetricRow    : the parameters to exclude from single metric entries (e.g useful for aggregations over distinct values, such as queries)
    * @param taggingConfiguration          : configuration to tag the processed elements based on input, parsed value and final result
    * @param requestTemplateStorageKey     : the key under which to store the RequestTemplate in the value map
    * @param mapFutureMetricRowCalculation : definition of metric calculations
    * @param calculations         : additional value calculations
    * @param as                            : implicit ActorSystem
    * @param ec                            : implicit ExecutionContext
    * @return
    */
  def fullProcessingFlow(connections: Seq[Connection],
                         contextPath: String,
                         fixedParams: Map[String, Seq[String]],
                         requestAndParsingFlow: Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)], NotUsed],
                         excludeParamsFromMetricRow: Seq[String],
                         taggingConfiguration: Option[TaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]],
                         requestTemplateStorageKey: String,
                         calculations: Seq[Calculation[WeaklyTypedMap[String], Double]])
                        (implicit as: ActorSystem, ec: ExecutionContext): Flow[RequestTemplateBuilderModifier, ProcessingMessage[MetricRow], NotUsed] = {
    val partialFlow: Flow[RequestTemplateBuilderModifier, ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)], NotUsed] =
      modifiersToRequestTemplateMessageFlow(contextPath, fixedParams)
        .via(Flow.fromFunction(el => {
          taggingConfiguration.foreach(config => config.tagInit(el))
          el
        }))
        .via(requestAndParsingFlow)
        // tagging
        .via(Flow.fromFunction(el => {
          taggingConfiguration.foreach(config => config.tagProcessed(el))
          el
        }))
        partialFlow.via(Flow.fromFunction(processingMsg => metricsCalc(
          processingMessage = processingMsg,
          calculations,
          requestTemplateStorageKey = requestTemplateStorageKey,
          excludeParamsFromMetricRow = excludeParamsFromMetricRow
        )))
      // tagging
      .via(Flow.fromFunction(el => {
        taggingConfiguration.foreach(config => config.tagResult(el))
        el
      }))
  }

}
