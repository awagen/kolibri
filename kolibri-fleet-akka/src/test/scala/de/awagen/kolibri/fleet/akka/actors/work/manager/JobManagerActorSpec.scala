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

package de.awagen.kolibri.fleet.akka.actors.work.manager

import akka.actor.ActorRef
import akka.testkit.TestProbe
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{ProcessingMessage, ProcessingResult, ResultSummary}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import de.awagen.kolibri.fleet.akka.actors.KolibriTestKit
import de.awagen.kolibri.fleet.akka.actors.TestMessages.{TaggedInt, messagesToActorRefRunnableGenFunc}
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.FinishedJobEvent
import de.awagen.kolibri.fleet.akka.actors.work.manager.JobManagerActor.ProcessJobCmd
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnable
import de.awagen.kolibri.fleet.akka.jobdefinitions.TestJobDefinitions.MapWithCount

import scala.concurrent.duration._

class JobManagerActorSpec extends KolibriTestKit {

  "JobManagerActor" must {

    val aggregatorSupplier = new SerializableSupplier[Aggregator[ProcessingMessage[Int], MapWithCount[Tag, Double]]] {
      override def apply(): Aggregator[ProcessingMessage[Int], MapWithCount[Tag, Double]] =
        new Aggregator[ProcessingMessage[Int], MapWithCount[Tag, Double]]() {
          var map: MapWithCount[Tag, Double] = MapWithCount(Map.empty[Tag, Double], 0)

          override def add(sample: ProcessingMessage[Int]): Unit = {
            val keys = sample.getTagsForType(AGGREGATION)
            keys.foreach(x => {
              map = MapWithCount(map.map + (x -> (map.map.getOrElse(x, 0.0) + sample.data)), map.count + 1)
            })
          }

          override def aggregation: MapWithCount[Tag, Double] = MapWithCount(Map(map.map.toSeq: _*), map.count)

          override def addAggregate(other: MapWithCount[Tag, Double]): Unit = {
            other.map.keys.foreach(x => {
              map = MapWithCount(map.map + (x -> (map.map.getOrElse(x, 0.0) + other.map(x))), map.count + other.count)
            })
          }
        }
    }

    "correctly process ProcessJobCmd message" in {
      // given
      val testProbe: TestProbe = TestProbe()
      val managerProps = JobManagerActor.props(
        experimentId = "testId",
        perBatchAggregatorSupplier = aggregatorSupplier,
        perJobAggregatorSupplier = aggregatorSupplier,
        writer = (_: MapWithCount[Tag, Double], _: Tag) => Right(()),
        maxProcessDuration = 10 minutes,
        maxBatchDuration = 1 minute)
      val jobManagerActor: ActorRef = system.actorOf(managerProps)
      val jobGenerator: IndexedGenerator[ActorRunnable[TaggedInt, Int, Int, MapWithCount[Tag, Double]]] = ByFunctionNrLimitedIndexedGenerator(
        nrOfElements = 4,
        genFunc = x => Some(messagesToActorRefRunnableGenFunc("testId").apply(x))
      )
      // when
      val msg: ProcessJobCmd[TaggedInt, Int, Int, MapWithCount[Tag, Double]] = ProcessJobCmd(job = jobGenerator)
      jobManagerActor.tell(msg, testProbe.ref)
      val expectedResult = ResultSummary(
        result = ProcessingResult.SUCCESS,
        nrOfBatchesTotal = 4,
        nrOfBatchesSentForProcessing = 4,
        nrOfResultsReceived = 4,
        failedBatches = Seq()
      )
      // then
      testProbe.expectMsgPF(10 seconds) {
        case e: FinishedJobEvent if e.jobId == "testId" && e.jobStatusInfo.resultSummary == expectedResult => true
        case _ => false
      }
    }
  }

}
