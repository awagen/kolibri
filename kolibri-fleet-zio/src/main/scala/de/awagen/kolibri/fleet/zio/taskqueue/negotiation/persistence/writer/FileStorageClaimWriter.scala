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

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.Directories.Claims.{claimNameToPath, getFullFilePathForClaimFile}
import de.awagen.kolibri.fleet.zio.config.Directories.InProgressTasks.getInProgressFilePathForJob
import de.awagen.kolibri.fleet.zio.config.Directories.OpenTasks
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.ClaimFileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.FileStorageClaimWriter.isCurrentNodeClaim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import zio.{Task, ZIO}

object FileStorageClaimWriter {

  private def isCurrentNodeClaim(claim: Claim): Boolean = {
    claim.nodeId == AppProperties.config.node_hash
  }

}

case class FileStorageClaimWriter(writer: Writer[String, String, _]) extends ClaimWriter {

  /**
   * Write a claim for a given job for a given claim topic (e.g claiming an execution, a cleanup or the like).
   * NOTE that jobId here means [jobName]_[timePlacedInMillis]
   */
  override def fileBatchClaim(processId: ProcessId,
                              claimTopic: ClaimTopic,
                              existingClaimsForBatch: Set[Claim]): Task[ClaimFilingStatus] = {
    ZIO.ifZIO(ZIO.succeed(existingClaimsForBatch.isEmpty))(
      onTrue = {
        // write claim
        for {
          claimPath <- ZIO.attempt(getFullFilePathForClaimFile(processId.jobId, processId.batchNr, claimTopic))
          _ <- ZIO.logDebug(s"writing claim to path: $claimPath")
          persistResult <- ZIO.attemptBlockingIO(writer.write("", claimPath))
          result <- ZIO.fromEither(persistResult).map(_ => ClaimFilingStatus.PERSIST_SUCCESS)
        } yield result
      },
      onFalse = ZIO.succeed(ClaimFilingStatus.OTHER_CLAIM_EXISTS)
    )
  }

  /**
   * Writing a file representing the given batch to the in-progress folder.
   * File is only named by the batch number.
   * The content of the file contains additional information about the process state.
   * Note that when the processing has not yet started, the information about number of elements
   * and the like might be at default values (e.g 0).
   */
  override def writeTaskToProgressFolder(processId: ProcessId): Task[Any] = {
    (for {
      writePath <- ZIO.attempt(getInProgressFilePathForJob(
        processId.jobId,
        processId.batchNr,
        AppProperties.config.node_hash)
      )
      _ <- ZIO.logDebug(s"writing in-progress task to: $writePath")
      toInProgressWriteResult <- ZIO.attemptBlockingIO({
        val processingState = ProcessingState(
          processId,
          ProcessingInfo(
            ProcessingStatus.PLANNED,
            0,
            0,
            AppProperties.config.node_hash,
            ProcessingStateUtils.timeInMillisToFormattedTime(System.currentTimeMillis())
          )
        )
        writer.write(
          ProcessingStateJsonProtocol.processingStateFormat.write(processingState).toString,
          writePath
        )
      })
    } yield toInProgressWriteResult)
      .flatMap({
        case Left(e) => ZIO.fail(e)
        case Right(v) => ZIO.succeed(v)
      })
  }

  /**
   * Remove claims. The passed claim file names can either be just the filenames or the full uri,
   * since we remove all path-info besides the file name for which the actual claim path
   * is then generated before deletion. This makes sure the basepath of the used writer is
   * taken into account (we could also do by deleting any basepath suffix in the paths)
   */
  override def removeClaims(existingClaims: Set[Claim], claimURIFilter: Claim => Boolean): Task[Unit] = {
    for {
      _ <- ZIO.logDebug(s"Available claims: $existingClaims")
      claimsToDelete <- ZIO.attempt({
        existingClaims.filter(claimURIFilter)
          .map(claim => claimNameToPath(ClaimFileNameFormat.getFileName(claim)))
      })
      _ <- ZIO.logDebug(s" Claims for deletion after filtering: $claimsToDelete")
      deletionResult <- ZIO.ifZIO(ZIO.succeed(claimsToDelete.nonEmpty))(
        onTrue = claimsToDelete.tail.foldLeft(removeFile(claimsToDelete.head))((task, fileId) => {
          task.flatMap(_ => removeFile(fileId))
        }),
        onFalse = ZIO.succeed(())
      )
    } yield deletionResult
  }

  private def removeFile(file: String): Task[Any] = {
    ZIO.attemptBlockingIO(writer.delete(file))
      .flatMap({
        case Left(e) => ZIO.fail(e)
        case Right(v) => ZIO.succeed(v)
      })
  }

  /**
   * Should only be used after a successful verification of a files claim. Upon exercising, the node will add the
   * a progress state file with "PLANNED" status to indicate to workers
   * that the task is ready to be picked up, and update the state of the batch (remove the status that the batch is open to be
   * picked for processing).
   *
   * if winner hash corresponds to the current node, file an in-progress note,
   * remove the file indicating the batch is open for processing and
   * remove all claims.
   *
   * Steps:
   * 1) write process status file with some initial information
   * 2) remove the file indicating that the batch is in open state
   * 3) remove claims on the batch belonging to other nodes
   * 4) if all of the above successful, remove the claim filed by
   * this very node
   */
  override def exerciseBatchClaim(processId: ProcessId,
                                  existingClaimsForBatch: Set[Claim]): Task[Unit] = {
    for {
      _ <- writeTaskToProgressFolder(processId)
      openTaskFile <- ZIO.succeed(s"${OpenTasks.jobOpenTasksSubFolder(processId.jobId, isOpenJob = true)}/${processId.batchNr}")
      _ <- removeFile(openTaskFile)
      // first only delete those claims that do not belong to the current node
      _ <- removeClaims(existingClaimsForBatch, x => !isCurrentNodeClaim(x))
      // then delete the claim for the current node (assumption since exercise claim was called is that the current node
      // successfully claimed the batch execution
      _ <- removeClaims(existingClaimsForBatch, isCurrentNodeClaim)
    } yield ()
  }


}
