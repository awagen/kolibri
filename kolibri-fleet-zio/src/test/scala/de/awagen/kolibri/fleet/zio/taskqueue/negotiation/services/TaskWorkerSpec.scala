/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services

import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.metrics.aggregation.immutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.{JsonTypeCast, NamedClassTyped}
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator}
import de.awagen.kolibri.definitions.domain.jobdefinitions.Batch
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers._
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors._
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors._
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{BatchAggregationInfo, JobBatch}
import de.awagen.kolibri.fleet.zio.execution.{JobDefinitions, TaskTestObjects, ZIOTask}
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker
import zio.{Scope, ZIO}
import zio.test._

object TaskWorkerSpec extends ZIOSpecDefault {

  object TestObjects {

    val baseResourceFolder: String = getClass.getResource("/testdata").getPath

    def countingAggregator(count: Int): Aggregator[TaggedWithType with DataPoint[Unit], Int] = new Aggregator[TaggedWithType with DataPoint[Unit], Int] {
      override def add(sample: TaggedWithType with DataPoint[Unit]): Aggregator[TaggedWithType with DataPoint[Unit], Int] = {
        countingAggregator(count + 1)
      }

      override def aggregation: Int = count

      override def addAggregate(aggregatedValue: Int): Aggregator[TaggedWithType with DataPoint[Unit], Int] = {
        countingAggregator(count + aggregatedValue)
      }
    }

    // aggregation info assuming plain result
    val batchAggregationInfo = BatchAggregationInfo(
      Left(NamedClassTyped[Unit]("DONE_WAITING")),
      () => TestObjects.countingAggregator(0)
    )
    // aggregation info assuming result wrapped in ProcessingMessage
    val batchAggregationInfoResultAsProcessingMessage = BatchAggregationInfo(
      Right(NamedClassTyped[ProcessingMessage[Unit]]("DONE_WAITING")),
      () => TestObjects.countingAggregator(0)
    )
    // job definition where the result is plain Unit
    val jobBatch: JobBatch[Int, Unit, Int] = JobBatch(
      JobDefinitions.simpleWaitJob("testJob1", 10, 100, elementsPerBatch = 10,
        aggregationInfoOpt = Some(batchAggregationInfo)),
      1
    )
    // job definition where result is ProcessingMessage[Unit]
    val jobBatchResultAsProcessingMessage: JobBatch[Int, Unit, Int] = JobBatch(
      JobDefinitions.simpleWaitJobResultAsProcessingMessage("testJob1", 10, 100, elementsPerBatch = 10,
        aggregationInfoOpt = Some(batchAggregationInfoResultAsProcessingMessage)),
      1
    )

    val jobBatchWithoutAggregation: JobBatch[Int, Unit, Int] = JobBatch(
      JobDefinitions.simpleWaitJob("testJob1", 10, 100, elementsPerBatch = 10,
        aggregationInfoOpt = None),
      1
    )
  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TaskWorkerSpec")(

    test("run method executes batch and aggregates results") {
      // given, when, then
      for {
        workRawResult <- TaskWorker.work(TestObjects.jobBatch)
        workProcessingMessageResult <- TaskWorker.work(TestObjects.jobBatchResultAsProcessingMessage)
        // join is needed here to make sure the fiber completed the execution
        _ <- workRawResult._2.join
        _ <- workProcessingMessageResult._2.join
        aggregator <- workRawResult._1.get
        aggregatorForResultAsProcessingMessage <- workProcessingMessageResult._1.get
      } yield assert((aggregator.aggregation, aggregatorForResultAsProcessingMessage.aggregation))(
        Assertion.assertion("both aggregations must contain all elements")(x => x._1 == 10 && x._2 == 10)
      )
    },

    test("must also execute if no aggregator defined") {
      // given, when, then
      for {
        work <- TaskWorker.work(TestObjects.jobBatchWithoutAggregation)
        _ <- work._2.join
        aggregator <- work._1.get
      } yield assert(aggregator.aggregation)(Assertion.equalTo(0))
    },

    test("execute requesting and metric calculation") {
      val parsingConfig = ParsingConfig(Seq(
        TypedJsonSingleValueSelector("field1", PlainPathSelector(Seq("results", "field1")), JsonTypeCast.STRING),
        TypedJsonSingleValueSelector("productIds", PlainPathSelector(Seq("results", "productIds")), JsonTypeCast.SEQ_STRING),
      ))
      val requestTask = TaskTestObjects.requestAndParseTask(
        TaskTestObjects.httpClientMock("""{"results": {"field1": "value1", "productIds": ["p5", "p2", "p1", "p4", "p3"]}}"""),
        parsingConfig
      )
      val metricsTask = TaskTestObjects.calculateMetricsTask(
        requestTask.successKey,
        NodeResourceProvider
      )
      val tasks: Seq[ZIOTask[_]] = Seq(requestTask, metricsTask)
      val judgementFileResourcePath: String = "/data/test_judgements.txt"
      val jobDefinition = JobDefinitions.JobDefinition(
        jobName = "testJob1",
        resourceSetup = Seq(TaskTestObjects.judgementResourceDirective(judgementFileResourcePath)),
        batches = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(
          Batch[RequestTemplateBuilderModifier](0,
            ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(RequestParameterModifier(Map("q" -> Seq("q1")), replace = true)))
          )
        )),
        taskSequence = tasks,
        aggregationInfo = Some(BatchAggregationInfo[MetricRow, MetricAggregation[Tag]](
          successKey = Right(metricsTask.successKey),
          batchAggregatorSupplier = () => new TagKeyMetricAggregationPerClassAggregator(
            aggregationState = MetricAggregation.empty[Tag](identity),
            ignoreIdDiff = false
          ),
          writer = JobDefinitions.doNothingWriter[MetricAggregation[Tag]]
        )
      ))
      for {
        aggAndFiber <- TaskWorker.work[RequestTemplateBuilderModifier, MetricRow, MetricAggregation[Tag]](JobBatch(jobDefinition, 0))
        _ <- aggAndFiber._2.join
        // NOTE: make sure the results are tagged, otherwise the aggregator might not
        // reflect the sample in the final aggregation (depending on tagger)
        aggregator <- aggAndFiber._1.get
        _ <- ZIO.logInfo(s"aggregation: ${aggregator.aggregation.aggregationStateMap}")
      } yield assert(aggregator.aggregation.count)(Assertion.equalTo(1)) &&
        assert(aggregator.aggregation.aggregationStateMap.values.head.rows.values.head.metrics("ndcg_5").biValue.value2.value.asInstanceOf[Double])(Assertion.approximatelyEquals(0.3797, 0.0001))
    }
  )
}
