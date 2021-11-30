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

package de.awagen.kolibri.base.cluster

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.kolibri.base.config.AppProperties.config

object ClusterSingletonUtils {

  val singletonConfig: Config = ConfigFactory.load(config.SINGLETON_MANAGER_CONFIG_PATH)

  def createClusterSingletonManager(actorSystem: ActorSystem, props: Props): ActorRef = {
    val clusterSingletonProperties = ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings.apply(singletonConfig)
    )
    actorSystem.actorOf(clusterSingletonProperties, config.SINGLETON_MANAGER_NAME)
  }

}
