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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.Aggregator
import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.definitions.domain.jobdefinitions.Batch
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask

object JobDefinitions {

  case class BatchAggregationInfo[W](successKey: ClassTyped[ProcessingMessage[Any]],
                                     batchAggregatorSupplier: () => Aggregator[ProcessingMessage[Any], W])

  /**
   * Job definition here encapsulates several parts:
   * a) initial setup of node resources. Might be needed to load frequently queries, high volume data
   * into node memory before starting the actual execution,
   * b) generator of elements to process. This also provide the batching strategy on its partitions
   * c) sequence of tasks, where both tagging information on the element as well as data generated
   * during the task are passed downstream to enable iterative steps of execution,
   * e.g one or more request steps (request + parsing of needed info into Map fields), followed
   * by processing steps and a final write result step.
   */
  case class JobDefinition[+T, W](jobName: String,
                                  resourceSetup: Seq[ResourceDirective[_]],
                                  batches: IndexedGenerator[Batch[T]],
                                  taskSequence: Seq[ZIOTask[_]],
                                  aggregationInfo: Option[BatchAggregationInfo[W]])

  def simpleWaitJob(jobName: String, nrBatches: Int, waitDurationInMillis: Long): JobDefinition[Int, Unit] = {
    JobDefinition[Int, Unit](
      jobName = jobName,
      resourceSetup = Seq.empty,
      batches = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        Range(0, nrBatches, 1)
          .map(batchNr => Batch(batchNr, ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(1))))
      ),
      taskSequence = Seq(SimpleWaitTask(waitDurationInMillis)),
      aggregationInfo = None
    )
  }

  case class JobBatch[+T, W](job: JobDefinition[T, W], batchNr: Int)

}
