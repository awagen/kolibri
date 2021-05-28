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
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.jobdefinitions.Batch
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.TestTaskHelper.{concatIdsTask, productIdResult, reverseIdsTaskPM, reversedIdKeyPM}
import de.awagen.kolibri.base.processing.execution.expectation.{BaseExecutionExpectation, ReceiveCountExpectation, StopExpectation, TimeExpectation}
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.REPORT_TO_ACTOR_SINK
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.{immutable, mutable}
import scala.concurrent.duration._
import scala.reflect.runtime.universe

object TestMessages {

  val log: Logger = LoggerFactory.getLogger(TestMessages.getClass)

  def messagesToActorRefRunnable(): ActorRunnable[Int, Int, Int, Map[Tag, Double]] = ActorRunnable(jobId = "test", batchNr = 1, supplier = ByFunctionNrLimitedIndexedGenerator(11, x => Some(x)), transformer = Flow.fromFunction[Int, Corn[Int]](x => Corn(x + 10)), processingActorProps = Some(Props(TestTransformActor(m => {
    Corn(m.data).withTags(AGGREGATION, Set(StringTag("ALL")))
  }))), expectationGenerator = _ => BaseExecutionExpectation(
    fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(
      Range(0, 11, 1).map(x => Corn(x + 10) -> 1): _*
    ))),
    fulfillAnyForFail = Seq(StopExpectation(0, _ => false, _ => false),
      TimeExpectation(100 days))), aggregationSupplier = new SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]]] {
    override def get(): Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] = new Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] {
      override def add(sample: TaggedWithType[Tag] with DataStore[Int]): Unit = ()

      override def aggregation: Map[Tag, Double] = Map.empty[Tag, Double]

      override def addAggregate(other: Map[Tag, Double]): Unit = ()

    }
  }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)

  val expectedValuesForMessagesToActorRefRunnable: immutable.Seq[Corn[Int]] = Range(0, 11, 1).map(x => Corn(x + 11))

  val msg1: ActorRunnable[Int, Int, Int, Map[Tag, Double]] = ActorRunnable(jobId = "test", batchNr = 1, supplier = ByFunctionNrLimitedIndexedGenerator(11, x => Some(x)), transformer = Flow.fromFunction[Int, ProcessingMessage[Int]](x => {
    Corn(x + 10).withTags(AGGREGATION, Set(StringTag("ALL")))
  }),
    processingActorProps = None, expectationGenerator = _ => BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(
        Range(0, 11, 1).map(x => {
          val corn = Corn(x + 10).withTags(AGGREGATION, Set(StringTag("ALL")))
          corn -> 1
        }): _*
      ))),
      fulfillAnyForFail = Seq(StopExpectation(0, _ => false, _ => false),
        TimeExpectation(100 days))), aggregationSupplier = new SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]]] {
      override def get(): Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] = new Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] {
        override def add(sample: TaggedWithType[Tag] with DataStore[Int]): Unit = ()

        override def aggregation: Map[Tag, Double] = Map.empty[Tag, Double]

        override def addAggregate(other: Map[Tag, Double]): Unit = ()

      }
    }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)

  val expectedValuesForMsg1: immutable.Seq[Corn[Int]] = Range(0, 11, 1).map(x => Corn(x + 10))

  case class TaggedInt(value: Int) extends TaggedWithType[Tag]

  def messagesToActorRefRunnableGenFunc: Int => ActorRunnable[TaggedInt, Int, Int, Map[Tag, Double]] = x =>
    ActorRunnable(jobId = "test", batchNr = x,
      supplier = ByFunctionNrLimitedIndexedGenerator(3, y => Some(TaggedInt(y + x))),
      transformer = Flow.fromFunction[TaggedInt, ProcessingMessage[Int]](z => {
        Corn(z.value).withTags(AGGREGATION, z.getTagsForType(AGGREGATION))
      }),
      processingActorProps = Some(Props(TestTransformActor(m => {
        Corn(m.data + 1).withTags(AGGREGATION, Set(StringTag("ALL")))
      }))),
      expectationGenerator = _ => BaseExecutionExpectation(
        fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(
          Corn(x + 1) -> 1,
          Corn(x + 2) -> 1,
          Corn(x + 3) -> 1))),
        fulfillAnyForFail = Seq(StopExpectation(0, _ => false, _ => false),
          TimeExpectation(100 days))), aggregationSupplier = new SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]]] {
        override def get(): Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] =
          new Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]]() {
            val map: mutable.Map[Tag, Double] = mutable.Map.empty

            override def add(sample: TaggedWithType[Tag] with DataStore[Int]): Unit = {
              sample match {
                case _: Corn[Int] =>
                  val keys: Set[Tag] = sample.getTagsForType(AGGREGATION)
                  keys.foreach(x => {
                    map(x) = map.getOrElse(x, 0.0) + sample.data
                  })
                case e =>
                  log.warn(s"Expected message of type Corn, got: $e")
              }
            }

            override def aggregation: Map[Tag, Double] = Map(map.toSeq: _*)

            override def addAggregate(other: Map[Tag, Double]): Unit = {
              other.keys.foreach(x => {
                map(x) = map.getOrElse(x, 0.0) + other.getOrElse(x, 0.0)
              })
            }
          }
      }, returnType = REPORT_TO_ACTOR_SINK, 1 minute, 1 minute)

  def expectedMessagesForRunnableGenFunc(i: Int) = Seq(
    Corn(i + 1),
    Corn(i + 2),
    Corn(i + 3)
  )


  def generateProcessActorRunnableJobCmd(jobId: String): ProcessActorRunnableJobCmd[_, _, _, _] = {
    val actorRunnableGenerator: IndexedGenerator[ActorRunnable[TaggedInt, Int, Int, Map[Tag, Double]]] = ByFunctionNrLimitedIndexedGenerator(
      nrOfElements = 4,
      genFunc = x => Some(messagesToActorRefRunnableGenFunc(x))
    )
    ProcessActorRunnableJobCmd[TaggedInt, Corn[Int], Int, Map[Tag, Double]](
      jobId = jobId,
      processElements = actorRunnableGenerator.asInstanceOf[IndexedGenerator[ActorRunnable[TaggedInt, Corn[Int], Int, Map[Tag, Double]]]],
      aggregatorSupplier = new SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]]] {
        override def get(): Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] = new Aggregator[TaggedWithType[Tag] with DataStore[Int], Map[Tag, Double]] {
          override def add(sample: TaggedWithType[Tag] with DataStore[Int]): Unit = ()

          override def aggregation: Map[Tag, Double] = Map.empty[Tag, Double]

          override def addAggregate(other: Map[Tag, Double]): Unit = ()

        }
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
    ByFunctionNrLimitedIndexedGenerator(elements.size, x => Some(TaggedTypeTaggedMap(TypedMapStore(mutable.Map(startDataKey -> elements(x))))))
  }

  def generateProcessActorRunnableTaskJobCmd(jobId: String): ProcessActorRunnableTaskJobCmd[Any] = {
    val baseElements = Seq(Seq("1", "2"), Seq("3", "4"), Seq("5", "6"))
    val batchTypeTaggedMapGenerator: IndexedGenerator[TaggedTypeTaggedMapBatch] = ByFunctionNrLimitedIndexedGenerator(
      5,
      batchNr => Some(Batch(batchNr, typeTaggedMapIterator(baseElements.map(y => y ++ s"$batchNr"), productIdResult)))
    )
    val tasks: Seq[Task[_]] = Seq(concatIdsTask, reverseIdsTaskPM)
    ProcessActorRunnableTaskJobCmd(
      jobId,
      batchTypeTaggedMapGenerator,
      tasks,
      reversedIdKeyPM,
      aggregatorSupplier = new SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[Any], Any]] {
        override def get(): Aggregator[TaggedWithType[Tag] with DataStore[Any], Any] = new Aggregator[TaggedWithType[Tag] with DataStore[Any], Any] {
          override def add(sample: TaggedWithType[Tag] with DataStore[Any]): Unit = ()

          override def aggregation: Any = ()

          override def addAggregate(other: Any): Unit = ()
        }
      },
      writer = new Writer[Any, Tag, Any] {
        override def write(data: Any, targetIdentifier: Tag): Either[Exception, Any] = Right(())
      },
      10 seconds,
      1 minute
    )
  }
}
