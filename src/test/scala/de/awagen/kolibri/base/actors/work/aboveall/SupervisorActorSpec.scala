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

package de.awagen.kolibri.base.actors.work.aboveall

import akka.actor.ActorRef
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.base.actors.KolibriTestKit
import de.awagen.kolibri.base.actors.TestMessages.{generateProcessActorRunnableJobCmd, generateProcessActorRunnableTaskJobCmd}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{FinishedJobEvent, ProcessingResult}
import de.awagen.kolibri.base.actors.work.worker.ResultMessages.ResultSummary
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._


class SupervisorActorSpec extends KolibriTestKit
  with ImplicitSender
  with AnyWordSpecLike
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
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

  "SupervisorActor" must {

    "correctly process ProcessActorRunnableJobCmd" in {
      // given
      val supervisor: ActorRef = system.actorOf(SupervisorActor.props(true))
      val msg: SupervisorActor.ProcessActorRunnableJobCmd[_,_] = generateProcessActorRunnableJobCmd("testId1")
      // when
      supervisor ! msg
      // then
      val expectedResult = ResultSummary(
        result = ProcessingResult.SUCCESS,
        nrOfBatchesTotal = 4,
        nrOfBatchesSentForProcessing = 4,
        nrOfResultsReceived = 4,
        leftoverExpectationsMap = Map(),
        failedBatches = Seq()
      )
      expectMsg(1 minute, FinishedJobEvent("testId1", expectedResult))
    }

    "correctly process ProcessActorRunnableTaskJobCmd" in {
      // given
      val supervisor: ActorRef = system.actorOf(SupervisorActor.props(true))
      val msg: SupervisorActor.ProcessActorRunnableTaskJobCmd[_] = generateProcessActorRunnableTaskJobCmd("testId2")
      // when
      supervisor ! msg
      // then
      val expectedResult = ResultSummary(
        result = ProcessingResult.SUCCESS,
        nrOfBatchesTotal = 5,
        nrOfBatchesSentForProcessing = 5,
        nrOfResultsReceived = 5,
        leftoverExpectationsMap = Map(),
        failedBatches = Seq()
      )
      expectMsg(1 minute, FinishedJobEvent("testId2", expectedResult))
    }

  }

}
