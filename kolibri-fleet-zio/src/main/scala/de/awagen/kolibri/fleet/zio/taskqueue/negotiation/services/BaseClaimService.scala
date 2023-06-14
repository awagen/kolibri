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

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.maxNrJobsClaimed
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, JobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.BaseClaimService.{DUMMY_BATCH_NR, isCurrentNodeClaim}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingState, ProcessingStateUtils}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import zio.stream.{ZSink, ZStream}
import zio.{Task, ZIO}

object BaseClaimService {

  val DUMMY_BATCH_NR: Int = -1

  private def isCurrentNodeClaim(claim: Claim): Boolean = {
    claim.nodeId == AppProperties.config.node_hash
  }

}

case class BaseClaimService(claimReader: ClaimReader,
                            claimUpdater: ClaimWriter,
                            workStateReader: WorkStateReader,
                            workStateWriter: WorkStateWriter,
                            jobStateWriter: JobStateWriter) extends ClaimService {

  /**
   * Given a range of claims, file all of them (e.g "apply for eligibility to perform the claimed task")
   */
  private[services] def fileBatchProcessingClaims(processIds: Seq[ProcessId]): Task[Unit] = {
    for {
      fileBatchClaimResults <- ZStream.fromIterable(processIds).foreach(id => {
        claimUpdater.fileClaim(id, ClaimTopics.JobTaskProcessingClaim)
      })
    } yield fileBatchClaimResults
  }

  /**
   * Filing claim for resetting a batch processing state for another node.
   */
  private[services] def fileBatchResetClaim(processId: ProcessId, nodeHash: String): Task[ClaimFilingStatus] = {
    claimUpdater.fileClaim(processId, ClaimTopics.JobTaskResetClaim(nodeHash))
  }

  /**
   * Filing claim for moving the batch status data to done
   */
  private[services] def fileJobToDoneClaim(jobId: String): Task[ClaimFilingStatus] = {
    claimUpdater.fileClaim(ProcessId(jobId, DUMMY_BATCH_NR), ClaimTopics.JobWrapUpClaim)
  }

  /**
   * Given any claim, pick the right method to file the claim
   */
  private[services] def fileClaim(claim: Claim): Task[Unit] = {
    claim.claimTopic match {
      case ClaimTopics.JobTaskProcessingClaim =>
        fileBatchProcessingClaims(Seq(ProcessId(claim.jobId, claim.batchNr)))
      case ClaimTopics.JobTaskResetClaim(nodeHash) =>
        fileBatchResetClaim(ProcessId(claim.jobId, claim.batchNr), nodeHash).map(_ => ())
      case ClaimTopics.JobWrapUpClaim =>
        fileJobToDoneClaim(claim.jobId).map(_ => ())
    }
  }

  /**
   * Given a Seq of any claims, file all of them
   */
  private[services] def fileClaims(claims: Seq[Claim]): Task[Unit] = {
    ZStream.fromIterable(claims).foreach(fileClaim)
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
   * deletes all claims that match the job / batchNr / claimTopic combination of the
   * passed claim. First deletes those claims from other node and then the one of the current node.
   * The assumption here is that the node calling for deletion of the claims actually won
   * the claim, thus do only use after positive result from verifyClaim
   */
  private[services] def deleteAllClaimsForMatchingJobAndBatchAndClaimTopic(claim: Claim): Task[Unit] = {
    for {
      // get all claims for the same job / batch / claimTopic combination as the passed claim
      existingClaimsForBatch <- claimReader.getClaimsForBatch(claim.jobId, claim.batchNr, claim.claimTopic)
      // first delete claims belonging to other nodes to avoid a claim for another node remaining if the winner
      // claim is already deleted
      _ <- claimUpdater.removeClaims(existingClaimsForBatch, x => !isCurrentNodeClaim(x))
      // also delete the claim of the current node (the one who won the claim)
      _ <- claimUpdater.removeClaims(existingClaimsForBatch, isCurrentNodeClaim)
    } yield ()
  }


  /**
   * Take distinct exercise action depending on the ClaimTopic files.
   * - JobTaskProcessingClaim: write in-progress state with status PLANNED for the batch for current node,
   * delete the other claims
   * - JobTaskResetClaim: means that some node did not in time update the processing state of a batch it was
   * processing. In that case we need to delete the in-progress file for that particular node, write the batch
   * file back to the "open" folder for the job to be claimed by other nodes.
   * - JobWrapUpClaim: moving job from open-jobs to done-jobs
   * - Unknown: do nothing
   *
   */
  private[services] def exerciseClaim(claim: Claim): Task[Unit] = {
    claim.claimTopic match {
      case ClaimTopics.JobTaskProcessingClaim =>
        for {
          // exercising the claim and deleting the claim files
          _ <- claimUpdater.exerciseBatchClaim(ProcessId(claim.jobId, claim.batchNr))
          // delete claims for respective job
          _ <- deleteAllClaimsForMatchingJobAndBatchAndClaimTopic(claim)
        } yield ()
      case ClaimTopics.JobTaskResetClaim(nodeHash) =>
        for {
          // write a file for the batch to reset back to open-folder
          _ <- jobStateWriter.writeBatchToOpen(ProcessId(claim.jobId, claim.batchNr))
          _ <- workStateWriter.deleteInProgressState(ProcessId(claim.jobId, claim.batchNr), nodeHash)
          // clean up all claims for this particular task
          _ <- deleteAllClaimsForMatchingJobAndBatchAndClaimTopic(claim)
        } yield ()
      case ClaimTopics.JobWrapUpClaim =>
        for {
          // wrap up whole job by moving the full job folder to done state
          _ <- jobStateWriter.moveToDone(claim.jobId)
          // remove all claims
          _ <- deleteAllClaimsForMatchingJobAndBatchAndClaimTopic(claim)
        } yield ()
      case _ =>
        for {
          // not a covered claim, thus do nothing but removing existing claims
          _ <- deleteAllClaimsForMatchingJobAndBatchAndClaimTopic(claim)
        } yield ()
    }
  }

  /**
   * Exercise the given claims. This usually involves changes to the persisted processing state.
   */
  private[services] def exerciseClaims(claims: Seq[Claim]): Task[Unit] = {
    ZStream.fromIterable(claims).foreach(claimInfo => exerciseClaim(claimInfo))
  }

  /**
   * Check of there is room to claim more batch processing tasks. If yes, file claims for those.
   */
  private[services] def fillUpBatchProcessingClaims(openJobsSnapshot: OpenJobsSnapshot): Task[Unit] = {
    for {
      // open job ids used to limit the retrieved state data to
      jobIds <- ZIO.succeed(openJobsSnapshot.jobStateSnapshots.values.map(x => x.jobId).toSet)
      // Set[Claim] representing the claims made by the current node
      existingClaimsForNode <- claimReader.getClaimsByCurrentNode(
        ClaimTopics.JobTaskProcessingClaim,
        jobIds
      )
      // fetching all files showing successfully claimed (exercised claims in the form of
      // files in the in-progress state subfolder) batches as full file paths to the in-progress files
      inProgressStateFilesPerJobForThisNode <- workStateReader.getInProgressIdsForCurrentNode(jobIds)
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
        jobIds,
        ClaimTopics.JobTaskProcessingClaim
      ).map(x => x.groupBy(claim => claim.jobId).map(x => (x._1, x._2.map(y => y.batchNr))))
      nNextOpenBatches <- ZIO.attempt(openJobsSnapshot.getNextNOpenBatches(numberOfNewlyClaimableBatches, existingClaims))
      nNextOpenProcessIds <- ZIO.attempt(nNextOpenBatches.flatMap(defWithBatches => {
        defWithBatches._2.map(batchNr => ProcessId(defWithBatches._1.jobName, batchNr))
      }))
      _ <- fileBatchProcessingClaims(nNextOpenProcessIds)
    } yield ()
  }

  /**
   * checks if task reset claim already exists for passed processing state,
   * and if not files the claim.
   * TODO: better getting all relevant claims at once and then filtering instead of checking for each candidate (IO)
   */
  private[services] def fileTaskResetClaimIfDoesntExist(state: ProcessingState): Task[Unit] = {
    for {
      // check if any claims already exist
      existingClaims <- claimReader.getAllClaims(
        Set(state.stateId.jobId),
        ClaimTopics.JobTaskResetClaim(state.processingInfo.processingNode)
      )
      // if claim does not exist, file it
      _ <- ZIO.when(existingClaims.isEmpty)(
        claimUpdater.fileClaim(
          ProcessId(state.stateId.jobId, state.stateId.batchNr),
          ClaimTopics.JobTaskResetClaim(state.processingInfo.processingNode)
        )
      )
    } yield ()
  }

  /**
   * Check if there is any in-progress task state for any node that has not been updated recent enough.
   * If so and in case there is no claim for this yet, file the claim (we do not need limitation here,
   * as its not a longer computation but just a small state persistence)
   *
   * @param processingStates : processingStates reflecting all currently persisted processing states
   */
  private[services] def fillUpTaskResetClaims(processingStates: Set[ProcessingState]): Task[Unit] = {
    for {
      // current time to filter out those in-progress states that were not updated for too long
      currentTimeInMillis <- ZIO.succeed(System.currentTimeMillis())
      // filtering for in-progress states with overdue updates
      overdueProcessingStates <- ZStream.fromIterable(processingStates)
        .filter(state => {
          val timeUpdated: Long = ProcessingStateUtils.timeStringToTimeInMillis(state.processingInfo.lastUpdate)
          currentTimeInMillis - timeUpdated > AppProperties.config.maxTimeBetweenProgressUpdatesInSeconds
        })
        .runCollect
      _ <- ZStream.fromIterable(overdueProcessingStates)
        .foreach(state => fileTaskResetClaimIfDoesntExist(state))
    } yield ()
  }

  /**
   * File claim to move job state to done.
   * This needs claiming when a) its not yet claimed and b) all open tasks in job are completed
   *
   * @param jobIdsToMove : the jobIds for which to claim moving it to done (condition: there can be no open batch or
   *                     running batch be remaining for the job; this condition is not checked here, we only check whether
   *                     any other node has claimed it so far for the job and do not claim if another claim exists)
   */
  private[services] def fillUpJobToDoneClaims(openJobsSnapshot: OpenJobsSnapshot): Task[Unit] = {
    for {
      // extract all jobIds that are in open state but that have no open or in-progress batches anymore
      jobIdsWithoutUnfinishedBusiness <- ZIO.attempt({
        openJobsSnapshot.jobStateSnapshots.values.filter(x => {
          !x.batchesToState.values.exists(state => state != BatchProcessingStates.Done)
        }).map(x => x.jobId).toSet
      })
      // jobs that already have a respective claim filed by some node
      jobsWithExistingWrapUpClaims <- claimReader
        .getAllClaims(jobIdsWithoutUnfinishedBusiness, ClaimTopics.JobWrapUpClaim).map(x => x.map(y => y.jobId))
      // extract jobIds for which a wrap-up claim shall be filed
      claimForJobIds <- ZIO.attempt(jobIdsWithoutUnfinishedBusiness -- jobsWithExistingWrapUpClaims)
      // file claims
      _ <- ZStream.fromIterable(claimForJobIds).foreach(jobId => {
        claimUpdater.fileClaim(ProcessId(jobId, DUMMY_BATCH_NR), ClaimTopics.JobWrapUpClaim)
      })
    } yield ()
  }

  private[services] def fillUpClaims(claimTopic: ClaimTopic,
                                     openJobSnapshot: OpenJobsSnapshot,
                                     processingStates: Set[ProcessingState]): Task[Unit] = {
    claimTopic match {
      case ClaimTopics.JobTaskProcessingClaim =>
        fillUpBatchProcessingClaims(openJobSnapshot)
      case ClaimTopics.JobTaskResetClaim(_) =>
        fillUpTaskResetClaims(processingStates)
      case ClaimTopics.JobWrapUpClaim =>
        fillUpJobToDoneClaims(openJobSnapshot)
      case _ =>
        ZIO.succeed(())
    }
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
      claimsByCurrentNode <- claimReader.getClaimsByCurrentNode(claimTopic, openJobsSnapshot.jobStateSnapshots.values.map(x => x.jobId).toSet)
      //verify each claim
      // remove the claims that are not needed anymore (only for this particular node)
      claimsToBeRemoved <- ZIO.succeed(claimsByCurrentNode.filter(x => ignoreJobIdsOnThisNode.contains(x.jobId)))
      _ <- claimUpdater.removeClaims(claimsToBeRemoved, _ => true)
      // for those jobs that shall be ignored, also remove all process state files and move the files representing the
      // respective batches back to the 'open' folder
      verifiedClaims <- verifyClaimsAndReturnSuccessful(claimsByCurrentNode, claimTopic)
      // Now exercise all verified claims
      _ <- exerciseClaims(verifiedClaims)
      // extract all processing states
      processingStates <- workStateReader.getInProgressStateForAllNodes(openJobsSnapshot.jobStateSnapshots.keySet)
        .map(x => x.values.flatMap(y => y.values).flatten.toSet)
      // add claims for given claimTopic (if any)
      _ <- fillUpClaims(claimTopic, openJobsSnapshot, processingStates)
    } yield ()
  }

  override def getAllClaims(jobIds: Set[String], claimTopic: ClaimTopic): Task[Set[Claim]] = claimReader.getAllClaims(jobIds, claimTopic)
}
