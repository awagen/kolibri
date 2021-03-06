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

package de.awagen.kolibri.base.actors.routing

import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.kolibri.base.actors.KolibriTestKit
import de.awagen.kolibri.base.actors.testactors.TestTransformActor
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.Corn
import de.awagen.kolibri.base.cluster.ClusterSingletonUtils
import de.awagen.kolibri.base.config.AppProperties.config
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers

import scala.concurrent.duration._

class RoutingActorSpec extends KolibriTestKit
  with ImplicitSender
  with org.scalatest.wordspec.AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true
  var cluster: Cluster = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    // local node discovery for cluster forming
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    cluster = Cluster(system)
    val transformActorProps = Props(TestTransformActor(x => Corn(x.data + 1)))
    cluster.registerOnMemberUp({
      ClusterSingletonUtils.createClusterSingletonManager(system, Props(RoutingActor(transformActorProps)))
    })
  }

  private[this] def createWorkerRoutingService: ActorRef = {
    val singletonProxyConfig: Config = ConfigFactory.load(config.SINGLETON_PROXY_CONFIG_PATH)
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = config.SINGLETON_MANAGER_PATH,
        settings = ClusterSingletonProxySettings.create(singletonProxyConfig)
      )
    )
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "RoutingActor" must {

    "successfully create and route to actor and send response to sender" in {
      // given
      val router: ActorRef = createWorkerRoutingService
      // when
      router ! Corn(1)
      // then
      expectMsg(1 minute, Corn(2))
    }

  }


}
