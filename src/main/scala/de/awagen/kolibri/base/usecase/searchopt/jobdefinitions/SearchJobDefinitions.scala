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
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.jobdefinitions.JobMsgFactory
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import de.awagen.kolibri.base.processing.classifier.Mapper.AcceptAllAsIdentityMapper
import de.awagen.kolibri.base.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.responsehandlers.SolrHttpResponseHandlers
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Aggregators.{fullJobToSingleTagAggregatorSupplier, singleBatchAggregatorSupplier}
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.BatchGenerators.batchGenerator
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Expectations.expectationPerBatchSupplier
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.RequestTemplatesAndBuilders.taggerByParameter
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.{Flows, Writer}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Metrics._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{JudgementHandlingStrategy, MetricsCalculation}
import de.awagen.kolibri.base.usecase.searchopt.provider.ClassPathFileBasedJudgementProviderFactory
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object SearchJobDefinitions {


  def searchEvaluationToRunnableJobCmd(searchEvaluation: SearchEvaluation)(implicit as: ActorSystem, ec: ExecutionContext):
  SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
    JobMsgFactory.createActorRunnableJobCmd[Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]], RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]](
      jobId = searchEvaluation.jobName,
      data = searchEvaluation.requestPermutation.getModifierSeq,
      dataBatchGenerator = batchGenerator(batchByIndex = searchEvaluation.batchByIndex),
      transformerFlow = Flows.fullProcessingFlow(
        throughputActor = Option.empty[ActorRef],
        contextPath = searchEvaluation.contextPath,
        fixedParams = searchEvaluation.fixedParams,
        queryParam = searchEvaluation.queryParam,
        excludeParamsFromMetricRow = searchEvaluation.excludeParamsFromMetricRow,
        groupId = searchEvaluation.jobName,
        connections = searchEvaluation.connections,
        requestTagger = taggerByParameter(searchEvaluation.tagByParam),
        responseParsingFunc = SolrHttpResponseHandlers.httpToProductIdSeqFuture(_ => true),
        judgementProviderFactory = ClassPathFileBasedJudgementProviderFactory(
          searchEvaluation.judgementFileClasspathURI
        ),
        metricsCalculation = MetricsCalculation(
          metrics = Seq(NDCG_10, PRECISION_4, ERR),
          judgementHandling = JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS)
      ),
      processingActorProps = None,
      perBatchExpectationGenerator = expectationPerBatchSupplier[MetricRow](
        600 minutes,
        10,
        0.3F,
        new SerializableFunction1[Any, SuccessAndErrorCounts] {
          override def apply(v1: Any): SuccessAndErrorCounts = v1 match {
            case Corn(e) if e.isInstanceOf[MetricRow] =>
              val result = e.asInstanceOf[MetricRow]
              SuccessAndErrorCounts(result.totalSuccessCountMin, result.totalErrorCountMin)
            case AggregationState(data: MetricAggregation[Tag], _, _, _) =>
              SuccessAndErrorCounts(data.totalSuccessCountMin, data.totalErrorCountMin)
            case _ => SuccessAndErrorCounts(0, 0)
          }
        }
      ),
      perBatchAggregatorSupplier = singleBatchAggregatorSupplier,
      perJobAggregatorSupplier = fullJobToSingleTagAggregatorSupplier,
      writer = Writer.localMetricAggregationWriter(
        "/app/data",
        "\t",
        searchEvaluation.jobName,
        x => x.toString),
      filteringSingleElementMapperForAggregator = new AcceptAllAsIdentityMapper[ProcessingMessage[MetricRow]],
      filterAggregationMapperForAggregator = new AcceptAllAsIdentityMapper[MetricAggregation[Tag]],
      filteringMapperForResultSending = new AcceptAllAsIdentityMapper[MetricAggregation[Tag]],
      returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
      allowedTimePerElementInMillis = searchEvaluation.allowedTimePerElementInMillis,
      allowedTimePerBatchInSeconds = searchEvaluation.allowedTimePerBatchInSeconds,
      allowedTimeForJobInSeconds = searchEvaluation.allowedTimeForJobInSeconds,
      expectResultsFromBatchCalculations = searchEvaluation.expectResultsFromBatchCalculations)
  }

}

