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


package de.awagen.kolibri.base.usecase.searchopt.http.client.flows

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream._
import akka.{Done, NotUsed}
import de.awagen.kolibri.base.actors.flows.GenericRequestFlows
import de.awagen.kolibri.base.actors.flows.GenericRequestFlows.flowThroughActorMeter
import de.awagen.kolibri.base.actors.tracking.ThroughputActor.AddForStage
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation
import de.awagen.kolibri.base.usecase.searchopt.processing.plan.PlanProvider
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.tagging.TagImplicits.{logger, _}
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.{ParameterMultiValueTag, StringTag}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object RequestProcessingFlows {


  /**
    * Create a graph that is closed and thus can be run.
    * It uses Balance, meaning each request just goes to one of the given connection pool. If just one connection is
    * given, all will go that way.
    * Connect processing of the parsed result by passing the respective sink that takes care of it
    *
    * @param connections              Seq[Connection]: providing connection information for distinct hosts to send requests to
    * @param requestTemplateGenerator IndexedGenerator[ProcessingMessage[RequestTemplate]]: providing the wrapped RequestTemplates defining the requests to execute
    * @param queryParam               String - the parameter providing the query
    * @param groupId                  String - groupId
    * @param connectionToFlowFunc     Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] - function from connection to processing flow
    * @param parsingFunc              HttpResponse => Future[Either[Throwable, T]] - parsing function for responses
    * @param throughputActor          ActorRef - actor to which throughput information is sent
    * @param sink                     Sink[(Either[Throwable, T], ProcessingMessage[RequestTemplate]), Future[Done]] - Sink for the parsed result
    * @param as                       implicit ActorSystem
    * @param mat                      implicit Materializer
    * @param ec                       implicit ExecutionContext
    * @param ac                       implicit ActorContext
    * @return
    */
  def createBalancedRunnableSearchResponseHandlingGraph[T](connections: Seq[Connection],
                                                           requestTemplateGenerator: IndexedGenerator[ProcessingMessage[RequestTemplate]],
                                                           queryParam: String,
                                                           groupId: String,
                                                           connectionToFlowFunc: Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _],
                                                           parsingFunc: HttpResponse => Future[Either[Throwable, T]],
                                                           throughputActor: ActorRef,
                                                           sink: Sink[ProcessingMessage[(Either[Throwable, T], RequestTemplate)], Future[Done]])
                                                          (implicit as: ActorSystem,
                                                           mat: Materializer,
                                                           ec: ExecutionContext,
                                                           ac: ActorContext): RunnableGraph[Future[Done]] = {
    RunnableGraph.fromGraph(GraphDSL.create(sink) {
      implicit b =>
        sinkInst =>
          import GraphDSL.Implicits._
          // creating the elements that will be part of the sink
          val source: SourceShape[ProcessingMessage[RequestTemplate]] = b.add(Source.fromIterator[ProcessingMessage[RequestTemplate]](() => requestTemplateGenerator.iterator))
          val flow: FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)]] = b.add(requestAndParsingFlow[T](throughputActor, queryParam, groupId, connections, connectionToFlowFunc, parsingFunc))
          // creating the graph
          source ~> flow ~> sinkInst
          ClosedShape
    })
  }

  /**
    * Return Graph[FlowShape] with flow of processing single ProcessingMessage[RequestTemplate] elements
    *
    * @param throughputActor      ActorRef - actor to which throughput information is sent
    * @param queryParam           String - the parameter providing the query
    * @param groupId              String - groupId
    * @param connections          Seq[Connection]: providing connection information for distinct hosts to send requests to
    * @param connectionToFlowFunc Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] - function from connection to processing flow
    * @param parsingFunc          HttpResponse => Future[Either[Throwable, T]] - parsing function for responses
    * @param as                   implicit ActorSystem
    * @param mat                  implicit Materializer
    * @param ec                   implicit ExecutionContext
    * @param ac                   implicit ActorContext
    * @tparam T type of the parsed result
    * @return Graph[FlowShape] providing the streaming requesting and parsing logic
    */
  def requestAndParsingFlow[T](throughputActor: ActorRef,
                               queryParam: String,
                               groupId: String,
                               connections: Seq[Connection],
                               connectionToFlowFunc: Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _],
                               parsingFunc: HttpResponse => Future[Either[Throwable, T]]
                              )(implicit as: ActorSystem,
                                mat: Materializer,
                                ec: ExecutionContext,
                                ac: ActorContext): Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)]], NotUsed] = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    // now define the single elements
    val initThroughputFlow = b.add(Flow.fromFunction[ProcessingMessage[RequestTemplate], ProcessingMessage[RequestTemplate]](x => {
      throughputActor ! AddForStage("fromSource")
      // tag with the varied parameters to allow grouping of requests (e.g for aggregation of metrics and such)
      // exclude query parameter
      try {
        val filteredParams: Map[String, Seq[String]] = x.data.parameters.filter(y => y._1 != queryParam)
        val tag = ParameterMultiValueTag(filteredParams).toMultiTag.add(StringTag(s"groupId=${groupId}"))
        x.addTag(AGGREGATION, tag)
      }
      catch {
        case e: Exception => logger.warn(s"could not add parameters tag, ignoring: $e")
      }
      x
    }).log("after fromSource throughput meter"))
    val balance: UniformFanOutShape[ProcessingMessage[RequestTemplate], ProcessingMessage[RequestTemplate]] = b
      .add(Balance.apply[ProcessingMessage[RequestTemplate]](connections.size, waitForAllDownstreams = false))
    val connectionFlows: Seq[Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)], NotUsed]] = connections
      .map(x => {
        val connectionPool: Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] = connectionToFlowFunc(x)
        val flowResult: Flow[ProcessingMessage[RequestTemplate], (Either[Throwable, T], ProcessingMessage[RequestTemplate]), NotUsed] = GenericRequestFlows
          .requestAndParseResponseFlow[ProcessingMessage[RequestTemplate], T](
            credentialsProvider = x.credentialsProvider,
            connectionPool = connectionPool,
            meter = Some(throughputActor), responseHandler = parsingFunc)
        flowResult.via(Flow.fromFunction(x => {
          // map to processing message of tuple
          Corn((x._1, x._2.data)).withTags(TagType.AGGREGATION, x._2.getTagsForType(AGGREGATION))
        }))
      })
    val merge = b.add(Merge[ProcessingMessage[(Either[Throwable, T], RequestTemplate)]](connections.size))
    val throughputFlow: FlowShape[ProcessingMessage[(Either[Throwable, T], RequestTemplate)], ProcessingMessage[(Either[Throwable, T], RequestTemplate)]] = b
      .add(flowThroughActorMeter(throughputActor, "toMetricsCalc"))
    initThroughputFlow ~> balance
    connectionFlows.foreach(x => balance ~> x ~> merge)
    merge ~> throughputFlow
    FlowShape(initThroughputFlow.in, throughputFlow.out)
  }


  def experimentDefinitionToTaskSeq(query: String,
                                    metricsCalculation: MetricsCalculation,
                                    judgementProviderFactory: JudgementProviderFactory[Double])
                                   (implicit ec: ExecutionContext): Seq[Task[_]] = PlanProvider
    .metricsCalcuationTaskSeq(query, judgementProviderFactory, metricsCalculation)

}
