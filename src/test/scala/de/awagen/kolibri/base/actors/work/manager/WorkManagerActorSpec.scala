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

package de.awagen.kolibri.base.actors.work.manager

import akka.actor.ActorRef
import akka.testkit.{TestKit, TestProbe}
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.ACK
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor.{TaskExecutionWithTypedResult, TasksWithTypedResult}
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.BaseJobPartIdentifier
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, Corn}
import de.awagen.kolibri.base.actors.{KolibriTestKitNoCluster, TestMessages}
import de.awagen.kolibri.base.processing.TestTaskHelper._
import de.awagen.kolibri.base.processing.execution.SimpleTaskExecution
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.tagging.TypeTaggedMapImplicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class WorkManagerActorSpec extends KolibriTestKitNoCluster
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

  "WorkManager" must {

    "correctly execute ActorRunnable[_,_]" in {
      // given
      val workManager: ActorRef = system.actorOf(WorkManagerActor.props())
      val testProbe = TestProbe()
      // when
      workManager.tell(TestMessages.messagesToActorRefRunnable(), testProbe.ref)
      // then
      testProbe.expectMsgPF(2 second) {
        case ACK(_, _, _) =>
          true
        case e => fail(s"instead of expected received: $e")
      }
      testProbe.expectMsgPF(2 second) {
        case AggregationState(_, _, _, _) =>
          true
        case e => fail(s"instead of expected received: $e")
      }
    }

    "correctly execute TasksWithTypedResult" in {
      // given
      val workManager: ActorRef = system.actorOf(WorkManagerActor.props())
      val testProbe = TestProbe()
      val data: TypeTaggedMap with TaggedWithType[Tag] = TypedMapStore.empty.toTaggedWithTypeMap
      data.addTag(AGGREGATION, StringTag("ALL"))
      data.put(productIdResult, Seq("p3", "p4", "p21"))
      val tasks: Seq[Task[_]] = Seq(concatIdsTask, reverseIdsTaskPM)
      val msg = TasksWithTypedResult(data = data, tasks = tasks, finalResultKey = reversedIdKeyPM,
        BaseJobPartIdentifier(jobId = "testJob", batchNr = 1))
      // when
      workManager.tell(msg, testProbe.ref)
      // then
      testProbe.expectMsgPF(2 seconds) {
        case Corn(value) =>
          value mustBe "12p,4p,3p"
        case other => fail(s"received message $other instead of expected success msg")
      }
    }

    "correctly execute TaskExecutionWithTypedResult" in {
      // given
      val workManager: ActorRef = system.actorOf(WorkManagerActor.props())
      val testProbe = TestProbe()
      val data: TypeTaggedMap with TaggedWithType[Tag] = TypedMapStore.empty.toTaggedWithTypeMap
      data.addTag(AGGREGATION, StringTag("ALL"))
      data.put(productIdResult, Seq("p3", "p4", "p21"))
      val tasks: Seq[Task[_]] = Seq(concatIdsTask, reverseIdsTaskPM)
      val taskExecution = SimpleTaskExecution(
        resultKey = reversedIdKeyPM,
        currentData = data.toTaggedWithTypeMap,
        tasks = tasks
      )
      // when
      workManager.tell(TaskExecutionWithTypedResult(taskExecution = taskExecution,
        BaseJobPartIdentifier(jobId = "testJob", batchNr = 1)), testProbe.ref)
      // then
      testProbe.expectMsgPF(2 seconds) {
        case Corn(result) =>
          result mustBe "12p,4p,3p"
        case other => fail(s"received message $other instead of expected success msg")
      }
    }
  }
}
