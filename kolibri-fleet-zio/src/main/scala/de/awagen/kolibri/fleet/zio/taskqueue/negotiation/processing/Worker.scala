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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing

import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import zio.{Fiber, Task}

/**
 * Instances of Worker take care of the actual computations.
 */
trait Worker {

  /**
   * The computation of a task. Returns tuple of the aggregator ref and the fiber for the running task.
   * The aggregator will not have all data before the fiber.status is done.
   * By returning the fiber here we are also able to interrupt it in case it is not needed anymore.
   */
  def work[T, V, W <: WithCount](jobBatch: JobBatch[T, V, W]): Task[(Aggregator[TaggedWithType with DataPoint[V], W], Fiber.Runtime[Any, Any])]

}
