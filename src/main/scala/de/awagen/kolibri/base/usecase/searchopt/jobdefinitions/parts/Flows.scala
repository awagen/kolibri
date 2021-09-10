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
import akka.stream.{FlowShape, Graph}
import de.awagen.kolibri.base.actors.flows.GenericFlows.{Host, getHttpConnectionPoolFlow, getHttpsConnectionPoolFlow}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppProperties.config
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.TaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.RequestProcessingFlows
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, CalculationResult, FutureCalculation}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Functions.{resultEitherToMetricRowResponse, throwableToMetricRowResponse}
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag

import scala.concurrent.{ExecutionContext, Future}
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
  def processingFlow(contextPath: String,
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
      getHttpsConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
    }
    else getHttpConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
  }

  /**
    * Execution graph from ProcessingMessage[RequestTemplate] to ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)],
    * where the Either either holds Throwable if request + parsing failed or the Map containing
    * properties parsed from the response
    *
    * @param connections         - connections to balance requests on
    * @param responseParsingFunc :
    * @param as
    * @param ec
    * @return
    */
  def requestingFlow(connections: Seq[Connection],
                     responseParsingFunc: HttpResponse => Future[Either[Throwable, WeaklyTypedMap[String]]])(implicit as: ActorSystem, ec: ExecutionContext): Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)]], NotUsed] =
    RequestProcessingFlows.requestAndParsingFlow(
      connections,
      connectionFunc,
      responseParsingFunc
    )

  def metricsCalc(processingMessage: ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)],
                  mapFutureMetricRowCalculation: FutureCalculation[WeaklyTypedMap[String], MetricRow],
                  singleMapCalculations: Seq[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]],
                  requestTemplateStorageKey: String,
                  excludeParamsFromMetricRow: Seq[String])(implicit ec: ExecutionContext): Future[ProcessingMessage[MetricRow]] = {
    processingMessage.data._1 match {
      case e@Left(_) =>
        // need to add paramNames here to set the fail reasons for each
        val allParamNames: Set[String] = singleMapCalculations.map(x => x.name).toSet ++ mapFutureMetricRowCalculation.calculationValueNames
        val metricRow = throwableToMetricRowResponse(e.value, allParamNames)
        val result: ProcessingMessage[MetricRow] = Corn(metricRow)
        val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
        result.addTags(TagType.AGGREGATION, originalTags)
        Future.successful(result)
      case e@Right(_) =>
        // add query parameter
        e.value.put[RequestTemplate](requestTemplateStorageKey, processingMessage.data._2)
        // apply calculations resulting in MetricRow
        val rowResultFuture: Future[MetricRow] = mapFutureMetricRowCalculation.apply(e.value)
        // compute and add single results
        val singleResults: Seq[MetricRow] = singleMapCalculations
          .map(x => {
            val value = x.apply(e.value)
            resultEitherToMetricRowResponse(x.name, value)
          })
        val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
        val combinedResultFuture: Future[MetricRow] = rowResultFuture.map(resRow => {
          var result: MetricRow = resRow
          singleResults.foreach(single => {
            result = result.addRecord(single)
          })
          result
        })
        combinedResultFuture.map(res => Corn(res).withTags(TagType.AGGREGATION, originalTags))
    }
  }

  /**
    * Full flow definition from RequestTemplateBuilderModifier to ProcessingMessage[MetricRow]
    *
    * @param connections                   : connections to be utilized for the requests. Requests will be balanced across all given connections
    * @param contextPath                   : context path to be used for requests
    * @param fixedParams                   : fixed parameters to use for every request
    * @param excludeParamsFromMetricRow    : the parameters to exclude from single metric entries (e.g useful for aggregations over distinct values, such as queries)
    * @param taggingConfiguration          : configuration to tag the processed elements based on input, parsed value and final result
    * @param responseParsingFunc           : the parsing function to map HttpResponse to Future of either Throwable (in case of error)
    *                                      or Seq[String] giving the productIds in order (in case of successful execution)
    * @param requestTemplateStorageKey     : the key under which to store the RequestTemplate in the value map
    * @param mapFutureMetricRowCalculation : definition of metric calculations
    * @param singleMapCalculations         : additional value calculations
    * @param as                            : implicit ActorSystem
    * @param ec                            : implicit ExecutionContext
    * @return
    */
  def fullProcessingFlow(connections: Seq[Connection],
                         contextPath: String,
                         fixedParams: Map[String, Seq[String]],
                         excludeParamsFromMetricRow: Seq[String],
                         taggingConfiguration: Option[TaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]],
                         responseParsingFunc: HttpResponse => Future[Either[Throwable, WeaklyTypedMap[String]]],
                         requestTemplateStorageKey: String,
                         mapFutureMetricRowCalculation: FutureCalculation[WeaklyTypedMap[String], MetricRow],
                         singleMapCalculations: Seq[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]])
                        (implicit as: ActorSystem, ec: ExecutionContext): Flow[RequestTemplateBuilderModifier, ProcessingMessage[MetricRow], NotUsed] = {
    val partialFlow: Flow[RequestTemplateBuilderModifier, ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)], NotUsed] =
      processingFlow(contextPath, fixedParams)
        .via(Flow.fromFunction(el => {
          taggingConfiguration.foreach(config => config.tagInit(el))
          el
        }))
        .via(Flow.fromGraph(requestingFlow(
          connections = connections,
          responseParsingFunc = responseParsingFunc)))
        // tagging
        .via(Flow.fromFunction(el => {
          taggingConfiguration.foreach(config => config.tagProcessed(el))
          el
        }))
    partialFlow.mapAsyncUnordered[ProcessingMessage[MetricRow]](config.requestParallelism)(x => {
      metricsCalc(processingMessage = x,
        mapFutureMetricRowCalculation,
        singleMapCalculations,
        requestTemplateStorageKey = requestTemplateStorageKey,
        excludeParamsFromMetricRow = excludeParamsFromMetricRow)
    })
      // tagging
      .via(Flow.fromFunction(el => {
        taggingConfiguration.foreach(config => config.tagResult(el))
        el
      }))
  }

}
