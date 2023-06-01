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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopic.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.ClaimWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import zio.stream.{ZSink, ZStream}
import zio.{Task, ZIO}

case class BaseClaimService(claimReader: ClaimReader,
                            claimUpdater: ClaimWriter,
                            workStateReader: WorkStateReader) extends ClaimService {


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
      verifiedClaims <- ZStream.fromIterable(claimsByCurrentNode).filterZIO(claimInfo => {
        claimReader.verifyBatchClaim(claimInfo.jobId, claimInfo.batchNr, claimTopic)
          .either
          .map({
            case Right(v) => v
            case Left(_) => ClaimVerifyStatus.FAILED_VERIFICATION
          })
          .map(status => status == ClaimVerifyStatus.CLAIM_ACCEPTED)
      })
        .run(ZSink.foldLeft(Seq.empty[Claim])((oldState, newElement) => oldState :+ newElement))
      // Now exercise all verified claims
      _ <- ZStream.fromIterable(verifiedClaims).foreach(claimInfo => {
        val existingClaimsForBatch = claimReader.getClaimsForBatch(claimInfo.jobId, claimInfo.batchNr, claimInfo.claimTopic)
        existingClaimsForBatch.map(claimSet => {
          claimUpdater.exerciseBatchClaim(claimInfo.jobId, claimInfo.batchNr, claimSet)
        }).map(_ => ())
      })
      // Set[Claim] representing the claims made by the current node
      existingClaimsForNode <- claimReader.getClaimsByCurrentNode(claimTopic, openJobsSnapshot)
      // fetching all files showing succesfully claimed (exercised claims in the form of
      // files in the in-progress state subfolder) batches as full file paths to the in-progress files
      inProgressStateFilesPerJobForThisNode <- workStateReader.getInProgressIdsForNode(openJobsSnapshot.jobStateSnapshots.keys.toSet)
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
        ClaimTopic.JOB_TASK_PROCESSING_CLAIM
      ).map(x => x.groupBy(claim => claim.jobId).map(x => (x._1, x._2.map(y => y.batchNr)))
      )
      _ <- ZIO.attempt(openJobsSnapshot.getNextNOpenBatches(numberOfNewlyClaimableBatches, existingClaims))
        .flatMap(openBatchesToClaim => {
          val jobIdToBatchPairs: Seq[(String, Int)] = openBatchesToClaim
            .flatMap(x => {
              x._2.map(batchNr => (x._1.jobName, batchNr))
            })
          ZStream.fromIterable(jobIdToBatchPairs)
            .foreach(el => {
              claimUpdater.fileBatchClaim(el._1, el._2, ClaimTopic.JOB_TASK_PROCESSING_CLAIM, Set.empty)
            })
        })
    } yield ()
  }
}
