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

import de.awagen.kolibri.definitions.domain.TaskDataKeys
import de.awagen.kolibri.definitions.domain.jobdefinitions.provider.data.BatchGenerators.{OrderedMultiValuesBatchGenerator, OrderedMultiValuesTypedTagBatchGenerator}
import de.awagen.kolibri.definitions.domain.jobdefinitions.provider.data.DataProviders.OrderedMultiValuesProvider
import de.awagen.kolibri.definitions.domain.jobdefinitions.{Batch, RunnableExpectationGenerators, TaskDefinitions}
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.classifier.Mapper.FilteringMapper
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.MapImplicits.MutableTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.{ProcessActorRunnableJobCmd, ProcessActorRunnableTaskJobCmd}
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnableSinkType.ActorRunnableSinkType
import de.awagen.kolibri.fleet.akka.jobdefinitions.ProcessingActorProps.ProcessingActorProps
import de.awagen.kolibri.storage.io.writer.Writers.Writer

object JobDefinitions {

  trait ActorRunnableJobDefinition[V, V1, V2, U <: WithCount] {

    def createActorRunnableJobCmd: ProcessActorRunnableJobCmd[V, V1, V2, U]

  }

  trait ActorRunnableTaskJobDefinition[W <: WithCount] {

    def createActorRunnableTaskJobCmd: ProcessActorRunnableTaskJobCmd[W]

  }


  case class OrderedMultiValuesRunnableJobDefinition(jobId: String,
                                                     dataProvider: OrderedMultiValuesProvider,
                                                     batchGenerator: OrderedMultiValuesBatchGenerator,
                                                     transformer: MapTransformerFlows.Val,
                                                     processingActorProps: Option[ProcessingActorProps],
                                                     expectationGenerators: RunnableExpectationGenerators.Val,
                                                     returnType: ActorRunnableSinkType,
                                                     perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]],
                                                     perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]],
                                                     writer: Writer[MetricAggregation[Tag], Tag, Any],
                                                     filteringSingleElementMapperForAggregator: FilteringMapper[ProcessingMessage[MetricRow], ProcessingMessage[MetricRow]],
                                                     filterAggregationMapperForAggregator: FilteringMapper[MetricAggregation[Tag], MetricAggregation[Tag]],
                                                     filteringMapperForResultSending: FilteringMapper[MetricAggregation[Tag], MetricAggregation[Tag]],
                                                     allowedTimePerElementInMillis: Long,
                                                     allowedTimePerBatchInSeconds: Long,
                                                     allowedTimeForJobInSeconds: Long,
                                                     expectResultsFromBatchCalculations: Boolean
                                                    ) extends ActorRunnableJobDefinition[MutableTaggedMap[String, Seq[Any]], Any, MetricRow, MetricAggregation[Tag]] {
    override def createActorRunnableJobCmd: ProcessActorRunnableJobCmd[MutableTaggedMap[String, Seq[Any]], Any, MetricRow, MetricAggregation[Tag]] = {
      val serializableBatchGenerator: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[MutableTaggedMap[String, Seq[Any]]]]] = x => batchGenerator.batchFunc.apply(x)
      JobMsgFactory.createActorRunnableJobCmd(
        jobId = jobId,
        data = dataProvider.data,
        dataBatchGenerator = serializableBatchGenerator,
        transformerFlow = transformer.transformerFlow,
        processingActorProps = processingActorProps.map(x => x.props),
        perBatchExpectationGenerator = expectationGenerators.elementCountToExpectationFunc,
        perBatchAggregatorSupplier = perBatchAggregatorSupplier,
        perJobAggregatorSupplier = perJobAggregatorSupplier,
        writer = writer,
        filteringSingleElementMapperForAggregator = filteringSingleElementMapperForAggregator,
        filterAggregationMapperForAggregator = filterAggregationMapperForAggregator,
        filteringMapperForResultSending = filteringMapperForResultSending,
        returnType = returnType,
        allowedTimePerElementInMillis = allowedTimePerElementInMillis,
        allowedTimePerBatchInSeconds = allowedTimePerBatchInSeconds,
        allowedTimeForJobInSeconds = allowedTimeForJobInSeconds,
        expectResultsFromBatchCalculations = expectResultsFromBatchCalculations)
    }
  }

  case class OrderedMultiValuesRunnableTaskJobDefinition(jobId: String,
                                                         dataProvider: OrderedMultiValuesProvider,
                                                         batchGenerator: OrderedMultiValuesTypedTagBatchGenerator,
                                                         resultDataKey: TaskDataKeys.Val[ProcessingMessage[MetricRow]],
                                                         tasks: Seq[TaskDefinitions.Val[Any]],
                                                         perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[Any], MetricAggregation[Tag]],
                                                         perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[Any], MetricAggregation[Tag]],
                                                         writer: Writer[MetricAggregation[Tag], Tag, Any],
                                                         filteringSingleElementMapperForAggregator: FilteringMapper[ProcessingMessage[Any], ProcessingMessage[Any]],
                                                         filterAggregationMapperForAggregator: FilteringMapper[MetricAggregation[Tag], MetricAggregation[Tag]],
                                                         filteringMapperForResultSending: FilteringMapper[MetricAggregation[Tag], MetricAggregation[Tag]],
                                                         allowedTimePerBatchInSeconds: Long,
                                                         allowedTimeForJobInSeconds: Long,
                                                         sendResultsBack: Boolean
                                                        ) extends ActorRunnableTaskJobDefinition[MetricAggregation[Tag]] {
    override def createActorRunnableTaskJobCmd: ProcessActorRunnableTaskJobCmd[MetricAggregation[Tag]] = {
      val serializableBatchGeneratorFunc: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType]]] = {
        (v1: OrderedMultiValues) => batchGenerator.batchFunc.apply(v1)
      }
      JobMsgFactory.createActorRunnableTaskJobCmd[OrderedMultiValues, MetricAggregation[Tag]](
        jobId = jobId,
        data = dataProvider.data,
        dataBatchGenerator = serializableBatchGeneratorFunc,
        resultDataKey = resultDataKey,
        tasks = tasks,
        perBatchAggregatorSupplier = perBatchAggregatorSupplier,
        perJobAggregatorSupplier = perJobAggregatorSupplier,
        writer = writer,
        filteringSingleElementMapperForAggregator = filteringSingleElementMapperForAggregator,
        filterAggregationMapperForAggregator = filterAggregationMapperForAggregator,
        filteringMapperForResultSending = filteringMapperForResultSending,
        allowedTimePerBatchInSeconds = allowedTimePerBatchInSeconds,
        allowedTimeForJobInSeconds = allowedTimeForJobInSeconds,
        sendResultsBack = sendResultsBack
      )
    }
  }


}
