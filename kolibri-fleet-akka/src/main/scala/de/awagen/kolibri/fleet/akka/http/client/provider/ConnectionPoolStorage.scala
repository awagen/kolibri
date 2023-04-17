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

package de.awagen.kolibri.fleet.akka.http.client.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.domain.Connections.Host
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.datatypes.ConcurrentUpdateMapOps
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.fleet.akka.config.AppConfig.httpModule

import java.util.concurrent.atomic.AtomicReference
import scala.util.Try

case class ConnectionPoolStorage(map: AtomicReference[Map[String, Flow[(HttpRequest, DataPoint[RequestTemplate]), (Try[HttpResponse], DataPoint[RequestTemplate]), Http.HostConnectionPool]]]) {

  def getPoolForHost(host: Host, useHttps: Boolean)(implicit actorSystem: ActorSystem): Flow[(HttpRequest, DataPoint[RequestTemplate]), (Try[HttpResponse], DataPoint[RequestTemplate]), Http.HostConnectionPool] = {
    val poolFunc = if (useHttps) httpModule.httpDIModule.getHttpsConnectionPoolFlow[DataPoint[RequestTemplate]] else httpModule.httpDIModule.getHttpConnectionPoolFlow[DataPoint[RequestTemplate]]
    val hostIdentifier = s"${host.hostname}${host.port}$useHttps"
    ConcurrentUpdateMapOps.updateMapEntryIfKeyNotExists[String, Flow[(HttpRequest, DataPoint[RequestTemplate]), (Try[HttpResponse], DataPoint[RequestTemplate]), Http.HostConnectionPool]](map, s"$hostIdentifier", poolFunc.apply(host))
    map.get()(hostIdentifier)
  }

}
