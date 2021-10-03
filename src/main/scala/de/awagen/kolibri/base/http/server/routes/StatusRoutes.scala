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


package de.awagen.kolibri.base.http.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor.{MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.ProvideAllRunningJobStates
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.ShortJobStatusInfo
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.base.http.server.routes.BaseRoutes.{clusterMetricsListenerActor, supervisorActor}
import de.awagen.kolibri.base.io.json.ClusterStatesJsonProtocol._
import de.awagen.kolibri.base.io.json.JobManagerEventJsonProtocol.shortJobStatusFormat
import spray.json.enrichAny

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object StatusRoutes extends CORSHandler {

  def nodeState(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(100 millis)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    import akka.http.scaladsl.server.Directives._

    corsHandler(
      path("nodeState") {
        get {
          onSuccess(clusterMetricsListenerActor ? ProvideMetrics) {
            case MetricsProvided(f) =>
              complete(f.toJson.toString())
            case _ =>
              complete(StatusCodes.ServerError.apply(500)("unexpected status response from server",
                "unexpected status response from server").toString())
          }
        }
      }
    )
  }

  def jobStates(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(100 millis)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    import akka.http.scaladsl.server.Directives._
    corsHandler(
      path("jobStates") {
        get {
          onSuccess(supervisorActor ? ProvideAllRunningJobStates) {
            case result: Success[Seq[ShortJobStatusInfo]] =>
              complete(StatusCodes.OK, s"""${result.get.toJson.toString()}""")
            case result: Failure[Any] =>
              complete(StatusCodes.ServerError.apply(500)(
                result.exception.getMessage,
                result.exception.getMessage).toString())
            case _ =>
              complete(StatusCodes.ServerError.apply(500)(
                "unexpected server response",
                "unexpected server response").toString())
          }
        }
      }
    )
  }

  def health(implicit system: ActorSystem): Route = {
    import akka.http.scaladsl.server.Directives._
    corsHandler(
      path("health") {
        get {
          complete(StatusCodes.OK)
        }
      })

  }

}
