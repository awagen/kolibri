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

package de.awagen.kolibri.base.actors

import akka.actor.Props
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.testactors.TestTransformActor
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{ProcessActorRunnableJobCmd, ProcessActorRunnableTaskJobCmd, TaggedTypeTaggedMapBatch}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.Corn
import de.awagen.kolibri.base.domain.jobdefinitions.Batch
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.TestTaskHelper.{concatIdsTask, productIdResult, reverseIdsTask, reversedIdKey}
import de.awagen.kolibri.base.processing.execution.expectation.{BaseExecutionExpectation, ReceiveCountExpectation, StopExpectation, TimeExpectation}
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.REPORT_TO_ACTOR_SINK
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.collections.{BaseIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, BaseAnyAggregator}

import scala.collection.{immutable, mutable}
import scala.concurrent.duration._
import scala.reflect.runtime.universe

object TestMessages {

  def messagesToActorRefRunnable(): ActorRunnable[Int, Corn[Int], Map[Tag, Double]] = ActorRunnable(jobId = "test", batchNr = 1, supplier = BaseIndexedGenerator(11, x => Some(x)), transformer = Flow.fromFunction[Int, Corn[Int]](x => Corn(x + 10)), processingActorProps = Some(Props(TestTransformActor(m => {
    val corn = Corn(m.data)
    corn.addTags(AGGREGATION, Set(StringTag("ALL")))
    corn
  }))), expectationGenerator = _ => BaseExecutionExpectation(
    fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(
      Range(0, 11, 1).map(x => Corn(x + 10) -> 1): _*
    ))),
    fulfillAnyForFail = Seq(StopExpectation(0, _ => false, _ => false),
      TimeExpectation(100 days))), aggregationSupplier = new SerializableSupplier[Aggregator[Tag, Any, Map[Tag, Double]]] {
    override def get(): Aggregator[Tag, Any, Map[Tag, Double]] = BaseAnyAggregator(new Aggregator[Tag, Corn[Int], Map[Tag, Double]] {
      override def add(keys: Set[Tag], sample: Corn[Int]): Unit = ()

      override def aggregation: Map[Tag, Double] = Map.empty[Tag, Double]

      override def add(other: Map[Tag, Double]): Unit = ()

    })
  }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)

  val expectedValuesForMessagesToActorRefRunnable: immutable.Seq[Corn[Int]] = Range(0, 11, 1).map(x => Corn(x + 11))

  val msg1: ActorRunnable[Int, Corn[Int], Map[Tag, Double]] = ActorRunnable(jobId = "test", batchNr = 1, supplier = BaseIndexedGenerator(11, x => Some(x)), transformer = Flow.fromFunction[Int, Corn[Int]](x => {
    val corn = Corn(x + 10)
    corn.addTags(AGGREGATION, Set(StringTag("ALL")))
    corn
  }),
    processingActorProps = None, expectationGenerator = _ => BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(
        Range(0, 11, 1).map(x => {
          val corn = Corn(x + 10)
          corn.addTags(AGGREGATION, Set(StringTag("ALL")))
          corn -> 1
        }): _*
      ))),
      fulfillAnyForFail = Seq(StopExpectation(0, _ => false, _ => false),
        TimeExpectation(100 days))), aggregationSupplier = new SerializableSupplier[Aggregator[Tag, Any, Map[Tag, Double]]] {
      override def get(): Aggregator[Tag, Any, Map[Tag, Double]] = BaseAnyAggregator(new Aggregator[Tag, Corn[Int], Map[Tag, Double]] {
        override def add(keys: Set[Tag], sample: Corn[Int]): Unit = ()

        override def aggregation: Map[Tag, Double] = Map.empty[Tag, Double]

        override def add(other: Map[Tag, Double]): Unit = ()

      })
    }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)

  val expectedValuesForMsg1: immutable.Seq[Corn[Int]] = Range(0, 11, 1).map(x => Corn(x + 10))

  case class TaggedInt(value: Int) extends TaggedWithType[Tag]

  def messagesToActorRefRunnableGenFunc: Int => ActorRunnable[TaggedInt, Corn[Int], Map[Tag, Double]] = x =>
    ActorRunnable(jobId = "test", batchNr = x, supplier = BaseIndexedGenerator(3, y => Some(TaggedInt(y + x))), transformer = Flow.fromFunction[TaggedInt, Corn[Int]](z => Corn(z.value)),
      processingActorProps = Some(Props(TestTransformActor(m => {
        val corn = Corn(m.data + 1)
        corn.addTags(AGGREGATION, Set(StringTag("ALL")))
        corn
      }))),
      expectationGenerator = _ => BaseExecutionExpectation(
        fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(
          Corn(x + 1) -> 1,
          Corn(x + 2) -> 1,
          Corn(x + 3) -> 1))),
        fulfillAnyForFail = Seq(StopExpectation(0, _ => false, _ => false),
          TimeExpectation(100 days))), aggregationSupplier = new SerializableSupplier[Aggregator[Tag, Any, Map[Tag, Double]]] {
        override def get(): Aggregator[Tag, Any, Map[Tag, Double]] = BaseAnyAggregator(
          new Aggregator[Tag, Corn[Int], Map[Tag, Double]]() {
            val map: mutable.Map[Tag, Double] = mutable.Map.empty

            override def add(keys: Set[Tag], sample: Corn[Int]): Unit = {
              keys.foreach(x => {
                map(x) = map.getOrElse(x, 0.0) + sample.data
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
      }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)

  def expectedMessagesForRunnableGenFunc(i: Int) = Seq(
    Corn(i + 1),
    Corn(i + 2),
    Corn(i + 3)
  )


  def generateProcessActorRunnableJobCmd(jobId: String): ProcessActorRunnableJobCmd[_, _] = {
    val actorRunnableGenerator: IndexedGenerator[ActorRunnable[TaggedInt, Corn[Int], Map[Tag, Double]]] = BaseIndexedGenerator(
      nrOfElements = 4,
      genFunc = x => Some(messagesToActorRefRunnableGenFunc(x))
    )
    ProcessActorRunnableJobCmd[TaggedInt, Map[Tag, Double]](
      jobId = jobId,
      processElements = actorRunnableGenerator.asInstanceOf[IndexedGenerator[ActorRunnable[TaggedInt, Any, Map[Tag, Double]]]],
      aggregatorSupplier = new SerializableSupplier[Aggregator[Tag, Any, Map[Tag, Double]]] {
        override def get(): Aggregator[Tag, Any, Map[Tag, Double]] = BaseAnyAggregator(new Aggregator[Tag, Corn[Int], Map[Tag, Double]] {
          override def add(keys: Set[Tag], sample: Corn[Int]): Unit = ()

          override def aggregation: Map[Tag, Double] = Map.empty[Tag, Double]

          override def add(other: Map[Tag, Double]): Unit = ()

        })
      },
      writer = (_: Map[Tag, Double], _: Tag) => Right(()),
      allowedTimePerBatch = 10 seconds,
      allowedTimeForJob = 2 minutes
    )
  }

  case class TaggedTypeTaggedMap(d: TypedMapStore) extends TypeTaggedMap with TaggedWithType[Tag] {
    override def put[T, V](key: ClassTyped[V], value: T)(implicit evidence$2: universe.TypeTag[T]): Option[Any] = d.put(key, value)

    override def remove[T](key: ClassTyped[T]): Option[T] = d.remove(key)

    override def get[V](key: ClassTyped[V]): Option[V] = d.get(key)

    override def keys: Iterable[ClassTyped[Any]] = d.keys

    override def keySet: collection.Set[ClassTyped[Any]] = d.keySet
  }

  def typeTaggedMapIterator[T](elements: Seq[T], startDataKey: ClassTyped[T]): IndexedGenerator[TypeTaggedMap with TaggedWithType[Tag]] = {
    BaseIndexedGenerator(elements.size, x => Some(TaggedTypeTaggedMap(TypedMapStore(mutable.Map(startDataKey -> elements(x))))))
  }

  def generateProcessActorRunnableTaskJobCmd(jobId: String): ProcessActorRunnableTaskJobCmd[Any] = {
    val baseElements = Seq(Seq("1", "2"), Seq("3", "4"), Seq("5", "6"))
    val batchTypeTaggedMapGenerator: IndexedGenerator[TaggedTypeTaggedMapBatch] = BaseIndexedGenerator(
      5,
      batchNr => Some(Batch(batchNr, typeTaggedMapIterator(baseElements.map(y => y ++ s"$batchNr"), productIdResult.typed)))
    )
    val tasks: Seq[Task[_]] = Seq(concatIdsTask, reverseIdsTask)
    ProcessActorRunnableTaskJobCmd(
      jobId,
      batchTypeTaggedMapGenerator,
      tasks,
      reversedIdKey.typed,
      aggregatorSupplier = new SerializableSupplier[Aggregator[Tag, Any, Any]] {
        override def get(): Aggregator[Tag, Any, Any] = BaseAnyAggregator[Any, Any](new Aggregator[Tag, Any, Any] {
          override def add(keys: Set[Tag], sample: Any): Unit = ()

          override def aggregation: Any = ()

          override def add(other: Any): Unit = ()
        })
      },
      writer = new Writer[Any, Tag, Any] {
        override def write(data: Any, targetIdentifier: Tag): Either[Exception, Any] = Right(())
      },
      10 seconds,
      1 minute
    )
  }
}
