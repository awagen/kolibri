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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader

import ClaimReader.ClaimTopic.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus
import zio.Task


object ClaimReader {

  object ClaimTopic extends Enumeration {
    type ClaimTopic = Value

    val JOB_TASK_PROCESSING_CLAIM,
    JOB_TASK_WRAP_UP_CLAIM,
    JOB_TASK_RESET_CLAIM,
    UNKNOWN = Value
  }

}

trait ClaimReader {

  def getAllClaims(jobIds: Set[String], claimTopic: ClaimTopic): Task[Set[Claim]]

  def getClaimsForJob(jobId: String, claimTopic: ClaimTopic): Task[Seq[Claim]]

  def getClaimsForBatch(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[Set[Claim]]

  def getClaimsByCurrentNode(claimTopic: ClaimTopic, openJobsSnapshot: OpenJobsSnapshot): Task[Set[Claim]]

  def verifyBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimVerifyStatus]




}
