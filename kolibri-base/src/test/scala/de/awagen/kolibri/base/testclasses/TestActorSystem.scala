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

package de.awagen.kolibri.base.testclasses

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

trait TestActorSystem {

  val configValues = new java.util.HashMap[String, Any]()
  configValues.put("akka.actor.provider", "akka.actor.LocalActorRefProvider")
  val baseConfig: Config = ConfigFactory.parseMap(configValues).withFallback(
    ConfigFactory.load()
      .withoutPath("akka.remote")
      .withoutPath("akka.cluster")
      .withoutPath("akka.actor.deployment")
      .withoutPath("akka.extensions"))
  implicit val system: ActorSystem = ActorSystem("test", baseConfig)

}
