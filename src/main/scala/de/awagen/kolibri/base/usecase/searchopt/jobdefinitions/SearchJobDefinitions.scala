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
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationStateWithData, AggregationStateWithoutData, Corn, ProcessingMessage}
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
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.{Flows, Writer}
import de.awagen.kolibri.base.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object SearchJobDefinitions {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)


  def jsValueToTypeTaggedMap(parsingConfig: ParsingConfig): SerializableFunction1[JsValue, WeaklyTypedMap[String]] = new SerializableFunction1[JsValue, WeaklyTypedMap[String]] {
    override def apply(jsValue: JsValue): WeaklyTypedMap[String] = {
      val typedMap = BaseWeaklyTypedMap(mutable.Map.empty)
      parsingConfig.seqSelectors.foreach(seqSelector => {
        val value: Seq[_] = seqSelector.select(jsValue)
        typedMap.put(seqSelector.name, value)
      })
      parsingConfig.singleSelectors.foreach(selector => {
        val valueOpt: Option[Any] = selector.select(jsValue)
        valueOpt.foreach(value => typedMap.put(selector.name, value))
      })
      if (typedMap.keys.isEmpty) {
        logger.warn("no data placed in typed map")
      }
      typedMap
    }
  }

  def searchEvaluationToRunnableJobCmd(searchEvaluation: SearchEvaluation)(implicit as: ActorSystem, ec: ExecutionContext):
  SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
    JobMsgFactory.createActorRunnableJobCmd[Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]], RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]](
      jobId = searchEvaluation.jobName,
      data = searchEvaluation.requestTemplateModifiers,
      dataBatchGenerator = batchGenerator(batchByIndex = searchEvaluation.batchByIndex),
      transformerFlow = Flows.fullProcessingFlow(
        throughputActor = Option.empty[ActorRef],
        contextPath = searchEvaluation.contextPath,
        fixedParams = searchEvaluation.fixedParams,
        queryParam = searchEvaluation.queryParam,
        excludeParamsFromMetricRow = searchEvaluation.excludeParamsFromMetricRow,
        groupId = searchEvaluation.jobName,
        connections = searchEvaluation.connections,
        taggingConfiguration = searchEvaluation.taggingConfiguration,
        requestTemplateStorageKey = searchEvaluation.requestTemplateStorageKey,
        responseParsingFunc = SolrHttpResponseHandlers.httpResponseToTypeTaggedMapParseFunc(_ => true, jsValueToTypeTaggedMap(searchEvaluation.parsingConfig)),
        mapFutureMetricRowCalculation = searchEvaluation.mapFutureMetricRowCalculation,
        singleMapCalculations = searchEvaluation.singleMapCalculations,
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
            case AggregationStateWithData(data: MetricAggregation[Tag], _, _, _) =>
              // TODO: check the counts, e.g at the moment sum should be the correct criterium
              SuccessAndErrorCounts(data.totalSuccessCountSum, data.totalErrorCountSum)
            case AggregationStateWithoutData(elementCount: Int, _, _, _) =>
              // TODO: need success and error counts here, change elementCount in the message to
              // SuccessAndErrorCounts
              SuccessAndErrorCounts(elementCount, 0)
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

