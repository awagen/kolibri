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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import zio.Task


/**
 * Manages the pickup of in-progress states for tasks in "PLANNED" status
 * and handling of stopping running tasks if indicated and filling open processing
 * slots with new tasks if resources freed.
 */
trait WorkHandlerService {

  /**
   * Manage batches in the following steps:
   * - pick up the in-progress states with state PLANNED. Put into processing queue.
   * - clear finished batches and fill empty processing slots with tasks from queue
   * (as soon as any batch for particular job is started first, initially load the global
   * resources defined for that job)
   * - for those tasks not in the in-progress state folder anymore, make sure to kill
   * the corresponding job (except if the reading of states causes error, in that
   * case do not kill)
   */
  def manageBatches(openJobSnapshot: OpenJobsSnapshot): Task[Unit]

  /**
   * Update the current state of in-progress files for this node.
   * In case status of a batch does not change, at least the timestamp will change
   * so that other nodes can decide whether a task must be taken from the node by
   * removing in-progress state and moving batch back to "open" to be claimed by any node.
   */
  def updateProcessStates: Task[Unit]

}
