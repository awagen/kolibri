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
import de.awagen.kolibri.base.processing.execution.expectation.{BaseExecutionExpectation, ExecutionExpectation}
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.REPORT_TO_ACTOR_SINK
import de.awagen.kolibri.base.processing.execution.job.TestActorRunnableActor.IntMsg
import de.awagen.kolibri.datatypes.collections.BaseIndexedGenerator
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
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

    val generatorFunc: SerializableFunction1[Int, Option[Int]] = (v1: Int) => Some(v1 + 1)
    val transformerFunc: SerializableFunction1[Int, IntMsg] = (v1: Int) => IntMsg(v1 + 2)
    // NOTE: lambda expression here instead of explicit new SerializableSupplier call doest work, kryo serialization fails then
    val expectationGen: SerializableSupplier[ExecutionExpectation] = new SerializableSupplier[ExecutionExpectation] {
      override def get(): ExecutionExpectation = BaseExecutionExpectation.empty()
    }
    val msg1: ActorRunnable[Int, IntMsg, Double] = ActorRunnable(jobId = "test", batchNr = 1, supplier = BaseIndexedGenerator(4, generatorFunc), transformer = Flow.fromFunction(transformerFunc), processingActorProps = None, expectationGenerator = _ => expectationGen.get(), aggregationSupplier = new SerializableSupplier[Aggregator[Tag, Any, Double]]{
                override def get(): Aggregator[Tag, Any, Double] = new Aggregator[Tag, Any, Double] {
                  override def add(keys: Set[Tag], sample: Any): Unit = ()

                  override def aggregation: Double = 0.0

                  override def add(aggregatedValue: Double): Unit = ()
                }
              }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)
  }

  "ActorRunnable" should {

    "correctly execute ActorRunnable" in {
      // given
      val runnableExecutorActor: ActorRef = system.actorOf(Props[TestActorRunnableActor])
      val expectedValues: immutable.Seq[IntMsg] = Seq(1, 2, 3, 4).map(x => IntMsg(x + 2))
      // when
      runnableExecutorActor ! TestMessages.msg1
      // then
      expectMsgAllOf[IntMsg](2 seconds, expectedValues: _*)
    }

    "be serializable" in {
      // given
      val actorRunnable: ActorRunnable[Int, IntMsg, Double] = TestMessages.msg1
      // when
      val serialization = SerializationExtension(system)
      val bytes = serialization.serialize(actorRunnable).get
      val serializerId = serialization.findSerializerFor(actorRunnable).identifier
      val manifest = Serializers.manifestFor(serialization.findSerializerFor(actorRunnable), actorRunnable)
      // Turn it back into an object
      val back: ActorRunnable[Int, IntMsg, Double] = serialization.deserialize(bytes, serializerId, manifest).get.asInstanceOf[ActorRunnable[Int, IntMsg, Double]]
      // then
      back.jobId mustBe actorRunnable.jobId
      back.batchNr mustBe actorRunnable.batchNr
    }

  }

}
