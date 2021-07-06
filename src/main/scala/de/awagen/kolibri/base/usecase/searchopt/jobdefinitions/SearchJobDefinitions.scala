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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.JobMsgFactory
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{RequestParameterModifier, RequestTemplateBuilderModifier}
import de.awagen.kolibri.base.processing.modifiers.{Modifier, RequestTemplateBuilderModifiers}
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.responsehandlers.SolrHttpResponseHandlers
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Aggregators.{fullJobToSingleTagAggregatorSupplier, singleBatchAggregatorSupplier}
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.BatchGenerators.batchGenerator
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Expectations.expectationPerBatchSupplier
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.RequestTemplatesAndBuilders.taggerByParameter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.{Flows, Writer}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Metrics._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{JudgementHandlingStrategy, MetricsCalculation}
import de.awagen.kolibri.base.usecase.searchopt.provider.{ClassPathFileBasedJudgementProviderFactory, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.{DistinctValues, RangeValues}

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object SearchJobDefinitions {

  // define the changes to the request
  val urlModifierGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = Seq(
    ByFunctionNrLimitedIndexedGenerator(20,
      genFunc = x => Some(RequestParameterModifier(params = immutable.Map[String, Seq[String]]("q" -> Seq(s"q$x")),
        replace = false)))
  )
  val requestParameterGrid: OrderedMultiValues = GridOrderedMultiValues.apply(Seq(
    DistinctValues[String]("a", Range(0, 10, 1).map(x => s"q$x")),
    RangeValues[Float](name = "rangeParam1", start = 0.0F, end = 100.0F, stepSize = 1.0F)
  ))
  val paramGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = requestParameterGrid.values
    .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
      x.getAll.map(y => RequestTemplateBuilderModifiers.RequestParameterModifier(params = immutable.Map(x.name -> Seq(y.toString)), replace = true)))
    )
  val allModifierGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = urlModifierGenerators ++ paramGenerators

  // defining which metrics to calculate and how to handle missing judgements
  def metricsCalculation: MetricsCalculation = MetricsCalculation(
    metrics = Seq(NDCG_10, PRECISION_4, ERR),
    judgementHandling = JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS)

  val judgementProviderFactory: JudgementProviderFactory[Double] = ClassPathFileBasedJudgementProviderFactory("data/test_judgements.txt")

  val connections: Seq[Connection] = Seq(
    Connection(host = "172.33.1.4", port = 80, useHttps = false, credentialsProvider = None)
  )

  // define the message to supervisor which will generate ProcessJobCmd
  def processActorRunnableJobCmd(fixedParams: Map[String, Seq[String]])(implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] =
    JobMsgFactory.createActorRunnableJobCmd[Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]], RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]](
      jobId = "testJobId",
      data = allModifierGenerators,
      dataBatchGenerator = batchGenerator(batchByIndex = 0),
      transformerFlow = Flows.fullProcessingFlow(
        throughputActor = Option.empty[ActorRef],
        contextPath = "search",
        fixedParams = fixedParams,
        queryParam = "q",
        groupId = "testJobId",
        connections = connections,
        requestTagger = taggerByParameter("q"),
//        responseParsingFunc = SolrHttpResponseHandlers.httpToProductIdSeqFuture(JsValueValidation.validateStatusCode),
        responseParsingFunc = SolrHttpResponseHandlers.httpToProductIdSeqFuture(_ => true),
        judgementProviderFactory = judgementProviderFactory,
        metricsCalculation = metricsCalculation),
      processingActorProps = None, // need to change this
      perBatchExpectationGenerator = expectationPerBatchSupplier[MetricRow](600 minutes, 10,
        0.3F),
      perBatchAggregatorSupplier = singleBatchAggregatorSupplier,
      perJobAggregatorSupplier = fullJobToSingleTagAggregatorSupplier,
      writer = Writer.localMetricAggregationWriter("/app/data",
        "\t",
        "testJobId",
        x => x.toString),
      returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
      allowedTimePerElementInMillis = 10000,
      allowedTimePerBatchInSeconds = 60000,
      allowedTimeForJobInSeconds = 36000,
      // TODO: set this to no if the batch level is the same as the result writing level (e.g lets say grouping by query)
      expectResultsFromBatchCalculations = false
    )

}

