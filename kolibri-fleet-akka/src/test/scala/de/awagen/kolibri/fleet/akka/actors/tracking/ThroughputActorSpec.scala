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

package de.awagen.kolibri.fleet.akka.actors.tracking

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.fleet.akka.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.fleet.akka.actors.tracking.ThroughputActor.{AddForStage, ProvideThroughputForStage, ThroughputForStage}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._


class ThroughputActorSpec extends KolibriTestKitNoCluster
  with ImplicitSender //required for expectMsg
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "ThroughputActor" must {

    "correctly track throughput" in {
      // given
      val throughputActor = system.actorOf(Props(new ThroughputActor(50 milliseconds, 50)))
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      Thread.sleep(51)
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage2")
      throughputActor ! AddForStage("stage2")
      // when
      throughputActor ! ProvideThroughputForStage("stage1")
      throughputActor ! ProvideThroughputForStage("stage2")
      // then
      val receivedMessages: Seq[ThroughputForStage] = receiveWhile[ThroughputForStage](1 second, 50 millis, 2)({
        case e => e.asInstanceOf[ThroughputForStage]
      })
      receivedMessages.head.throughputPerTimeUnit.count(x => x.value == 5) mustBe 1
      receivedMessages.head.throughputPerTimeUnit.count(x => x.value == 1) mustBe 1
      receivedMessages.head.totalSoFar mustBe 6
      receivedMessages(1).throughputPerTimeUnit.count(x => x.value == 2) mustBe 1
      receivedMessages(1).totalSoFar mustBe 2
    }

  }

}
