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

package de.awagen.kolibri.fleet.akka.actors

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.{Logger, LoggerFactory}

class KolibriTypedTestKitNoCluster(val configOverrides: java.util.HashMap[String, Any]) extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val loadedConfig: Config = ConfigFactory.load("application-test-no-cluster.conf")
  val usedConfig: Config = ConfigFactory.parseMap(configOverrides).withFallback(loadedConfig)
  val testKit: ActorTestKit = ActorTestKit("testsystem", usedConfig)
  implicit val system: ActorSystem[Nothing] = testKit.system

  val localMessageSerializationSetting: String = testKit.config.getString("akka.actor.serialize-messages")
  val creatorSerializationSetting: String = testKit.config.getString("akka.actor.serialize-creators")

  logger.info(s"local message serialization: $localMessageSerializationSetting")
  logger.info(s"creator serialization: $creatorSerializationSetting")

  override def afterAll(): Unit = testKit.shutdownTestKit()
}

