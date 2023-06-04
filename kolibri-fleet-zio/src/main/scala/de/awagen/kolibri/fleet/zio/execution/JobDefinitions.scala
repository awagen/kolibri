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
import de.awagen.kolibri.datatypes.tagging.{TaggedWithType, Tags}
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.Aggregator
import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.definitions.domain.jobdefinitions.Batch
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.{SimpleWaitTask, SimpleWaitTaskResultAsProcessingMessage}
import de.awagen.kolibri.storage.io.writer.Writers.Writer

object JobDefinitions {

  def doNothingWriter[W]: Writer[W, Tags.Tag, _] = {
    new Writer[W, Tags.Tag, Any] {
      override def write(data: W, targetIdentifier: Tags.Tag): Either[Exception, Any] = Right(())

      override def delete(targetIdentifier: Tags.Tag): Either[Exception, Any] = Right(())

      override def copyDirectory(dirPath: String, toDirPath: String): Unit = ()

      override def moveDirectory(dirPath: String, toDirPath: String): Unit = ()
    }
  }

  /**
   * Aggregation info that needs the key where to pick the value resulting from the computation from.
   * This success key can either refer to entry of type V or of V wrapped in a ProcessingMessage.
   * The aggregator always takes a ProcessingMessage, which provides means to selectively tag
   * results and - depending on the chosen aggregator - allows aggregating via distinct tag (or group of tags)
   */
  case class BatchAggregationInfo[V, W](successKey: Either[ClassTyped[V], ClassTyped[ProcessingMessage[V]]],
                                        batchAggregatorSupplier: () => Aggregator[TaggedWithType with DataPoint[V], W],
                                        writer: Writer[W, Tags.Tag, Any] = doNothingWriter[W])

  case class ValueWithCount[T](value: T, count: Int) extends WithCount

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
  case class JobDefinition[+T, V, W <: WithCount](jobName: String,
                                                  resourceSetup: Seq[ResourceDirective[_]],
                                                  batches: IndexedGenerator[Batch[T]],
                                                  taskSequence: Seq[ZIOTask[_]],
                                                  aggregationInfo: BatchAggregationInfo[V, W])

  def simpleWaitJob(jobName: String,
                    nrBatches: Int,
                    waitDurationInMillis: Long,
                    elementsPerBatch: Int,
                    aggregationInfo: BatchAggregationInfo[Unit, ValueWithCount[Int]]): JobDefinition[Int, Unit, ValueWithCount[Int]] = {
    JobDefinition[Int, Unit, ValueWithCount[Int]](
      jobName = jobName,
      resourceSetup = Seq.empty,
      batches = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        Range(0, nrBatches, 1)
          .map(batchNr => Batch(batchNr, ByFunctionNrLimitedIndexedGenerator.createFromSeq(Range(0, elementsPerBatch, 1))))
      ),
      taskSequence = Seq(SimpleWaitTask(waitDurationInMillis)),
      aggregationInfo = aggregationInfo
    )
  }

  def simpleWaitJobResultAsProcessingMessage(jobName: String,
                                             nrBatches: Int,
                                             waitDurationInMillis: Long,
                                             elementsPerBatch: Int,
                                             aggregationInfo: BatchAggregationInfo[Unit, ValueWithCount[Int]]): JobDefinition[Int, Unit, ValueWithCount[Int]] = {
    JobDefinition[Int, Unit, ValueWithCount[Int]](
      jobName = jobName,
      resourceSetup = Seq.empty,
      batches = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        Range(0, nrBatches, 1)
          .map(batchNr => Batch(batchNr, ByFunctionNrLimitedIndexedGenerator.createFromSeq(Range(0, elementsPerBatch, 1))))
      ),
      taskSequence = Seq(SimpleWaitTaskResultAsProcessingMessage(waitDurationInMillis)),
      aggregationInfo = aggregationInfo
    )
  }

  case class JobBatch[+T, V, W <: WithCount](job: JobDefinition[T, V, W], batchNr: Int)

}
