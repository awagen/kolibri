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


package de.awagen.kolibri.fleet.akka.jobdefinitions

import akka.stream.scaladsl.Flow
import de.awagen.kolibri.definitions.domain.jobdefinitions.Batch
import de.awagen.kolibri.definitions.domain.jobdefinitions.provider.data.BatchGenerators.IntNumberBatchGenerator
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.definitions.processing.classifier.Mapper.AcceptAllAsIdentityMapper
import de.awagen.kolibri.definitions.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.definitions.processing.execution.expectation._
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataSample
import de.awagen.kolibri.datatypes.values.RunningValues.doubleAvgRunningValue
import de.awagen.kolibri.datatypes.values.aggregation.immutable.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import de.awagen.kolibri.storage.io.writer.base.LocalDirectoryFileWriter
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

object TestJobDefinitions {

  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  case class MapWithCount[U, V](map: Map[U, V], count: Int) extends WithCount

  case class MutableMapWithCount[U, V](map: mutable.Map[U, V], var count: Int) extends WithCount

  object Implicits {
    // adding implicits only to transform normal Map to Mao implementing WithCount
    // for testing purposes the count value just remains 0
    implicit class MapWithCountImplicit[U, V](map: Map[U, V]) {
      def toCountMap(count: Int): MapWithCount[U, V] = MapWithCount(map, count)
    }

    // adding implicits only to transform normal Map to Mao implementing WithCount
    // for testing purposes the count value just remains 0
    implicit class MutableMapWithCountImplicit[U, V](map: mutable.Map[U, V]) {
      def toCountMap(count: Int): MutableMapWithCount[U, V] = MutableMapWithCount(map, count)
    }
  }

  import Implicits._


  class RunningDoubleAvgPerTagAggregator() extends Aggregator[ProcessingMessage[Double], MapWithCount[Tag, AggregateValue[Double]]] {
    val map: mutable.Map[Tag, AggregateValue[Double]] = mutable.Map.empty
    var count: Int = 0

    override def add(sample: ProcessingMessage[Double]): Unit = {
      val keys: Set[Tag] = sample.getTagsForType(AGGREGATION)
      keys.foreach(key => {
        map(key) = map.getOrElse(key, doubleAvgRunningValue(weightedCount = 0.0, count = 0, value = 0.0)).add(DataSample(1.0, sample.data))
        count += 1
      })
    }

    override def aggregation: MapWithCount[Tag, AggregateValue[Double]] = Map(map.toSeq: _*).toCountMap(count)

    override def addAggregate(aggregatedValue: MapWithCount[Tag, AggregateValue[Double]]): Unit = {
      aggregatedValue.map.keys.foreach(key => {
        map(key) = map.getOrElse(key, doubleAvgRunningValue(weightedCount = 0.0, count = 0, value = 0.0)).add(aggregatedValue.map(key))
        count += aggregatedValue.count
      })
    }
  }

  def piEstimationJob(jobName: String, nrThrows: Int, batchSize: Int, resultDir: String): SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]] = {
    assert(batchSize <= nrThrows)
    val flowFunct: SerializableFunction1[Int, ProcessingMessage[Double]] = new SerializableFunction1[Int, ProcessingMessage[Double]]() {
      override def apply(v1: Int): ProcessingMessage[Double] = {
        val sq_rad = math.pow(math.random(), 2) + math.pow(math.random(), 2)
        if (sq_rad <= 1) {
          Corn(1.0).withTags(AGGREGATION, Set(StringTag("ALL"), StringTag("1")))
        }
        else {
          Corn(0.0).withTags(AGGREGATION, Set(StringTag("ALL"), StringTag("0")))
        }
      }
    }
    val batchGenerator: SerializableFunction1[Int, IndexedGenerator[Batch[Int]]] = new SerializableFunction1[Int, IndexedGenerator[Batch[Int]]] {
      override def apply(v1: Int): IndexedGenerator[Batch[Int]] = IntNumberBatchGenerator(batchSize).batchFunc.apply(v1)
    }
    val expectationGen: SerializableFunction1[Int, ExecutionExpectation] = new SerializableFunction1[Int, ExecutionExpectation] {
      override def apply(v1: Int): ExecutionExpectation = BaseExecutionExpectation(
        fulfillAllForSuccess = Seq(ClassifyingCountExpectation(classifier = Map("finishResponse" -> {
          case Corn(e, _) if e.isInstanceOf[Double] => true
          case _ => false
        }), expectedClassCounts = Map("finishResponse" -> v1))),
        fulfillAnyForFail = Seq(StopExpectation(v1, {
          _ => SuccessAndErrorCounts(1, 0)
        }, x => x._2 > 0),
          TimeExpectation(10 seconds))
      )
    }

    val aggregatorSupplier = new SerializableSupplier[Aggregator[ProcessingMessage[Double], MapWithCount[Tag, AggregateValue[Double]]]]() {
      override def apply(): Aggregator[ProcessingMessage[Double], MapWithCount[Tag, AggregateValue[Double]]] = new RunningDoubleAvgPerTagAggregator()
    }

    val writer: Writer[MapWithCount[Tag, AggregateValue[Double]], Tag, Any] = new Writer[MapWithCount[Tag, AggregateValue[Double]], Tag, Any] {
      override def write(data: MapWithCount[Tag, AggregateValue[Double]], targetIdentifier: Tag): Either[Exception, Any] = {
        logger.info("writing result: {}", data)
        logger.info("result is '{}' on '{}' samples; writing result", data.map, data.map(StringTag("ALL")).numSamples)
        val fileWriter = LocalDirectoryFileWriter(resultDir)
        val resultString = data.map.keys.map(x => s"$x\t${data.map(x).numSamples}\t${data.map(x).value.toString}").toSeq.mkString("\n")
        fileWriter.write(resultString, "dartThrowResult.txt")
      }
      override def delete(targetIdentifier: Tag): Either[Exception, Any] = {
        throw new IllegalAccessException("no deletion allowed here")
      }
    }

    JobMsgFactory.createActorRunnableJobCmd[Int, Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]](
      jobId = jobName,
      nrThrows,
      dataBatchGenerator = batchGenerator,
      transformerFlow = Flow.fromFunction[Int, ProcessingMessage[Double]](flowFunct),
      processingActorProps = None,
      perBatchExpectationGenerator = expectationGen,
      perBatchAggregatorSupplier = aggregatorSupplier,
      perJobAggregatorSupplier = aggregatorSupplier,
      writer = writer,
      filteringSingleElementMapperForAggregator = new AcceptAllAsIdentityMapper[ProcessingMessage[Double]],
      filterAggregationMapperForAggregator = new AcceptAllAsIdentityMapper[MapWithCount[Tag, AggregateValue[Double]]],
      filteringMapperForResultSending = new AcceptAllAsIdentityMapper[MapWithCount[Tag, AggregateValue[Double]]],
      returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
      allowedTimePerElementInMillis = 10,
      allowedTimeForJobInSeconds = 600,
      allowedTimePerBatchInSeconds = 60,
      expectResultsFromBatchCalculations = true
    )
  }

}
