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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationStateWithData, AggregationStateWithoutData, Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.domain.jobdefinitions.JobMsgFactory
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluationDefinition
import de.awagen.kolibri.base.processing.classifier.Mapper.AcceptAllAsIdentityMapper
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.base.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.RequestProcessingFlows
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.RequestProcessingFlows.connectionToProcessingFunc
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.responsehandlers.SolrHttpResponseHandlers
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Aggregators.{fullJobToSingleTagAggregatorSupplier, singleBatchAggregatorSupplier}
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.BatchGenerators.batchByGeneratorAtIndex
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Expectations.expectationPerBatchSupplier
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Flows
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
import scala.util.Random

object SearchJobDefinitions {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)


  def jsValueToTypeTaggedMap(parsingConfig: ParsingConfig): SerializableFunction1[JsValue, WeaklyTypedMap[String]] = new SerializableFunction1[JsValue, WeaklyTypedMap[String]] {
    override def apply(jsValue: JsValue): WeaklyTypedMap[String] = {
      val typedMap = BaseWeaklyTypedMap(mutable.Map.empty)
      parsingConfig.selectors.foreach(selector => {
        val value: Any = selector.select(jsValue)
        value match {
          case v: Option[_] => v.foreach(value => typedMap.put(selector.name, value))
          case _ => typedMap.put(selector.name, value)
        }

      })
      if (typedMap.keys.isEmpty) {
        logger.warn("no data placed in typed map")
      }
      typedMap
    }
  }

  /**
   * Generating actor runnable job cmd from search evaluation definition.
   * This function actually loads in the parameter values used for the calculations,
   * thus this instantiation should only be done where the resulting job is to be
   * executed
   */
  def searchEvaluationToRunnableJobCmd(searchEvaluation: SearchEvaluationDefinition)(implicit as: ActorSystem, ec: ExecutionContext):
  SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
    val persistenceModule = AppConfig.persistenceModule
    val writer = persistenceModule.persistenceDIModule.csvMetricAggregationWriter(subFolder = searchEvaluation.jobName, x => {
      val randomAdd: String = Random.alphanumeric.take(5).mkString
      s"${x.toString()}-$randomAdd"
    })
    JobMsgFactory.createActorRunnableJobCmd[Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]], RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]](
      // the what (which job, which data, which batching method)
      jobId = searchEvaluation.jobName,
      // this call actually "materializes" the definition of the search evaluation job by filling
      // requestTemplateModifiers with the state of parameter combinations to evaluate
      data = searchEvaluation.requestTemplateModifiers,
      dataBatchGenerator = batchByGeneratorAtIndex(batchByIndex = searchEvaluation.batchByIndex),
      // data processing / including tagging and metrics calculations
      transformerFlow = transformer(searchEvaluation),
      // if set, sends the parsed data samples to created actor for those passed props for processing. Doesnt if set to none
      processingActorProps = None,
      // expectations, aggregations, writing, returnType (e.g whether to send results to some actor and so on)
      perBatchExpectationGenerator = perBatchExpectationGenerator(searchEvaluation.allowedTimePerBatchInSeconds),
      perBatchAggregatorSupplier = singleBatchAggregatorSupplier,
      perJobAggregatorSupplier = fullJobToSingleTagAggregatorSupplier,
      writer = writer,
      filteringSingleElementMapperForAggregator = new AcceptAllAsIdentityMapper[ProcessingMessage[MetricRow]],
      filterAggregationMapperForAggregator = new AcceptAllAsIdentityMapper[MetricAggregation[Tag]],
      filteringMapperForResultSending = new AcceptAllAsIdentityMapper[MetricAggregation[Tag]],
      returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
      allowedTimePerElementInMillis = searchEvaluation.allowedTimePerElementInMillis,
      allowedTimePerBatchInSeconds = searchEvaluation.allowedTimePerBatchInSeconds,
      allowedTimeForJobInSeconds = searchEvaluation.allowedTimeForJobInSeconds,
      expectResultsFromBatchCalculations = searchEvaluation.expectResultsFromBatchCalculations)
  }

  /**
   * Expectation definition per processing batch.
   *
   * @param allowedTimePerBatchInSeconds - maximal time a batch is allowed to execute, otherwise considered failed
   * @return
   */
  private[jobdefinitions] def perBatchExpectationGenerator(allowedTimePerBatchInSeconds: Int): Int => ExecutionExpectation = {
    expectationPerBatchSupplier[MetricRow](
      allowedTimePerBatchInSeconds seconds,
      50,
      0.5F,
      new SerializableFunction1[Any, SuccessAndErrorCounts] {
        override def apply(v1: Any): SuccessAndErrorCounts = v1 match {
          case Corn(e, _) if e.isInstanceOf[MetricRow] =>
            val result = e.asInstanceOf[MetricRow]
            SuccessAndErrorCounts(result.countStore.successCount, result.countStore.failCount)
          case AggregationStateWithData(data: MetricAggregation[Tag], _, _, _) =>
            val successSampleCount: Int = data.aggregationStateMap.values.flatMap(x => x.rows.values.map(y => y.countStore.successCount)).sum
            val errorSampleCount: Int = data.aggregationStateMap.values.flatMap(x => x.rows.values.map(y => y.countStore.failCount)).sum
            SuccessAndErrorCounts(successSampleCount, errorSampleCount)
          case AggregationStateWithoutData(elementCount: Int, _, _, _) =>
            // TODO: need success and error counts here, change elementCount in the message to
            // SuccessAndErrorCounts
            SuccessAndErrorCounts(elementCount, 0)
          case _ => SuccessAndErrorCounts(0, 0)
        }
      }
    )
  }

  /**
   * This is purely generation of the definition, no processing elements are generated here.
   * Defines the processing flow from the request definition to the result message
   *
   * @param eval
   * @param as
   * @param ec
   * @return
   */
  private[jobdefinitions] def transformer(eval: SearchEvaluationDefinition)(implicit as: ActorSystem, ec: ExecutionContext): Flow[RequestTemplateBuilderModifier, ProcessingMessages.ProcessingMessage[MetricRow], NotUsed] = {
    Flows.fullProcessingFlow(
      contextPath = eval.contextPath,
      fixedParams = eval.fixedParams,
      excludeParamsFromMetricRow = eval.excludeParamsFromMetricRow,
      connections = eval.connections,
      requestAndParsingFlow = Flow.fromGraph(
        RequestProcessingFlows.balancingRequestAndParsingFlow(
          eval.connections,
          connectionToProcessingFunc(
            SolrHttpResponseHandlers.httpResponseToTypeTaggedMapParseFunc(_ => true, jsValueToTypeTaggedMap(eval.parsingConfig))
          )
        )
      ),
      taggingConfiguration = eval.taggingConfiguration,
      requestTemplateStorageKey = eval.requestTemplateStorageKey,
      calculations = eval.calculations,
    )
  }

}

