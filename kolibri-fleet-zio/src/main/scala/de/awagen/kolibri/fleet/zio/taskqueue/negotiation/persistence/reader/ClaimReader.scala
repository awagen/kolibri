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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics.JobTaskResetClaim.typePrefix
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus
import zio.Task


object ClaimReader {

  object ClaimTopics {

    sealed trait ClaimTopic

    case object JobTaskProcessingClaim extends ClaimTopic {

      val id: String = "JOB_TASK_PROCESSING_CLAIM"

      override def toString: String = id

    }

    case object JobWrapUpClaim extends ClaimTopic {

      val id: String = "JOB_WRAP_UP_CLAIM"

      override def toString: String = id

    }

    object JobTaskResetClaim {

      val argumentDelimiter = "-"
      val typePrefix = "JOB_TASK_RESET_CLAIM"

      def fromString(str: String): JobTaskResetClaim = {
        JobTaskResetClaim(str.split(argumentDelimiter)(1))
      }

    }

    sealed case class JobTaskResetClaim(nodeHash: String) extends ClaimTopic {

      override def toString: String = s"$typePrefix-$nodeHash"

    }

    case object Unknown extends ClaimTopic {

      val id: String = "UNKNOWN"

      override def toString: String = id

    }

  }

  def stringToClaimTopic(str: String): ClaimTopic = str match {
    case JobTaskProcessingClaim.id => JobTaskProcessingClaim
    case JobWrapUpClaim.id => JobWrapUpClaim
    case v if v.startsWith(JobTaskResetClaim.typePrefix) => JobTaskResetClaim.fromString(str)
    case _ => Unknown
  }

}

trait ClaimReader {

  def getAllClaims(jobIds: Set[String], claimTopic: ClaimTopic): Task[Set[Claim]]

  def getClaimsForJob(jobId: String, claimTopic: ClaimTopic): Task[Seq[Claim]]

  def getClaimsForBatch(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[Set[Claim]]

  def getClaimsByCurrentNode(claimTopic: ClaimTopic, jobIds: Set[String]): Task[Set[Claim]]

  def verifyBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimVerifyStatus]




}
