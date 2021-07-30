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
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import de.awagen.kolibri.base.actors.flows.GenericRequestFlows.{Host, getHttpConnectionPoolFlow, getHttpsConnectionPoolFlow}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig.{config, logger}
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.RequestProcessingFlows
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.MetricValue

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Flows {


  /**
    * Transform single modifier to ProcessingMessage[RequestTemplate], the actual processing unit
    *
    * @param fixedParams
    * @param tagger
    * @return
    */
  def modifierToProcessingMessage(contextPath: String,
                                  fixedParams: Map[String, Seq[String]],
                                  tagger: ProcessingMessage[RequestTemplate] => ProcessingMessage[RequestTemplate]): Modifier[RequestTemplateBuilder] => ProcessingMessage[RequestTemplate] = v1 => {
    val requestTemplateBuilder: RequestTemplateBuilder = RequestTemplatesAndBuilders.getRequestTemplateBuilderSupplier(contextPath, fixedParams).apply()
    val requestTemplate: RequestTemplate = v1.apply(requestTemplateBuilder).build()
    tagger.apply(Corn(requestTemplate))
  }

  /**
    * Flow transforming Modifier on RequestTemplateBuilder to ProcessingMessage[RequestTemplate]
    *
    * @param contextPath : contextPath of the request
    * @param fixedParams : mapping of parameter names to possible multiple values
    * @param tagger      : function adding tags to ProcessingMessage[RequestTemplate]
    * @return
    */
  def processingFlow(contextPath: String,
                     fixedParams: Map[String, Seq[String]],
                     tagger: ProcessingMessage[RequestTemplate] => ProcessingMessage[RequestTemplate]): Flow[Modifier[RequestTemplateBuilder], ProcessingMessage[RequestTemplate], NotUsed] =
    Flow.fromFunction[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate]](
      modifierToProcessingMessage(contextPath, fixedParams, tagger)
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
    * Execution graph from ProcessingMessage[RequestTemplate] to ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)],
    * where the Either either holds Throwable if request + parsing failed or the Seq of productIds
    *
    * @param connections
    * @param queryParam
    * @param groupId
    * @param throughputActor
    * @param as
    * @param ec
    * @return
    */
  def requestingFlow(connections: Seq[Connection],
                     queryParam: String,
                     groupId: String,
                     responseParsingFunc: HttpResponse => Future[Either[Throwable, Seq[String]]],
                     throughputActor: Option[ActorRef])(implicit as: ActorSystem, ec: ExecutionContext): Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)]], NotUsed] =
    RequestProcessingFlows.requestAndParsingFlow(
      throughputActor,
      queryParam,
      groupId,
      connections,
      connectionFunc,
      responseParsingFunc
    )

  /**
    * Helper function to generate a MetricRow result from a throwable.
    *
    * @param e : The throwable to map to MetricRow
    * @return
    */
  def throwableToMetricRowResponse(e: Throwable): MetricRow = {
    val metricRow = MetricRow(params = Map.empty[String, Seq[String]], metrics = Map.empty[String, MetricValue[Double]])
    val failMetricName: String = TaskFailType.FailedByException(e).toString
    val addValue: MetricValue[Double] = MetricValue.createAvgFailSample(metricName = failMetricName, failMap = Map[ComputeFailReason, Int](ComputeFailReason(s"exception:${e.getClass.toString}") -> 1))
    metricRow.addMetric(addValue)
  }

  def metricsCalc(processingMessage: ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)],
                  judgementProviderFactory: JudgementProviderFactory[Double],
                  metricsCalculation: MetricsCalculation,
                  excludeParamsFromMetricRow: Seq[String])(implicit ec: ExecutionContext): Future[ProcessingMessage[MetricRow]] = {
    processingMessage.data._1 match {
      case e@Left(_) =>
        val metricRow = throwableToMetricRowResponse(e.value)
        val result: ProcessingMessage[MetricRow] = Corn(metricRow)
        val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
        result.addTags(TagType.AGGREGATION, originalTags)
        Future.successful(result)
      case e@Right(_) =>
        judgementProviderFactory.getJudgements.future
          .map(y => {
            val judgements: Seq[Option[Double]] = y.retrieveJudgements(processingMessage.data._2.parameters("q").head, e.value)
            logger.debug(s"retrieved judgements: $judgements")
            val metricRow: MetricRow = metricsCalculation.calculateAll(immutable.Map(processingMessage.data._2.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*), judgements)
            logger.debug(s"calculated metrics: $metricRow")
            val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
            Corn(metricRow).withTags(AGGREGATION, originalTags)
          })
          .recover(throwable => {
            logger.warn(s"failed retrieving judgements: $throwable")
            val metricRow = throwableToMetricRowResponse(throwable)
            val result: ProcessingMessage[MetricRow] = Corn(metricRow)
            val originalTags: Set[Tag] = processingMessage.getTagsForType(TagType.AGGREGATION)
            result.withTags(TagType.AGGREGATION, originalTags)
          })
    }
  }

  /**
    * Full flow definition from RequestTemplateBuilderModifier to ProcessingMessage[MetricRow]
    *
    * @param throughputActor          : optional ActorRef to receive throughput information
    * @param connections              : connections to be utilized for the requests. Requests will be balanced across all given connections
    * @param contextPath              : context path to be used for requests
    * @param fixedParams              : fixed parameters to use for every request
    * @param queryParam               : the parameter name of the query parameter
    * @param groupId                  : group id, usually the same as the jobId
    * @param requestTagger            : the tagger to tag ProcessingMessage[RequestTemplate]
    * @param responseParsingFunc      : the parsing function to map HttpResponse to Future of either Throwable (in case of error)
    *                                 or Seq[String] giving the productIds in order (in case of successful execution)
    * @param judgementProviderFactory : the factory providing judgement provider, which is used to retrieve judgements for
    *                                 the productIds
    * @param metricsCalculation       : definition of metrics to calculate and how to handle judgements (validations of judgements and
    *                                 handling of missing values)
    * @param as                       : implicit ActorSystem
    * @param ec                       : implicit ExecutionContext
    * @param timeout                  : implicit timeout for the requests
    * @return
    */
  def fullProcessingFlow(throughputActor: Option[ActorRef],
                         connections: Seq[Connection],
                         contextPath: String,
                         fixedParams: Map[String, Seq[String]],
                         queryParam: String,
                         excludeParamsFromMetricRow: Seq[String],
                         groupId: String,
                         requestTagger: ProcessingMessage[RequestTemplate] => ProcessingMessage[RequestTemplate],
                         responseParsingFunc: HttpResponse => Future[Either[Throwable, Seq[String]]],
                         judgementProviderFactory: JudgementProviderFactory[Double],
                         metricsCalculation: MetricsCalculation)
                        (implicit as: ActorSystem, ec: ExecutionContext): Flow[RequestTemplateBuilderModifier, ProcessingMessage[MetricRow], NotUsed] = {
    val partialFlow: Flow[RequestTemplateBuilderModifier, ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], NotUsed] =
      processingFlow(contextPath, fixedParams, requestTagger)
        .via(Flow.fromGraph(requestingFlow(
          connections = connections,
          queryParam = queryParam,
          groupId = groupId,
          responseParsingFunc = responseParsingFunc,
          throughputActor = throughputActor)))
    partialFlow.mapAsyncUnordered[ProcessingMessage[MetricRow]](config.requestParallelism)(x => {
      metricsCalc(processingMessage = x,
        judgementProviderFactory = judgementProviderFactory,
        metricsCalculation = metricsCalculation,
        excludeParamsFromMetricRow = excludeParamsFromMetricRow)
    })
  }

}
