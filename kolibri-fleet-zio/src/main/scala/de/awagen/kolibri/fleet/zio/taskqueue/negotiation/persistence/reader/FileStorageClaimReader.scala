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

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.Directories.Claims._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.ClaimFileNameFormat
import FileStorageClaimReader.ClaimOrdering
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import zio.stream.ZStream
import zio.{Task, ZIO}


object FileStorageClaimReader {

  object ClaimOrdering extends Ordering[Claim] {
    def compare(a: Claim, b: Claim): Int = a.timeClaimedInMillis compare b.timeClaimedInMillis
  }

}


/**
 * Handle reading and validation of claims.
 * Note that the file based handling of task executions follows the following logic:
 * - the main job folder holds the job definition that includes prerequisites in terms of data to load on the node before
 * execution and the actual processing logic and method to create batches
 * - the task folder for the job contains simply one file per batch , e.g 1, 2, 3,...
 * - claims are filed based on the batch. If a file is written, validated and exercised, the node earned the right
 * to process the batch. For this the related batch is loaded from the data specified in the job definition
 * and all data samples evaluated.
 * - after exercising a claim for a batch, a node has to write regular status updates, even if that is just "IN_QUEUE",
 * otherwise the other nodes will claim the right to cleanup the data, e.g move the batch back to open, and remove
 * all status info. This allows nodes to claim the batch again.
 *
 * The claim reader is only responsible for reading claims and
 * validating whether the node won the right to execute specific batches.
 *
 * NOTE that also other tasks are subject to claiming,
 * e.g in case some node has not written any in-progress state updates,
 * then the "open" state for a job has to be restored again.
 * For this some node needs to claim cleanup rights and then execute it.
 * This type of control process is still to be implemented though
 * for the cleanup case.
 */
case class FileStorageClaimReader(filterToOverviewReader: (String => Boolean) => DataOverviewReader,
                                  reader: Reader[String, Seq[String]]) extends ClaimReader {

  private[this] val overviewReader: DataOverviewReader = filterToOverviewReader(_ => true)

  override def getAllClaims(jobIds: Set[String], claimTopic: ClaimTopic): Task[Set[Claim]] =
    ZStream.fromIterable(jobIds)
      .mapZIO(jobId => getClaimsForJob(jobId, claimTopic))
      .runFold[Set[Claim]](Set.empty)((oldSet, newItem) => {oldSet ++ newItem.toSet})

  /**
   * Over all currently registered jobs, find the claims filed by this node
   */
  override def getClaimsByCurrentNode(claimTopic: ClaimTopic, jobIds: Set[String]): Task[Set[Claim]] = {
      ZStream.fromIterable(jobIds)
        .mapZIO(job => {
          getClaimsForJob(job, claimTopic)
            .map(seq => seq.filter(x => x.nodeId == AppProperties.config.node_hash))
        })
        .runFold(Set.empty[Claim])((oldSet, newValue) => oldSet ++ newValue)
  }

  /**
   * Get all full claim paths for the passed jobId and the given claimTopic
   */
  override def getClaimsForJob(jobId: String, claimTopic: ClaimTopic): Task[Seq[Claim]] = ZIO.attemptBlockingIO {
    overviewReader
      .listResources(jobClaimSubFolder(jobId, isOpenJob = true), _ => true)
      .map(filename => ClaimFileNameFormat.claimFromIdentifier(filename.split("/").last))
      .filter(claim => claim.claimTopic == claimTopic)
  }

  override def getClaimsForBatch(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[Set[Claim]] = {
    getClaimsForJob(jobId, claimTopic)
      .map(claimSeq => claimSeq.filter(claim => claim.batchNr == batchNr).toSet)
  }


  /**
   * Verify a filed claim. This is used after a claim was filed to check on it after a given period of time
   * to see whether the claim "won", e.g the node is allowed to pick the claimed batch and execute it.
   * Will either return a CLAIM_ACCEPTED, based on which we can exercise the claim (picking the batch to execute
   * and update the processing status), or returns other states indicating why a claim was not accepted.
   *
   * Verify whether a filed claim succeeded and if so (CLAIM_ACCEPTED)
   * indicates that the node can start moving the planned task to in-progress folder
   * to be picked up by the WorkHandler.
   */
  override def verifyBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimVerifyStatus] =  {
    val node_hash = AppProperties.config.node_hash
    implicit val ordering: Ordering[Claim] = ClaimOrdering
    for {
      claimNodeHashesSortedByTimestamp <- getClaimsForBatch(jobId, batchNr, claimTopic)
        .map(claims => claims.toSeq.sorted.map(x => x.nodeId))
      nodeHadFiledClaim <- ZIO.succeed(claimNodeHashesSortedByTimestamp.contains(node_hash))
      nodeClaimIsFirst <-  ZIO.succeed(claimNodeHashesSortedByTimestamp.head == node_hash)
    } yield (nodeHadFiledClaim, nodeClaimIsFirst, claimNodeHashesSortedByTimestamp.nonEmpty) match {
      case (_, _, false) => ClaimVerifyStatus.NO_CLAIM_EXISTS
      case (false, _, true) => ClaimVerifyStatus.NODE_CLAIM_DOES_NOT_EXIST
      case (_, false, true) => ClaimVerifyStatus.OTHER_CLAIMED_EARLIER
      case (true, true, true) => ClaimVerifyStatus.CLAIM_ACCEPTED
    }

  }

}
