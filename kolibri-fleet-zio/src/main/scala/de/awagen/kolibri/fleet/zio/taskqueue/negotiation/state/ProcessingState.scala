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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.InProgressTaskFileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus.ProcessingStatus


object ProcessingStatus extends Enumeration {
  type ProcessingStatus = Value
  val PLANNED: Value = Value
  val QUEUED: Value = Value
  val IN_PROGRESS: Value = Value
  val DONE: Value = Value
  val ABORTED: Value = Value
}

case class ProcessId(jobId: String,
                     batchNr: Int) {

  def toIdentifier: String = InProgressTaskFileNameFormat
    .getFileName(batchNr)

}

case class ProcessingInfo(processingStatus: ProcessingStatus,
                          numItemsTotal: Int,
                          numItemsProcessed: Int,
                          processingNode: String,
                          lastUpdate: String)

case class ProcessingState(stateId: ProcessId,
                           processingInfo: ProcessingInfo) {


}
