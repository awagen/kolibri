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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.Jobs.Job
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimExerciseStatus.ClaimExerciseStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus

/**
 * Finds tasks for which status updates are overdue and thus need resetting by moving task back to open status
 * and removing in-process state for the node that has tried to process the task last
 * NOTE: could handle via placement of cleanup note, which is then claimed after some interval by actually
 * changing the state. Ensures that only one node changes the state
 */
trait StateCleaner {

  def findCleanUpTasks: Either[Exception, Seq[Job]]

  def fileCleanupClaim(job: Job): ClaimFilingStatus

  def verifyCleanupClaim(job: Job): ClaimVerifyStatus

  def exerciseCleanupClaim(job: Job): ClaimExerciseStatus

}
