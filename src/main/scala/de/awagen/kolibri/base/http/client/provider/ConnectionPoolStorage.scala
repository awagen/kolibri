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

package de.awagen.kolibri.base.http.client.provider

import java.util.concurrent.atomic.AtomicReference
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.flows.GenericFlows.{Host, getHttpConnectionPoolFlow, getHttpsConnectionPoolFlow}
import de.awagen.kolibri.base.http.client.request.HttpRequestProvider
import de.awagen.kolibri.datatypes.ConcurrentUpdateMapOps
import de.awagen.kolibri.datatypes.types.DataStore

import scala.util.Try

case class ConnectionPoolStorage[T <: DataStore[HttpRequestProvider]](map: AtomicReference[Map[String, Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool]]]) {

  def getPoolForHost(host: Host, useHttps: Boolean)(implicit actorSystem: ActorSystem): Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] = {
    val poolFunc = if (useHttps) getHttpsConnectionPoolFlow[T] else getHttpConnectionPoolFlow[T]
    val hostIdentifier = s"${host.hostname}${host.port}$useHttps"
    ConcurrentUpdateMapOps.updateMapEntryIfKeyNotExists[String, Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool]](map, s"$hostIdentifier", poolFunc.apply(host))
    map.get()(hostIdentifier)
  }

}
