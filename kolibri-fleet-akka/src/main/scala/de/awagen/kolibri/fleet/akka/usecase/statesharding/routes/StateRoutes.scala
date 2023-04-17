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


package de.awagen.kolibri.fleet.akka.usecase.statesharding.routes

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, path, pathPrefix, post}
import akka.http.scaladsl.server.{Route, StandardRoute}
import de.awagen.kolibri.fleet.akka.cluster.ClusterNode
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.fleet.akka.http.server.routes.CORSHandler
import de.awagen.kolibri.fleet.akka.usecase.statesharding.actors.EventAggregatingActor
import de.awagen.kolibri.fleet.akka.usecase.statesharding.actors.EventAggregatingActor.{CombinedEvent, EntityEvent, KeyValueEvent, RequestEvent}
import de.awagen.kolibri.fleet.akka.usecase.statesharding.io.json.EventJsonProtocol._

import scala.concurrent.ExecutionContextExecutor

object StateRoutes extends CORSHandler {

  def requestEventRoute(event: RequestEvent): StandardRoute = {
    val eventSourceActor: EntityRef[EventAggregatingActor.RequestEvent] =
      ClusterNode.getSystemSetup.sharding
        .entityRefFor(ClusterNode.getSystemSetup.EventAggregatingActorTypeKey, event.sourceId)
    eventSourceActor ! event
    complete(StatusCodes.OK)
  }


  def sendCombinedEvent(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix("event") {
        path("all") {
          post {
            entity(as[CombinedEvent]) {
              event =>
                requestEventRoute(event)
            }
          }
        }
      })
  }

  def sendEntityEvent(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix("event") {
        path("entity") {
          post {
            entity(as[EntityEvent]) {
              event =>
                requestEventRoute(event)
            }
          }
        }
      })
  }

  def sendKeyValueEvent(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix("event") {
        path("keyvalue") {
          post {
            entity(as[KeyValueEvent]) {
              event => requestEventRoute(event)
            }
          }
        }
      })
  }


}
