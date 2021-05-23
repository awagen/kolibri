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

import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{ProcessActorRunnableJobCmd, ProcessActorRunnableTaskJobCmd}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.domain.TaskDataKeys
import de.awagen.kolibri.base.domain.jobdefinitions.ProcessingActorProps.ProcessingActorProps
import de.awagen.kolibri.base.domain.jobdefinitions.provider.data.BatchGenerators.{OrderedMultiValuesBatchGenerator, OrderedMultiValuesTypedTagBatchGenerator}
import de.awagen.kolibri.base.domain.jobdefinitions.provider.data.DataProviders.OrderedMultiValuesProvider
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.ActorRunnableSinkType
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.MapImplicits._
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator


object JobDefinitions {

  trait ActorRunnableJobDefinition[V, V1, V2, U] {

    def createActorRunnableJobCmd: ProcessActorRunnableJobCmd[V, V1, V2, U]

  }

  trait ActorRunnableTaskJobDefinition[W] {

    def createActorRunnableTaskJobCmd: ProcessActorRunnableTaskJobCmd[W]

  }


  case class OrderedMultiValuesRunnableJobDefinition(jobId: String,
                                                     dataProvider: OrderedMultiValuesProvider,
                                                     batchGenerator: OrderedMultiValuesBatchGenerator,
                                                     transformer: MapTransformerFlows.Val,
                                                     processingActorProps: Option[ProcessingActorProps],
                                                     expectationGenerators: RunnableExpectationGenerators.Val,
                                                     returnType: ActorRunnableSinkType,
                                                     aggregatorSupplier: SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]],
                                                     writer: Writer[MetricAggregation[Tag], Tag, Any],
                                                     allowedTimePerBatchInSeconds: Long,
                                                     allowedTimeForJobInSeconds: Long
                                                    ) extends ActorRunnableJobDefinition[MutableTaggedMap[String, Seq[Any]], Any, MetricRow, MetricAggregation[Tag]] {
    override def createActorRunnableJobCmd: ProcessActorRunnableJobCmd[MutableTaggedMap[String, Seq[Any]], Any, MetricRow, MetricAggregation[Tag]] = {
      val serializableBatchGenerator: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[MutableTaggedMap[String, Seq[Any]]]]] = x => batchGenerator.batchFunc.apply(x)
      JobMsgFactory.createActorRunnableJobCmd(
        jobId = jobId,
        data = dataProvider.data,
        dataBatchGenerator = serializableBatchGenerator,
        transformerFlow = transformer.transformerFlow,
        processingActorProps = processingActorProps.map(x => x.props),
        expectationGenerator = expectationGenerators.elementCountToExpectationFunc,
        aggregatorSupplier = aggregatorSupplier,
        writer = writer,
        returnType = returnType,
        allowedTimePerBatchInSeconds = allowedTimePerBatchInSeconds,
        allowedTimeForJobInSeconds = allowedTimeForJobInSeconds
      )
    }
  }

  case class OrderedMultiValuesRunnableTaskJobDefinition(jobId: String,
                                                         dataProvider: OrderedMultiValuesProvider,
                                                         batchGenerator: OrderedMultiValuesTypedTagBatchGenerator,
                                                         resultDataKey: TaskDataKeys.Val[ProcessingMessage[MetricRow]],
                                                         tasks: Seq[TaskDefinitions.Val[Any]],
                                                         aggregatorSupplier: SerializableSupplier[Aggregator[ProcessingMessage[Any], MetricAggregation[Tag]]],
                                                         writer: Writer[MetricAggregation[Tag], Tag, Any],
                                                         allowedTimePerBatchInSeconds: Long,
                                                         allowedTimeForJobInSeconds: Long
                                                        ) extends ActorRunnableTaskJobDefinition[MetricAggregation[Tag]] {
    override def createActorRunnableTaskJobCmd: ProcessActorRunnableTaskJobCmd[MetricAggregation[Tag]] = {
      val serializableBatchGeneratorFunc: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]]] = {
        (v1: OrderedMultiValues) => batchGenerator.batchFunc.apply(v1)
      }
      JobMsgFactory.createActorRunnableTaskJobCmd[OrderedMultiValues, MetricAggregation[Tag]](
        jobId = jobId,
        data = dataProvider.data,
        dataBatchGenerator = serializableBatchGeneratorFunc,
        resultDataKey = resultDataKey,
        tasks = tasks,
        aggregatorSupplier = aggregatorSupplier,
        writer = writer,
        allowedTimePerBatchInSeconds = allowedTimePerBatchInSeconds,
        allowedTimeForJobInSeconds = allowedTimeForJobInSeconds
      )
    }
  }


}
