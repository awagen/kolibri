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

package de.awagen.kolibri.fleet.akka.actors.tracking

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import de.awagen.kolibri.definitions.tracking.RequestTrackingContext
import de.awagen.kolibri.datatypes.io.KolibriSerializable
//import javax.inject.Singleton

import scala.collection.mutable

case class AddRequestContext(httpRequest: HttpRequest, requestTrackingContext: RequestTrackingContext) extends KolibriSerializable

case class SetRequestStartTime(httpRequest: HttpRequest, timeInMs: Long) extends KolibriSerializable

case class SetRequestStartFirstByteRead(httpRequest: HttpRequest, timeInMs: Long) extends KolibriSerializable

case class SetRequestEndFullResponseRead(httpRequest: HttpRequest, timeInMs: Long) extends KolibriSerializable

case class SetResponseStatsForRequest(httpRequest: HttpRequest, httpResponse: HttpResponse) extends KolibriSerializable

case class SetRequestFailed(httpRequest: HttpRequest, reason: String) extends KolibriSerializable

case class SetRequestSuccess(httpRequest: HttpRequest) extends KolibriSerializable


object RequestTrackingActor {
  val PROJECT_HEADER_PREFIX = "X_HPY_"

  def uniqueIdForHttpRequest(httpRequest: HttpRequest): Int = {
    val normalizedRequest = HttpRequest().withUri(httpRequest.getUri())
      .withMethod(httpRequest.method)
      .withEntity(httpRequest.entity)
      .withProtocol(httpRequest.protocol)
      .withHeaders(httpRequest.headers.filter(x => x.name().startsWith(PROJECT_HEADER_PREFIX)))
    normalizedRequest.hashCode()
  }

  def props: Props = Props[RequestTrackingActor]

}

//TODO: need some aggregation of the stats, for lots of requests keeping the contexts here is not useful and resource-wasting
class RequestTrackingActor extends Actor with ActorLogging {

  val requestContextMap: mutable.Map[Int, RequestTrackingContext] = scala.collection.mutable.Map[Int, RequestTrackingContext]()

  override def receive: Receive = {

    case AddRequestContext(httpRequest, requestTrackingContext) =>
      val id = RequestTrackingActor.uniqueIdForHttpRequest(httpRequest)
      if (requestContextMap.keySet.contains(id)) {
        log.warning("overwriting already existing request context")
        log.debug("adding tracking context for request '{}'", httpRequest.getUri())
      }
      log.debug(s"adding tracking context for request ${httpRequest.getUri()}")
      requestContextMap(id) = requestTrackingContext
    case SetRequestStartTime(httpRequest, timeInMs) =>
      val id = RequestTrackingActor.uniqueIdForHttpRequest(httpRequest)
      if (requestContextMap.keySet.contains(id)) {
        requestContextMap(id).withStartTime(timeInMs)
        log.debug("added startTime for request '{}'", httpRequest.getUri())
      }
      else log.debug("received start time for request '{}' but no context available", httpRequest.getUri())

    case SetRequestStartFirstByteRead(httpRequest, timeInMs) =>
      val id = RequestTrackingActor.uniqueIdForHttpRequest(httpRequest)
      if (requestContextMap.keySet.contains(id)) {
        requestContextMap(id).withStartResponseReadTimeInMs(timeInMs)
        val time = for {
          startTime <- requestContextMap(id).getStartTimeInMs
          endTime <- requestContextMap(id).getStartResponseReadTimeInMs
        } yield endTime - startTime
        log.debug("added time of first byte response read for request '{}'", httpRequest.getUri())
        log.debug("time to response first byte read: {}", time)
      }
      else log.debug("received time of first byte read for request {}' but no context available", httpRequest.getUri())

    case SetRequestEndFullResponseRead(httpRequest, timeInMs) =>
      val id = RequestTrackingActor.uniqueIdForHttpRequest(httpRequest)
      if (requestContextMap.keySet.contains(id)) {
        requestContextMap(id).withEndResponseReadTimeInMs(timeInMs)
        val time = for {
          startTime <- requestContextMap(id).getStartTimeInMs
          endTime <- requestContextMap(id).getEndResponseReadTimeInMs
        } yield endTime - startTime
        log.debug("added end full response read time for request '{}'", httpRequest.getUri())
        log.debug("time to full response read: {}", time)
      }
      else log.debug("received end of full response read for request '{}' but no context available", httpRequest.getUri())

    case SetResponseStatsForRequest(httpRequest, httpResponse) =>
      val id = RequestTrackingActor.uniqueIdForHttpRequest(httpRequest)
      if (requestContextMap.keySet.contains(id)) {
        requestContextMap(id).withStatusCode(httpResponse.status.value)
        log.debug("added response stats for request {}", httpRequest.getUri())
      }
      else log.debug("received response for request '{}' but no context available", httpRequest.getUri())

    case SetRequestFailed(httpRequest, reason) =>
      val id = RequestTrackingActor.uniqueIdForHttpRequest(httpRequest)
      if (requestContextMap.keySet.contains(id)) {
        requestContextMap(id).withRequestFailReason(reason)
        log.debug("added fail response reason for request: {}", httpRequest.getUri())
      }
      else log.debug(s"received failed response reason for request '${httpRequest.getUri()}' but no context available")
  }
}
