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
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.testkit.{TestKit, TestProbe}
import de.awagen.kolibri.base.actors.TestMessages.{TaggedInt, TestMsg, messagesToActorRefRunnableGenFunc}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{FinishedJobEvent, ProcessingResult}
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.ProcessJobCmd
import de.awagen.kolibri.base.actors.work.worker.ResultMessages.ResultSummary
import de.awagen.kolibri.base.actors.{KolibriTestKit, TestMessages}
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable
import de.awagen.kolibri.datatypes.collections.{BaseIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, BaseAnyAggregator}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable
import scala.concurrent.duration._

class JobManagerActorSpec extends KolibriTestKit
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

  "JobManagerActor" must {

    "correctly process ProcessJobCmd message" in {
      // given
      val testProbe: TestProbe = TestProbe()
      val managerProps = JobManagerActor.props(experimentId = "testId", runningTaskBaselineCount = 10, aggregatorSupplier = new SerializableSupplier[Aggregator[Tag, Any, Map[Tag, Double]]] {
        override def get(): Aggregator[Tag, Any, Map[Tag, Double]] = BaseAnyAggregator(
          new Aggregator[Tag, TestMsg, Map[Tag, Double]]() {
            val map: mutable.Map[Tag, Double] = mutable.Map.empty

            override def add(keys: Set[Tag], sample: TestMsg): Unit = {
              keys.foreach(x => {
                map(x) = map.getOrElse(x, 0.0) + sample.nr
              })
            }

            override def aggregation: Map[Tag, Double] = Map(map.toSeq: _*)

            override def add(other: Map[Tag, Double]): Unit = {
              other.keys.foreach(x => {
                map(x) = map.getOrElse(x, 0.0) + other.getOrElse(x, 0.0)
              })
            }
          }
        )
      }, writer = (_: Map[Tag, Double], _: Tag) => Right(()), maxProcessDuration = 10 minutes, maxBatchDuration = 1 minute)
      val jobManagerActor: ActorRef = system.actorOf(managerProps)
      val jobGenerator: IndexedGenerator[ActorRunnable[TaggedInt, TestMessages.TestMsg, Map[Tag, Double]]] = BaseIndexedGenerator(
        nrOfElements = 4,
        genFunc = x => Some(messagesToActorRefRunnableGenFunc.apply(x))
      )
      // when
      val msg: ProcessJobCmd[TaggedInt, TestMsg, Map[Tag, Double]] = ProcessJobCmd(job = jobGenerator)
      jobManagerActor.tell(msg, testProbe.ref)
      val expectedResult = ResultSummary(
        result = ProcessingResult.SUCCESS,
        nrOfBatchesTotal = 4,
        nrOfBatchesSentForProcessing = 4,
        nrOfResultsReceived = 4,
        leftoverExpectationsMap = Map(),
        failedBatches = Seq()
      )
      // then
      testProbe.expectMsg(2 minutes, FinishedJobEvent("testId", expectedResult))
    }
  }

}
