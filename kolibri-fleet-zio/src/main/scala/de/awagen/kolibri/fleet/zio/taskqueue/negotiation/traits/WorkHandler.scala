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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ProcessUpdateStatus.ProcessUpdateStatus
import zio.Task

/**
 * Takes care of picking the work status from all workers and update the proccessing state information for all.
 */
trait WorkHandler {

  /**
   * Update the processing status information for each task
   */
  def updateProcessStatus(): Task[ProcessUpdateStatus]

  def addBatches(batches: Seq[JobBatch[_,_]]): Task[Seq[Boolean]]

  def addBatch(batch: JobBatch[_,_]): Task[Boolean]

}
