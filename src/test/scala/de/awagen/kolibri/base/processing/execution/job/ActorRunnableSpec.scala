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

package de.awagen.kolibri.base.processing.execution.job

import akka.actor.{ActorRef, Props}
import akka.serialization.{SerializationExtension, Serializers}
import akka.stream.scaladsl.Flow
import akka.testkit.{ImplicitSender, TestKit}
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.processing.execution.expectation.{BaseExecutionExpectation, ExecutionExpectation}
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.REPORT_TO_ACTOR_SINK
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable
import scala.concurrent.duration._

class ActorRunnableSpec extends KolibriTestKitNoCluster
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  object TestMessages {

    val generatorFunc: SerializableFunction1[Int, Option[Int]] = new SerializableFunction1[Int, Option[Int]] {
      override def apply(v1: Int): Option[Int] = Some(v1 + 1)
    }
    val transformerFunc: SerializableFunction1[Int, ProcessingMessage[Int]] = new SerializableFunction1[Int, ProcessingMessage[Int]] {
      override def apply(v1: Int): ProcessingMessage[Int] = Corn(v1 + 2)
    }
    // NOTE: lambda expression here instead of explicit new SerializableSupplier call doest work, kryo serialization fails then
    val expectationGen: SerializableFunction1[Int, ExecutionExpectation] = new SerializableFunction1[Int, ExecutionExpectation] {
      override def apply(v1: Int): ExecutionExpectation = BaseExecutionExpectation.empty()
    }
    val msg1: ActorRunnable[Int, Int, Int, Double] = ActorRunnable(
      jobId = "test",
      batchNr = 1,
      supplier = ByFunctionNrLimitedIndexedGenerator(4, generatorFunc),
      transformer = Flow.fromFunction(transformerFunc),
      processingActorProps = None,
      expectationGenerator = expectationGen,
      aggregationSupplier = new SerializableSupplier[Aggregator[ProcessingMessage[Int], Double]] {
        override def apply(): Aggregator[ProcessingMessage[Int], Double] = new Aggregator[ProcessingMessage[Int], Double] {
          override def add(sample: ProcessingMessage[Int]): Unit = ()

          override def aggregation: Double = 0.0

          override def addAggregate(aggregatedValue: Double): Unit = ()
        }
      }, sinkType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)
  }

  "ActorRunnable" should {

    "correctly execute ActorRunnable" in {
      // given
      val runnableExecutorActor: ActorRef = system.actorOf(Props[TestActorRunnableActor])
      val expectedValues: immutable.Seq[ProcessingMessage[Int]] = Seq(1, 2, 3, 4).map(x => Corn(x + 2))
      // when
      runnableExecutorActor ! TestMessages.msg1
      // then
      expectMsgAllOf[ProcessingMessage[Int]](2 seconds, expectedValues: _*)
    }

    "be serializable" in {
      // given
      val actorRunnable: ActorRunnable[Int, Int, Int, Double] = TestMessages.msg1
      // when
      val serialization = SerializationExtension(system)
      val bytes = serialization.serialize(actorRunnable).get
      val serializerId = serialization.findSerializerFor(actorRunnable).identifier
      val manifest = Serializers.manifestFor(serialization.findSerializerFor(actorRunnable), actorRunnable)
      // Turn it back into an object
      val back: ActorRunnable[Int, Int, Int, Double] = serialization.deserialize(bytes, serializerId, manifest).get.asInstanceOf[ActorRunnable[Int, Int, Int, Double]]
      // then
      back.jobId mustBe actorRunnable.jobId
      back.batchNr mustBe actorRunnable.batchNr
    }

  }

}
