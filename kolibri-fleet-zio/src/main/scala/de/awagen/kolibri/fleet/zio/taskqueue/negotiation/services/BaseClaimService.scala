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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services

import de.awagen.kolibri.fleet.zio.config.AppProperties.config.maxNrJobsClaimed
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, JobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import zio.stream.{ZSink, ZStream}
import zio.{Task, ZIO}

case class BaseClaimService(claimReader: ClaimReader,
                            claimUpdater: ClaimWriter,
                            workStateReader: WorkStateReader,
                            workStateWriter: WorkStateWriter,
                            jobStateWriter: JobStateWriter) extends ClaimService {

  /**
   * Given a range of claims, file all of them (e.g "apply for eligibility to perform the claimed task")
   */
  private[services] def fileBatchClaims(batches: Seq[(JobDefinitions.JobDefinition[_, _, _], Seq[Int])]): Task[Unit] = {
    for {
      processIds <- ZIO.attempt(batches.flatMap(defWithBatches => {
        defWithBatches._2.map(batchNr => ProcessId(defWithBatches._1.jobName, batchNr))
      }))
      fileBatchClaimResults <- ZStream.fromIterable(processIds).foreach(id => {
        claimUpdater.fileBatchClaim(id, ClaimTopics.JobTaskProcessingClaim, Set.empty)
      })
    } yield fileBatchClaimResults
  }

  /**
   * Extract accepted claims from list of all filed claims for the given claim topic
   */
  private[services] def verifyClaimsAndReturnSuccessful(claims: Set[Claim], claimTopic: ClaimTopic): ZIO[Any, Nothing, Seq[Claim]] = {
    ZStream.fromIterable(claims).filterZIO(claimInfo => {
      claimReader.verifyBatchClaim(claimInfo.jobId, claimInfo.batchNr, claimTopic)
        .either
        .map({
          case Right(v) => v
          case Left(_) => ClaimVerifyStatus.FAILED_VERIFICATION
        })
        .map(status => status == ClaimVerifyStatus.CLAIM_ACCEPTED)
    })
      .run(ZSink.foldLeft(Seq.empty[Claim])((oldState, newElement: Claim) => oldState :+ newElement))
  }

  /**
   * Take distinct exercise action depending on the ClaimTopic files.
   * - JOB_TASK_PROCESSING_CLAIM: write in-progress state with status PLANNED for the batch for current node,
   * delete the other claims
   * - JOB_TASK_RESET_CLAIM: means that some node did not in time update the processing state of a batch it was
   * processing. In that case we need to delete the in-progress file for that particular node, write the batch
   * file back to the "open" folder for the job to be claimed by other nodes.
   */
  private[services] def exerciseClaim(claim: Claim): Task[Unit] = {
    claim.claimTopic match {
      case ClaimTopics.JobTaskProcessingClaim =>
        for {
          existingClaimsForBatch <- claimReader.getClaimsForBatch(claim.jobId, claim.batchNr, claim.claimTopic)
          _ <- claimUpdater.exerciseBatchClaim(ProcessId(claim.jobId, claim.batchNr), existingClaimsForBatch)
        } yield ()
      case ClaimTopics.JobTaskResetClaim(nodeHash) =>
        for {
          // write a file for the batch to reset back to open-folder
          _ <- jobStateWriter.writeBatchToOpen(ProcessId(claim.jobId, claim.batchNr))
          _ <- workStateWriter.deleteInProgressState(ProcessId(claim.jobId, claim.batchNr), nodeHash)
        } yield ()
      case ClaimTopics.JobWrapUpClaim =>
        // wrap up whole job by moving the full job folder to done state
        jobStateWriter.moveToDone(claim.jobId)
      case _ =>
        // not a covered claim, thus do nothing
        ZIO.succeed(())
    }
  }

  /**
   * Exercise the given claims. This usually involves changes to the persisted processing state.
   */
  private[services] def exerciseClaims(claims: Seq[Claim]): Task[Unit] = {
    ZStream.fromIterable(claims).foreach(claimInfo => exerciseClaim(claimInfo))
  }

  /**
   * Method to be called by
   * Here we follow the sequence:
   * 0 - check if any claims were made for any job that is now marked to be ignored by this node. If so,
   * delete the respective claims
   * 1 - verify existing claims and exercise the batches that the node claimed successfully
   * 2 - test whether there is any demand for the node to claim additional batches. If so, file additional claims
   */
  def manageClaims(claimTopic: ClaimTopic,
                   openJobsSnapshot: OpenJobsSnapshot): Task[Unit] = {
    for {
      ignoreJobIdsOnThisNode <- ZIO.succeed(openJobsSnapshot.jobsToBeIgnoredOnThisNode.map(x => x.jobId))
      // extract all (jobId, batchNr) tuples of claims currently filed for the node
      claimsByCurrentNode <- claimReader.getClaimsByCurrentNode(claimTopic, openJobsSnapshot)
      //verify each claim
      // remove the claims that are not needed anymore (only for this particular node)
      claimsToBeRemoved <- ZIO.succeed(claimsByCurrentNode.filter(x => ignoreJobIdsOnThisNode.contains(x.jobId)))
      _ <- claimUpdater.removeClaims(claimsToBeRemoved, _ => true)
      // for those jobs that shall be ignored, also remove all process state files and move the files representing the
      // respective batches back to the 'open' folder
      verifiedClaims <- verifyClaimsAndReturnSuccessful(claimsByCurrentNode, claimTopic)
      // Now exercise all verified claims
      _ <- exerciseClaims(verifiedClaims)


      // Set[Claim] representing the claims made by the current node
      existingClaimsForNode <- claimReader.getClaimsByCurrentNode(claimTopic, openJobsSnapshot)
      // fetching all files showing successfully claimed (exercised claims in the form of
      // files in the in-progress state subfolder) batches as full file paths to the in-progress files
      // TODO: picking in-progress info only holds in case the claim topic refers to a batch execution.
      // TODO: also further down on claim writing, decision what to claim is specific to the topics, e.g resetting another
      // node's processing state for a task is another matter than claiming a batch execution
      inProgressStateFilesPerJobForThisNode <- workStateReader.getInProgressIdsForCurrentNode(openJobsSnapshot.jobStateSnapshots.keys.toSet)
      // from the total number of claims filed and the number of batches already claimed calculate
      // whether there is any need to file more claims
      numberOfNewlyClaimableBatches <- ZIO.attempt({
        val nrOfInProgressFiles = inProgressStateFilesPerJobForThisNode.values.flatten.count(_ => true)
        val nrOfFiledClaims = existingClaimsForNode.count(_ => true)
        math.max(0, maxNrJobsClaimed - (nrOfInProgressFiles + nrOfFiledClaims))
      })
      // if there are is some room to claim additional batches, do so (filing the batch claim,
      // while the verification and possible exercising of each claim will be handled in the next
      // round of the manageClaims call
      existingClaims <- claimReader.getAllClaims(
        openJobsSnapshot.jobStateSnapshots.keys.toSet,
        ClaimTopics.JobTaskProcessingClaim
      ).map(x => x.groupBy(claim => claim.jobId).map(x => (x._1, x._2.map(y => y.batchNr)))
      )
      nNextOpenBatches <- ZIO.attempt(openJobsSnapshot.getNextNOpenBatches(numberOfNewlyClaimableBatches, existingClaims))
      _ <- fileBatchClaims(nNextOpenBatches)
    } yield ()
  }
}
