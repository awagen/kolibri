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
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.BaseJobPartIdentifier
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.Corn
import de.awagen.kolibri.base.actors.work.worker.TaskExecutionWorkerActor.ProcessTaskExecution
import de.awagen.kolibri.base.processing.TestTaskHelper._
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.processing.execution.{SimpleTaskExecution, TaskExecution}
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.StringTag
import de.awagen.kolibri.datatypes.tagging.TypeTaggedMapImplicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class TaskExecutionWorkerActorSpec extends KolibriTestKitNoCluster
  with ImplicitSender //required for expectMsg
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "TaskExecutionWorker" must {

    "correctly process syncronous tasks in task execution" in {
      // given
      val data: TypeTaggedMap with TaggedWithType = TypedMapStore.empty.toTaggedWithTypeMap
      data.addTag(AGGREGATION, StringTag("ALL"))
      data.put(productIdResult, Seq("p3", "p4", "p21"))
      val tasks: Seq[Task[_]] = Seq(concatIdsTask, reverseIdsTaskPM)
      val taskExecution: TaskExecution[String] = SimpleTaskExecution(
        resultKey = reversedIdKeyPM,
        data,
        tasks
      )
      val msg = ProcessTaskExecution(taskExecution, BaseJobPartIdentifier("testJob", 1))
      val executionWorker: ActorRef = system.actorOf(TaskExecutionWorkerActor.props)
      // when
      executionWorker ! msg
      // then
      expectMsgPF(2 seconds) {
        case Corn(result, _) =>
          result mustBe "12p,4p,3p"
        case other => fail(s"received message $other instead of expected success msg")
      }
    }

    "correctly execute task sequence including asyncronous tasks" in {
      // given
      implicit val ec: ExecutionContext = ExecutionContext.global
      val ExecutionWorker: ActorRef = system.actorOf(TaskExecutionWorkerActor.props)
      val data: TypeTaggedMap with TaggedWithType = TypedMapStore.empty.toTaggedWithTypeMap
      data.addTag(AGGREGATION, StringTag("ALL"))
      data.put(productIdResult, Seq("p3", "p4", "p21"))
      val tasks: Seq[Task[_]] = Seq(concatIdsTask, asyncReverseIdsTask)
      val taskExecution: TaskExecution[String] = SimpleTaskExecution(
        resultKey = reversedIdKeyPM,
        data.toTaggedWithTypeMap,
        tasks
      )
      val msg: ProcessTaskExecution[String] = ProcessTaskExecution(taskExecution, BaseJobPartIdentifier("testJob", 1))
      // when
      ExecutionWorker ! msg
      // then
      expectMsgPF(2 seconds) {
        case Corn(result, _) =>
          result mustBe "12p,4p,3p"
        case other => fail(s"received message $other instead of expected success msg")
      }
    }
  }
}
