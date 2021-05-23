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

package de.awagen.kolibri.base.processing.execution.task.utils

import akka.actor.Props
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.BatchTypeTaggedMapGenerator
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.BaseJobPartIdentifier
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.actors.work.worker.TaskExecutionWorkerActor.ProcessTaskExecution
import de.awagen.kolibri.base.processing.execution.SimpleTaskExecution
import de.awagen.kolibri.base.processing.execution.expectation.{BaseExecutionExpectation, ClassifyingCountExpectation, StopExpectation, TimeExpectation}
import de.awagen.kolibri.base.processing.execution.job.{ActorRunnable, ActorRunnableSinkType}
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.FiniteDuration

object TaskUtils {

  def tasksToActorRunnable[U](jobId: String,
                              resultKey: ClassTyped[ProcessingMessage[Any]],
                              mapGenerator: BatchTypeTaggedMapGenerator,
                              tasks: Seq[Task[_]],
                              aggregatorSupplier: SerializableSupplier[Aggregator[ProcessingMessage[Any], U]],
                              taskExecutionWorkerProps: Props,
                              timeoutPerRunnable: FiniteDuration,
                              timeoutPerElement: FiniteDuration): IndexedGenerator[ActorRunnable[SimpleTaskExecution[Any], Any, Any, U]] = {
    val executionIterable: IndexedGenerator[IndexedGenerator[SimpleTaskExecution[Any]]] =
      mapGenerator.mapGen(x => x.data.mapGen(y => SimpleTaskExecution(resultKey, y, tasks)))
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
        // right now the expectation bound to each ActorRunnable is to provide one result per processed element
        // back to the actor executing the runnable, without StopExpectation. TimeExpectation set to threshold of 10 min
        expectationGenerator = _ => BaseExecutionExpectation(
          Seq(ClassifyingCountExpectation(classifier = Map("ALL" -> (_ => true)),
            expectedClassCounts = Map("ALL" -> x.nrOfElements))),
          Seq(StopExpectation(x.nrOfElements, _ => false, _ => false),
            TimeExpectation(timeoutPerRunnable))
        ),
        aggregationSupplier = aggregatorSupplier,
        returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
        timeoutPerRunnable,
        timeoutPerElement)
    })
  }

}
