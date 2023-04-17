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

package de.awagen.kolibri.fleet.akka.http.server

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

object HttpServer extends App {

  def startHttpServer(route: Route, interface: String = "127.0.0.1", port: Int = 8000)
                     (implicit actorSystem: ActorSystem, fm: Materializer): Future[Http.ServerBinding] = {
    Http().newServerAt(interface, port).bindFlow(route)
  }

  def stopHttpServer(binding: Future[Http.ServerBinding])(implicit ec: ExecutionContext): Future[Done] = {
    binding.flatMap(_.unbind())
  }

  def stopHttpServerAndStopActorSystem(binding: Future[Http.ServerBinding])(implicit actorSystem: ActorSystem, ec: ExecutionContext): Unit = {
    stopHttpServer(binding).onComplete(_ => actorSystem.terminate())
  }

}
