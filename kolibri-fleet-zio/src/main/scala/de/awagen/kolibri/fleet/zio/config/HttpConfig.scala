/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.config

import de.awagen.kolibri.fleet.zio.config.AppProperties.config._
import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin}
import zio.http.ZClient.{Config, customized}
import zio.http.internal.middlewares.Cors.CorsConfig
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver
import zio.http.{Client, ClientSSLConfig, DnsResolver, Method, ZClient}
import zio.{Trace, ZLayer, durationInt}

import java.util.concurrent.TimeUnit

object HttpConfig {

  // same as Client.live, but not using a .fresh, which will always provide a new instance
  private lazy val liveHttpClientLayerWithEnv: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientDriver.live ++ ZLayer.service[DnsResolver]) >>> customized
  }

  private lazy val configLayer = ZLayer.succeed({
    AppProperties.config.connectionPoolType match {
      case "DYNAMIC" =>
        Config.default.withDynamicConnectionPool(connectionPoolSizeMin, connectionPoolSizeMax, zio.Duration(connectionTTLInSeconds, TimeUnit.SECONDS)).connectionTimeout(connectionTimeoutInSeconds seconds)
      case "FIXED" =>
        Config.default.withFixedConnectionPool(connectionPoolSizeMin).connectionTimeout(connectionTimeoutInSeconds seconds)
      case otherType =>
        throw new RuntimeException(s"Unknown connection pool type '$otherType', set to either 'FIXED' or 'DYNAMIC'")
    }
  })

  private lazy val nettyConfigLayer = AppProperties.config.nettyHttpClientThreadsMax match {
    case e if e > 0 =>
      ZLayer.succeed(NettyConfig.default.maxThreads(AppProperties.config.nettyHttpClientThreadsMax))
    case _ => ZLayer.succeed(NettyConfig.default)
  }

  lazy val liveHttpClientLayer: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (configLayer ++ nettyConfigLayer ++ DnsResolver.default) >>> Client.live // liveHttpClientLayerWithEnv
  }

  val sslConfig = ClientSSLConfig.Default
  val clientConfig = ZClient.Config.default.ssl(sslConfig)

  val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = _ => Some(AccessControlAllowOrigin.All),
    allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST, Method.PUT, Method.DELETE)
  )

}
