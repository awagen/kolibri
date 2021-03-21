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

package de.awagen.kolibri.base.actors.tracking

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.actors.tracking.ThroughputActor.{AddForStage, IndexedCount, ProvideThroughputForStage, ThroughputForStage}
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
      val throughputActor = system.actorOf(Props(new ThroughputActor(10 milliseconds, 50)))
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage1")
      Thread.sleep(11)
      throughputActor ! AddForStage("stage1")
      throughputActor ! AddForStage("stage2")
      throughputActor ! AddForStage("stage2")
      // when
      throughputActor ! ProvideThroughputForStage("stage1")
      throughputActor ! ProvideThroughputForStage("stage2")
      // then
      expectMsgAllOf(1 second, ThroughputForStage(
        "stage1",
        Vector(IndexedCount(0, 5), IndexedCount(1, 1)),
        6
      ),
        ThroughputForStage(
          "stage2",
          Vector(IndexedCount(0, 0), IndexedCount(1, 2)),
          2
        ))
    }

  }

}
