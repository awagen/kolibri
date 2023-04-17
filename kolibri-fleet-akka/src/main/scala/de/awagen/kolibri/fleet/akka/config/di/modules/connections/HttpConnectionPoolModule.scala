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


package de.awagen.kolibri.fleet.akka.config.di.modules.connections

import java.security.SecureRandom
import java.security.cert.X509Certificate

import akka.actor.{ActorSystem, ClassicActorSystemProvider}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import com.softwaremill.tagging
import de.awagen.kolibri.fleet.akka.config.AppProperties
import de.awagen.kolibri.base.domain.Connections.Host
import de.awagen.kolibri.fleet.akka.config.di.modules.Modules.{GENERAL_MODULE, HttpConnectionPoolDIModule}
import javax.net.ssl.{SSLContext, SSLEngine, TrustManager, X509TrustManager}

import scala.concurrent.Future
import scala.util.Try

class HttpConnectionPoolModule extends HttpConnectionPoolDIModule with tagging.Tag[GENERAL_MODULE] {

  def createInsecureSslEngine(sslContext: SSLContext)(host: String, port: Int): SSLEngine = {
    val engine = sslContext.createSSLEngine(host, port)
    engine.setUseClientMode(true)
    engine
  }

  // setup of insecure ssl engine as documented in: https://doc.akka.io/docs/akka-http/current/client-side/client-https-support.html
  // only use this is if absolutely needed
  // NOTE: while in the official docs above, didnt seem to do the trick in tests
  def badDefaultSSLContext: SSLContext = SSLContext.getDefault

  // alternative to the official one above (and successful in tests)
  def badSSLContext: SSLContext = {
    val permissiveTrustManager: TrustManager = new X509TrustManager() {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
      override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(Array.empty, Array(permissiveTrustManager), new SecureRandom())
    ctx
  }


  val badHttpsConnectionCtx: HttpsConnectionContext = ConnectionContext.httpsClient(createInsecureSslEngine(badSSLContext) _)

  override def getHttpConnectionPoolFlow[T](implicit actorSystem: ActorSystem): Host => Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] =
    x => Http().cachedHostConnectionPool[T](x.hostname, x.port)

  override def getHttpsConnectionPoolFlow[T](implicit actorSystem: ActorSystem): Host => Flow[(HttpRequest, T), (Try[HttpResponse], T), Http.HostConnectionPool] =
    x => {
      if (AppProperties.config.useInsecureSSLEngine){
        Http().cachedHostConnectionPoolHttps[T](x.hostname, x.port, connectionContext = badHttpsConnectionCtx)
      }
      else {
        Http().cachedHostConnectionPoolHttps[T](x.hostname, x.port)
      }
    }

  override def singleRequest(request: HttpRequest)(implicit system: ClassicActorSystemProvider): Future[HttpResponse] = {
    if (AppProperties.config.useInsecureSSLEngine){
      Http().singleRequest(request, badHttpsConnectionCtx)
    }
    else Http().singleRequest(request)
  }
}
