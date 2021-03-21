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

package de.awagen.kolibri.base.actors.flows

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.resources.ConnectionPoolManager
import de.awagen.kolibri.base.actors.tracking.ThroughputActor.AddForStage
import de.awagen.kolibri.base.actors.tracking.{SetRequestEndFullResponseRead, SetRequestFailed, SetRequestSuccess}
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.domain.jobdefinitions.provider.CredentialsProvider
import de.awagen.kolibri.base.http.client.request.HttpRequestProvider
import de.awagen.kolibri.base.tracking.RequestTrackingInterface

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Flows to be used generically on RequestContext
  */
object GenericRequestFlows {

  case class Host(hostname: String, port: Int)

  def getHttpConnectionPoolFlow[T](implicit actorSystem: ActorSystem): Host => Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] =
    x => Http().cachedHostConnectionPool[T](x.hostname, x.port)

  def getHttpsConnectionPoolFlow[T](implicit actorSystem: ActorSystem): Host => Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] =
    x => Http().cachedHostConnectionPoolHttps[T](x.hostname, x.port)

  def expandProcessContextFlow[T <: HttpRequestProvider](): Flow[T, (HttpRequest, T), NotUsed] =
    Flow.fromFunction[T, (HttpRequest, T)](x => {
      (x.getRequest, x)
    })

  def enrichWithCredentialsFlow[T](credentialsProvider: CredentialsProvider): Flow[(HttpRequest, T), (HttpRequest, T), NotUsed] =
    Flow.fromFunction(x => {
      (x._1.addHeader(credentialsProvider.getBasicAuthHttpHeader), x._2)
    })

  def identity[T](): Flow[(HttpRequest, T), (HttpRequest, T), NotUsed] =
    Flow.fromFunction(x => x)

  def genericRequestFlow[T <: HttpRequestProvider](connectionPool: Flow[(HttpRequest, T), (Try[HttpResponse], T), _],
                                                   credentialsProvider: Option[CredentialsProvider],
                                                   meter: Option[ActorRef]): Flow[T, (Try[HttpResponse], T), NotUsed] = {
    expandProcessContextFlow[T]()
      .via(if (credentialsProvider.nonEmpty) enrichWithCredentialsFlow(credentialsProvider.get) else identity())
      .via(if (meter.isDefined) flowThroughActorMeter(meter.get, "toConnectionPool") else identity())
      .via(connectionPool)
  }

  def flowThroughActorMeter[U](meter: ActorRef, stageName: String): Flow[U, U, _] = {
    Flow.fromFunction(x => {
      meter ! AddForStage(stageName)
      x
    })
  }

  def httpResponseTryToFutureFlow[T, U <: HttpRequestProvider](function: HttpResponse => Future[T],
                                                               failValue: Future[T]): Flow[(Try[HttpResponse], U), (Future[T], U), NotUsed] = {
    Flow.fromFunction[(Try[HttpResponse], U), (Future[T], U)]({
      case (Success(response), requestContext) =>
        if (config.useRequestTracking) RequestTrackingInterface.sendMessageToTrackingActor(SetRequestSuccess(requestContext.getRequest))
        (function.apply(response), requestContext)
      case (Failure(ex), processContext) =>
        if (config.useRequestTracking) RequestTrackingInterface.sendMessageToTrackingActor(SetRequestFailed(processContext.getRequest, ex.getMessage))
        (failValue, processContext)
    })
  }

  def processResponseAndTrackRead[T <: HttpRequestProvider, U](responseHandler: HttpResponse => Future[Either[Throwable, U]])(in: (Try[HttpResponse], T))
                                                              (implicit actorSystem: ActorSystem, ec: ExecutionContext,
                                                               mat: Materializer): Future[(Either[Throwable, U], T)] = {
    in match {
      case (Success(response), requestcontext) =>
        if (config.useRequestTracking) RequestTrackingInterface.sendMessageToTrackingActor(SetRequestSuccess(requestcontext.getRequest))
        for {
          v1 <- {
            val fut = responseHandler.apply(response)
            fut.onComplete(_ => {
              if (config.useRequestTracking) RequestTrackingInterface
                .sendMessageToTrackingActor(SetRequestEndFullResponseRead(requestcontext.getRequest, System.currentTimeMillis()))
            })
            fut
          }
          v2 <- Future.successful(requestcontext)
        } yield (v1, v2)
      case (Failure(ex), processcontext) =>
        if (config.useRequestTracking) RequestTrackingInterface.sendMessageToTrackingActor(SetRequestFailed(processcontext.getRequest, ex.getMessage))
        Future.successful[(Either[Throwable, U], T)]((Left(ex), processcontext))
    }
  }

  /**
    * Flow sending requests and parsing response
    *
    * @param credentialsProvider
    * @param connectionPool
    * @param meter
    * @param responseHandler
    * @param actorSystem
    * @param mat
    * @param ec
    * @tparam T
    * @tparam U
    * @return
    */
  def requestAndParseResponseFlow[T <: HttpRequestProvider, U](credentialsProvider: Option[CredentialsProvider],
                                                               connectionPool: Flow[(HttpRequest, T), (Try[HttpResponse], T), _],
                                                               meter: Option[ActorRef],
                                                               responseHandler: HttpResponse => Future[Either[Throwable, U]])
                                                              (implicit actorSystem: ActorSystem, mat: Materializer,
                                                               ec: ExecutionContext): Flow[T, (Either[Throwable, U], T), NotUsed] = {
    GenericRequestFlows.genericRequestFlow(connectionPool, credentialsProvider, meter)
      .mapAsyncUnordered[(Either[Throwable, U], T)](config.requestParallelism)(x => GenericRequestFlows.processResponseAndTrackRead[T, U](responseHandler)(x))
      .log("after request processing")
      .withAttributes(FlowAttributes.DEFAULT_LOG_LEVELS) //some logging settings
  }

  def requestAndParseResponseFlowByConnections[T <: HttpRequestProvider, U](credentialsProvider: Option[CredentialsProvider],
                                                                            connectionPool: Flow[(HttpRequest, T), (Try[HttpResponse], T), _],
                                                                            meter: Option[ActorRef],
                                                                            responseHandler: HttpResponse => Future[Either[Throwable, U]])
                                                                           (implicit actorSystem: ActorSystem, mat: Materializer,
                                                                            ec: ExecutionContext): Flow[T, (Either[Throwable, U], T), NotUsed] = {

    GenericRequestFlows.genericRequestFlow(connectionPool, credentialsProvider, meter)
      .mapAsyncUnordered[(Either[Throwable, U], T)](config.requestParallelism)(x => GenericRequestFlows.processResponseAndTrackRead[T, U](responseHandler)(x))
      .log("after request processing")
      .withAttributes(FlowAttributes.DEFAULT_LOG_LEVELS) //some logging settings
  }

  def getConnectionPoolFlow[T <: HttpRequestProvider](host: String, port: Int, useHttps: Boolean)
                                                     (implicit actorSystem: ActorSystem): Flow[(HttpRequest, T), (Try[HttpResponse], T), _] = {
    ConnectionPoolManager.connectionPoolStorage.getPoolForHost(Host(host, port), useHttps)
  }

}
