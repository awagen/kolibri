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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics.TaskTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.TaskStates.Task
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus

trait ClaimWriter {

  def fileClaim(processId: ProcessId, taskTopic: TaskTopic): zio.Task[ClaimFilingStatus]

  def writeTaskToProgressFolder(processId: ProcessId): zio.Task[Any]

  def removeClaims(existingClaims: Set[Task], claimURIFilter: Task => Boolean): zio.Task[Unit]

  def exerciseBatchClaim(process: ProcessId): zio.Task[Unit]

}
