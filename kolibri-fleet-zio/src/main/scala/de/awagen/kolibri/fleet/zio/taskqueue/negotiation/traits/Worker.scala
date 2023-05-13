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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits

import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.Aggregator
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.WorkStatus.WorkStatus
import zio.{Task, ZIO}

/**
 * Instances of Worker take care of the actual computations and provide the status of computation.
 */
trait Worker {

  /**
   * The computation of a task
   *
   * @return
   */
  def work[T, W](jobBatch: JobBatch[T,W]): ZIO[Any, Nothing, Option[Aggregator[ProcessingMessage[Any], W]]]

  /**
   * Returns current work status for the given task
   *
   * @return
   */
  def status(): Task[WorkStatus]

}
