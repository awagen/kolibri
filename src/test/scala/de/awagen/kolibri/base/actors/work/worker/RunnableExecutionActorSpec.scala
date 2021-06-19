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

package de.awagen.kolibri.base.actors.work.worker

import akka.actor.ActorRef
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.AggregationState
import de.awagen.kolibri.base.actors.{KolibriTestKitNoCluster, TestMessages}
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._


class RunnableExecutionActorSpec extends KolibriTestKitNoCluster
  with ImplicitSender //required for expectMsg
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "RunnableExecutorActor" must {

    "correctly execute ActorRunnable with REPORT_TO_ACTOR_SINK" in {
      // given
      val reportToActor: TestProbe = TestProbe()
      val runnableExecutorActor: ActorRef = system.actorOf(RunnableExecutionActor.probs(2 seconds))
      // when
      runnableExecutorActor.tell(TestMessages.msg1, reportToActor.ref)
      // then
      reportToActor.expectMsgPF(2 seconds){
        case _: AggregationState[Map[Tag, Double]] => true
        case _ => false
      }
    }

    "correctly execute ActorRunnable with IGNORE_SINK and reply receiving" in {
      // given
      val reportToActor: TestProbe = TestProbe()
      val runnableExecutorActor: ActorRef = system.actorOf(RunnableExecutionActor.probs(2 seconds))
      // when
      runnableExecutorActor.tell(TestMessages.messagesToActorRefRunnable("testJob"), reportToActor.ref)
      // then
      reportToActor.expectMsgPF(2 seconds){
        case _: AggregationState[Map[Tag, Double]] => true
        case _ => false
      }
    }

  }
}
