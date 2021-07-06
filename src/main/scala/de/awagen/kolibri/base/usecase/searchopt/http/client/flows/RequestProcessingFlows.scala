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
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream._
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}
import de.awagen.kolibri.base.actors.flows.GenericRequestFlows.flowThroughActorMeter
import de.awagen.kolibri.base.actors.tracking.ThroughputActor.AddForStage
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Flows.connectionFunc
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object RequestProcessingFlows {

  val logger: Logger = LoggerFactory.getLogger(RequestProcessingFlows.getClass)

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
                                                           throughputActor: Option[ActorRef],
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
          val flow: FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)]] = b.add(
            requestAndParsingFlow[T](throughputActor, queryParam, groupId, connections, connectionToFlowFunc, parsingFunc)
          )
          // creating the graph
          source ~> flow ~> sinkInst
          ClosedShape
    })
  }

  /**
    * Creates request execution and parsing flow based on single requests, just picking the protocol, host, port details
    * from the passed connection
    * @param connection: Connection object providing connection details to use
    * @param parsingFunc: Parsing function applied to the retrieved response to yield result
    * @param as
    * @param ec
    * @tparam T
    * @return
    */
  @deprecated
  def singleRequestFlow[T](connection: Connection, parsingFunc: HttpResponse => Future[Either[Throwable, T]])(implicit as: ActorSystem,
                                                                                                              ec: ExecutionContext): Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)], NotUsed] = {
    val flowResult: Flow[ProcessingMessage[RequestTemplate], (Either[Throwable, T], ProcessingMessage[RequestTemplate]), NotUsed] =
      Flow.fromFunction[ProcessingMessage[RequestTemplate], (HttpRequest, ProcessingMessage[RequestTemplate])](
        y => (y.data.getRequest, y)
      ).mapAsyncUnordered[(Either[Throwable, T], ProcessingMessage[RequestTemplate])](config.requestParallelism)(
        y => {
          val e: Future[(Either[Throwable, T], ProcessingMessage[RequestTemplate])] = {
            val protocol: String = if (connection.useHttps) "https" else "http"
            Http().singleRequest(y._1.withUri(Uri(s"$protocol://${connection.host}:${connection.port}${y._1.uri.toString()}")))
              .flatMap { response => parsingFunc.apply(response) }
              .map(v => (v, y._2))
          }
          e
        }
      )
    flowResult.via(Flow.fromFunction(y => {
      // map to processing message of tuple
      Corn((y._1, y._2.data)).withTags(TagType.AGGREGATION, y._2.getTagsForType(AGGREGATION))
    }))
  }

  /**
    * Flow based requesting based on connection pool for the given connection
    * @param connection: Connection providing details for the connection pool
    * @param parsingFunc: Parse function applied to the response to yield the result
    * @param as: implicit ActorSystem
    * @param ec: implicit ExecutionContext
    * @tparam T: type of the parsed result
    * @return
    */
  def connectionRequestFlow[T](connection: Connection, parsingFunc: HttpResponse => Future[Either[Throwable, T]])(implicit as: ActorSystem,
                                                                                                                  ec: ExecutionContext): Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)], NotUsed] = {
    val throughConnectionFlow: Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] =
      connectionFunc.apply(connection)
    val flow =
      Flow.fromFunction[ProcessingMessage[RequestTemplate], (HttpRequest, ProcessingMessage[RequestTemplate])](
        y => (y.data.getRequest, y)
      )
        .via(throughConnectionFlow)
        .mapAsyncUnordered[(Either[Throwable, T], ProcessingMessage[RequestTemplate])](config.requestParallelism) {
          case y@(Success(e), _) =>
            parsingFunc.apply(e).map(v => (v, y._2))
          case y@(Failure(e), _) =>
            Future.successful(Left(e)).map(v => (v, y._2))
        }
    flow.via(Flow.fromFunction(y => {
      // map to processing message of tuple
      Corn((y._1, y._2.data)).withTags(TagType.AGGREGATION, y._2.getTagsForType(AGGREGATION))
    }))
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
  def requestAndParsingFlow[T](throughputActor: Option[ActorRef],
                               queryParam: String,
                               groupId: String,
                               connections: Seq[Connection],
                               connectionToFlowFunc: Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _],
                               parsingFunc: HttpResponse => Future[Either[Throwable, T]]
                              )(implicit as: ActorSystem,
                                mat: Materializer,
                                ec: ExecutionContext): Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)]], NotUsed] = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    // now define the single elements
    val initThroughputFlow = b.add(Flow.fromFunction[ProcessingMessage[RequestTemplate], ProcessingMessage[RequestTemplate]](x => {
      throughputActor.foreach(x => x ! AddForStage("fromSource"))
      x
    }).log("after fromSource throughput meter"))
    val balance: UniformFanOutShape[ProcessingMessage[RequestTemplate], ProcessingMessage[RequestTemplate]] = b
      .add(Balance.apply[ProcessingMessage[RequestTemplate]](connections.size, waitForAllDownstreams = false))

    val connectionFlows: Seq[Flow[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, T], RequestTemplate)], NotUsed]] = connections
      .map(x => connectionRequestFlow(x, parsingFunc))
    val merge = b.add(Merge[ProcessingMessage[(Either[Throwable, T], RequestTemplate)]](connections.size))
    val identityFlow: Flow[ProcessingMessage[(Either[Throwable, T], RequestTemplate)], ProcessingMessage[(Either[Throwable, T], RequestTemplate)], NotUsed] = Flow.fromFunction(identity)
    val throughputFlow: FlowShape[ProcessingMessage[(Either[Throwable, T], RequestTemplate)], ProcessingMessage[(Either[Throwable, T], RequestTemplate)]] = b
      .add(
        throughputActor.map(ta => flowThroughActorMeter[ProcessingMessage[(Either[Throwable, T], RequestTemplate)]](ta, "toMetricsCalc")).getOrElse(identityFlow)
      )
    initThroughputFlow ~> balance
    connectionFlows.foreach(x => balance ~> x ~> merge)
    merge ~> throughputFlow
    FlowShape(initThroughputFlow.in, throughputFlow.out)
  }

}
