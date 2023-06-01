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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopic.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import zio.Task

trait ClaimWriter {

  def fileBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic, existingClaimsForBatch: Set[Claim]): Task[ClaimFilingStatus]

  def writeTaskToProgressFolder(jobId: String, batchNr: Int): Task[Any]

  def removeClaims(existingClaimFiles: Set[Claim], claimURIFilter: Claim => Boolean): Task[Unit]

  def exerciseBatchClaim(jobId: String,
                         batchNr: Int,
                         existingClaimsForBatch: Set[Claim]): Task[Unit]

}
