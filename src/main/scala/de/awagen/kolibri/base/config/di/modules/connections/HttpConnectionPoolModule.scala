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


package de.awagen.kolibri.base.config.di.modules.connections

import akka.actor.{ActorSystem, ClassicActorSystemProvider}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import com.softwaremill.tagging
import de.awagen.kolibri.base.domain.Connections.Host
import de.awagen.kolibri.base.config.di.modules.Modules.{GENERAL_MODULE, HttpConnectionPoolDIModule}

import scala.concurrent.Future
import scala.util.Try

class HttpConnectionPoolModule extends HttpConnectionPoolDIModule with tagging.Tag[GENERAL_MODULE] {

  override def getHttpConnectionPoolFlow[T](implicit actorSystem: ActorSystem): Host => Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] =
    x => Http().cachedHostConnectionPool[T](x.hostname, x.port)

  override def getHttpsConnectionPoolFlow[T](implicit actorSystem: ActorSystem): Host => Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] =
    x => Http().cachedHostConnectionPoolHttps[T](x.hostname, x.port)

  override def singleRequest(request: HttpRequest)(implicit system: ClassicActorSystemProvider): Future[HttpResponse] = {
    Http().singleRequest(request)
  }
}
