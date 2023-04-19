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

package de.awagen.kolibri.fleet.akka.execution.task.utils

import akka.actor.Props
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.definitions.processing
import de.awagen.kolibri.definitions.processing.JobPartIdentifiers.BaseJobPartIdentifier
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.definitions.processing.classifier.Mapper.AcceptAllAsIdentityMapper
import de.awagen.kolibri.definitions.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.definitions.processing.execution.SimpleTaskExecution
import de.awagen.kolibri.definitions.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.definitions.processing.execution.expectation.{BaseExecutionExpectation, ClassifyingCountExpectation, StopExpectation, TimeExpectation}
import de.awagen.kolibri.definitions.processing.execution.task.Task
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.BatchTypeTaggedMapGenerator
import de.awagen.kolibri.fleet.akka.actors.work.worker.TaskExecutionWorkerActor.ProcessTaskExecution
import de.awagen.kolibri.fleet.akka.execution.job.{ActorRunnable, ActorRunnableSinkType}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.FiniteDuration

object TaskUtils {

  def tasksToActorRunnable[U <: WithCount](jobId: String,
                                           resultKey: ClassTyped[ProcessingMessage[Any]],
                                           mapGenerator: BatchTypeTaggedMapGenerator,
                                           tasks: Seq[Task[_]],
                                           aggregatorSupplier: () => Aggregator[ProcessingMessage[Any], U],
                                           taskExecutionWorkerProps: Props,
                                           timeoutPerRunnable: FiniteDuration,
                                           timeoutPerElement: FiniteDuration,
                                           sendResultsBack: Boolean): IndexedGenerator[ActorRunnable[SimpleTaskExecution[Any], Any, Any, U]] = {
    val executionIterable: IndexedGenerator[IndexedGenerator[SimpleTaskExecution[Any]]] =
      mapGenerator.mapGen(x => x.data.mapGen(y => processing.execution.SimpleTaskExecution(resultKey, y, tasks)))
    val atomicInt = new AtomicInteger(0)
    executionIterable.mapGen(x => {
      val batchNr = atomicInt.addAndGet(1)
      ActorRunnable[SimpleTaskExecution[Any], Any, Any, U](
        jobId = jobId,
        batchNr = batchNr,
        supplier = x,
        // send single TaskExecutions to TaskExecutionWorkerActor for processing
        transformer = Flow.fromFunction[SimpleTaskExecution[Any], ProcessingMessage[Any]](x => Corn(ProcessTaskExecution(taskExecution = x, BaseJobPartIdentifier(jobId, batchNr)))),
        processingActorProps = Some(taskExecutionWorkerProps),
        AggregatorConfig(
          filteringSingleElementMapperForAggregator = new AcceptAllAsIdentityMapper[ProcessingMessage[Any]],
          filterAggregationMapperForAggregator = new AcceptAllAsIdentityMapper[U],
          filteringMapperForResultSending = new AcceptAllAsIdentityMapper[U],
          aggregatorSupplier = aggregatorSupplier
        ),
        // right now the expectation bound to each ActorRunnable is to provide one result per processed element
        // back to the actor executing the runnable, without StopExpectation. TimeExpectation set to threshold of 10 min
        expectationGenerator = _ => BaseExecutionExpectation(
          Seq(ClassifyingCountExpectation(classifier = Map("ALL" -> (_ => true)),
            expectedClassCounts = Map("ALL" -> x.nrOfElements))),
          Seq(StopExpectation(x.nrOfElements, _ => SuccessAndErrorCounts(1, 0), _ => false),
            TimeExpectation(timeoutPerRunnable))
        ),
        sinkType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
        timeoutPerRunnable,
        timeoutPerElement,
        sendResultsBack = sendResultsBack)
    })
  }

}
