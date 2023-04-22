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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic.ClaimTopic
import zio._


object ClaimHandler {

  object ClaimTopic extends Enumeration {
    type ClaimTopic = Value

    val JOB_TASK_PROCESSING_CLAIM: Value = Value
    val JOB_TASK_RESET_CLAIM: Value = Value
  }

}

trait ClaimHandler {

  /**
   * File claim for a given batch of a job. Implementation depends on storage, e.g in file system would mean
   * writing a claim file
   */
  def fileClaim(job: Job, claimTopic: ClaimTopic): Task[ClaimFilingStatus]

  /**
   * Verify a filed claim. This is used after a claim was filed to check on it after a given period of time
   * to see whether the claim "won", e.g the node is allowed to pick the claimed batch and execute it.
   * Will either return a CLAIM_ACCEPTED, based on which we can exercise the claim (picking the batch to execute
   * and update the processing status), or returns other states indicating why a claim was not accepted.
   */
  def verifyClaim(job: Job, claimTopic: ClaimTopic): Task[ClaimVerifyStatus]

  /**
   * Should only be used after a successful verification of a files claim. Upon exercising, the node will add the
   * task to its processing queue, and update the state of the batch (remove the status that the batch is open to be
   * picked for processing, put the batch in in-progress, set schedule to regularly update the processing state.
   */
  def exerciseClaim(job: Job, claimTopic: ClaimTopic): Task[ClaimExerciseStatus]

}
