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


package de.awagen.kolibri.base.domain.jobdefinitions

import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.jobdefinitions.provider.data.BatchGenerators.IntNumberBatchGenerator
import de.awagen.kolibri.base.http.server.BaseRoutes.logger
import de.awagen.kolibri.base.io.writer.base.LocalDirectoryFileFileWriter
import de.awagen.kolibri.base.processing.classifier.Mapper.AcceptAllAsIdentityMapper
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.RunningValue.doubleAvgRunningValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

object TestJobDefinitions {

  class RunningDoubleAvgPerTagAggregator() extends Aggregator[ProcessingMessage[Double], Map[Tag, AggregateValue[Double]]] {
    val map: mutable.Map[Tag, AggregateValue[Double]] = mutable.Map.empty

    override def add(sample: ProcessingMessage[Double]): Unit = {
      val keys: Set[Tag] = sample.getTagsForType(AGGREGATION)
      keys.foreach(key => {
        map(key) = map.getOrElse(key, doubleAvgRunningValue(count = 0, value = 0.0)).add(sample.data)
      })
    }

    override def aggregation: Map[Tag, AggregateValue[Double]] = Map(map.toSeq: _*)

    override def addAggregate(aggregatedValue: Map[Tag, AggregateValue[Double]]): Unit = {
      aggregatedValue.keys.foreach(key => {
        map(key) = map.getOrElse(key, doubleAvgRunningValue(count = 0, value = 0.0)).add(aggregatedValue(key))
      })
    }
  }

  def piEstimationJob(jobName: String, nrThrows: Int, batchSize: Int, resultDir: String): SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, Map[Tag, AggregateValue[Double]]] = {
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
          case Corn(e) if e.isInstanceOf[Double] => true
          case _ => false
        }), expectedClassCounts = Map("finishResponse" -> v1))),
        fulfillAnyForFail = Seq(StopExpectation(v1, {
          _ => false
        }, x => x._2 > 0),
          TimeExpectation(10 seconds))
      )
    }

    val aggregatorSupplier = new SerializableSupplier[Aggregator[ProcessingMessage[Double], Map[Tag, AggregateValue[Double]]]]() {
      override def apply(): Aggregator[ProcessingMessage[Double], Map[Tag, AggregateValue[Double]]] = new RunningDoubleAvgPerTagAggregator()
    }

    JobMsgFactory.createActorRunnableJobCmd[Int, Int, Double, Double, Map[Tag, AggregateValue[Double]]](
      jobId = jobName,
      nrThrows,
      dataBatchGenerator = batchGenerator,
      transformerFlow = Flow.fromFunction[Int, ProcessingMessage[Double]](flowFunct),
      processingActorProps = None,
      perBatchExpectationGenerator = expectationGen,
      perBatchAggregatorSupplier = aggregatorSupplier,
      perJobAggregatorSupplier = aggregatorSupplier,
      writer = (data: Map[Tag, AggregateValue[Double]], _: Tag) => {
        logger.info("writing result: {}", data)
        logger.info("result is '{}' on '{}' samples; writing result", data, data(StringTag("ALL")).count)
        val fileWriter = LocalDirectoryFileFileWriter(resultDir)
        val resultString = data.keys.map(x => s"$x\t${data(x).count}\t${data(x).value.toString}").toSeq.mkString("\n")
        fileWriter.write(resultString, "dartThrowResult.txt")
      },
      filteringSingleElementMapperForAggregator = new AcceptAllAsIdentityMapper[ProcessingMessage[Double]],
      filterAggregationMapperForAggregator = new AcceptAllAsIdentityMapper[Map[Tag, AggregateValue[Double]]],
      filteringMapperForResultSending = new AcceptAllAsIdentityMapper[Map[Tag, AggregateValue[Double]]],
      returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
      allowedTimePerElementInMillis = 10,
      allowedTimeForJobInSeconds = 600,
      allowedTimePerBatchInSeconds = 60,
      expectResultsFromBatchCalculations = true
    )
  }

}
