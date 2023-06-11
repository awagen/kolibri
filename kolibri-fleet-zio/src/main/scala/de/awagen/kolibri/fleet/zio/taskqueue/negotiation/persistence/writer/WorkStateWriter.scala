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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingState}
import zio.Task

trait WorkStateWriter {

  /**
   * Update the process state information for a particular batch.
   * Assumes that this node also processes the particular batch,
   * since ProcessingState contains information about
   */
  def updateInProgressState(processingState: ProcessingState): Task[Unit]

  /**
   * Deletes persisted state for the passed processId.
   */
  def deleteInProgressState(processId: ProcessId, nodeHash: String): Task[Unit]

  /**
   * Persist the corresponding batch as DONE
   * (e.g in case of file-based state, this means storing a file in the done-folder for the passed batch)
   */
  def writeToDone(processId: ProcessId): Task[Unit]

}
