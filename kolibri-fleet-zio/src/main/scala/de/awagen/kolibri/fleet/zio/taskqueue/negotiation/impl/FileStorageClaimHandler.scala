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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl

import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore, WeaklyTypedMap}
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.maxNrJobsClaimed
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.FileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, CREATION_TIME_IN_MILLIS, JOB_ID, NODE_HASH, PROCESSING_STATUS, TOPIC}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus.ProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessingState, ProcessingStateUtils, ProcessingStatus}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.{ClaimFilingStatus, ClaimVerifyStatus}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic.{ClaimTopic, UNKNOWN}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.{ClaimHandler, JobStateHandler}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig.Directories
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import zio.stream.{ZSink, ZStream}
import zio.{Queue, Task, ZIO}

import scala.collection.mutable

object FileStorageClaimHandler {

  case object OpenTaskFileNameFormat extends FileNameFormat(Seq(BATCH_NR), "__") {
    def getFileName(batchNr: Int): String = {
      this.format(TypedMapStore(mutable.Map(BATCH_NR.namedClassTyped -> batchNr)))
    }
  }

  case object InProgressTaskFileNameFormat extends FileNameFormat(Seq(BATCH_NR, PROCESSING_STATUS), "__") {
    def getFileName(batchNr: Int, processingStatus: ProcessingStatus): String = {
      this.format(TypedMapStore(mutable.Map(
        BATCH_NR.namedClassTyped -> batchNr,
        PROCESSING_STATUS.namedClassTyped -> processingStatus.toString,
      )))
    }
  }

  case object ClaimFileNameFormat extends FileNameFormat(Seq(TOPIC, JOB_ID, BATCH_NR, CREATION_TIME_IN_MILLIS, NODE_HASH), "__") {

    def getFileName(claimTopic: ClaimTopic,
                    jobName: String,
                    batchNr: Int,
                    creationTimeInMillis: Long,
                    nodeHash: String): String = {
      val map: TypeTaggedMap = TypedMapStore(mutable.Map(
        TOPIC.namedClassTyped -> claimTopic.toString,
        JOB_ID.namedClassTyped -> jobName,
        BATCH_NR.namedClassTyped -> batchNr,
        CREATION_TIME_IN_MILLIS.namedClassTyped -> creationTimeInMillis,
        NODE_HASH.namedClassTyped -> nodeHash
      ))
      format(map)
    }
  }

  private def getFullFilePathForClaimFile(jobId: String, batchNr: Int, claimTopic: ClaimTopic): String = {
    val fileName = ClaimFileNameFormat.getFileName(
      claimTopic,
      jobId,
      batchNr,
      System.currentTimeMillis(),
      AppProperties.config.node_hash
    )
    s"${ZioDIConfig.Directories.jobClaimSubFolder(jobId, isOpenJob = true)}/$fileName"
  }

  private def getInProgressFilePathForJob(jobId: String, batchNr: Int, processingStatus: ProcessingStatus): String = {
    val fileName: String = InProgressTaskFileNameFormat.getFileName(batchNr, processingStatus)
    s"${Directories.jobTasksInProgressStateSubFolder(jobId, isOpenJob = true)}/${AppProperties.config.node_hash}/$fileName"
  }

  private def isCurrentNodeClaim(claimURI: String): Boolean = {
    val claimAttributes: WeaklyTypedMap[String] = ClaimFileNameFormat.parse(claimURI.split("/").last)
    claimAttributes.get(NODE_HASH.namedClassTyped.name).getOrElse("UNDEFINED") == AppProperties.config.node_hash
  }

  private def claimNameToPath(name: String): String = {
    val parsedName: WeaklyTypedMap[String] = ClaimFileNameFormat.parse(name)
    val jobId = JOB_ID.parseFunc(parsedName.get(JOB_ID.namedClassTyped.name).get)
    s"${Directories.jobClaimSubFolder(jobId, isOpenJob = true)}/$name"
  }


}

/**
 * Handle filing, validation and exercising of claims.
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
 * The claim handler is only responsible for filing claims,
 * validating whether the node won the right to execute specific batches
 * and then exercising the claim which means writing a process state file
 * and removing the file indicating the batch is still open for processing,
 * along with removing all existing claim files.
 * The steps after this are then handled by WorkManager directly syncing with
 * the process state folder for each job.
 *
 * NOTE that also other tasks are subject to claiming,
 * e.g in case some node has not written any inprogress state updates,
 * then the "open" state for a job has to be restored again.
 * For this some node needs to claim cleanup rights and then execute it.
 * This type of control process is still to be implemented though
 * for the cleanup case.
 */
case class FileStorageClaimHandler(filterToOverviewReader: (String => Boolean) => DataOverviewReader,
                                   writer: Writer[String, String, _],
                                   reader: Reader[String, Seq[String]],
                                   jobStateHandler: JobStateHandler) extends ClaimHandler {

  import FileStorageClaimHandler._

  private[this] val overviewReader: DataOverviewReader = filterToOverviewReader(_ => true)

  /**
   * Get the mapping of jobId to full file paths of in-progress files
   * for the current node, covering all the passed jobs
   */
  private def getExistingInProgressFilesForNode(jobs: Set[String]): Map[String, Set[String]] = {
    jobs.map(jobId => {
      val inProgressStateFiles: Set[String] = overviewReader
        .listResources(Directories.jobTasksInProgressStateForNodeSubFolder(
          jobId,
          AppProperties.config.node_hash,
          isOpenJob = true
        ), _ => true).toSet
      (jobId, inProgressStateFiles)
    }).toMap
  }

  /**
   * Get all full claim paths for the passed jobId and the given claimTopic
   */
  private def getExistingClaimsForJob(jobId: String, claimTopic: ClaimTopic): Seq[String] = {
    overviewReader
      .listResources(Directories.jobClaimSubFolder(jobId, isOpenJob = true), filename => {
        val topic: String = ClaimFileNameFormat.parse(filename.split("/").last)
          .get(TOPIC.namedClassTyped.name)
          .getOrElse(UNKNOWN.toString)
        topic == claimTopic.toString
      })
  }

  private def getExistingClaimsForBatch(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Seq[String] = {
    getExistingClaimsForJob(jobId, claimTopic)
      .filter(file => {
        ClaimFileNameFormat.parse(file.split("/").last)
          .get(BATCH_NR.namedClassTyped.name)
          .get == batchNr
      })
  }

  /**
   * Write a claim for a given job for a given claim topic (e.g claiming an execution, a cleanup or the like).
   * NOTE that jobId here means [jobName]_[timePlacedInMillis]
   */
  private[impl] def fileBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimFilingStatus] = {
    ZIO.ifZIO(ZIO.attemptBlockingIO(getExistingClaimsForBatch(jobId, batchNr, claimTopic).isEmpty))(
      onTrue = {
        // write claim
        val claimPath = getFullFilePathForClaimFile(jobId, batchNr, claimTopic)
        val persistResult: Either[Exception, _] = writer.write("", claimPath)
        persistResult match {
          case Left(e) =>
            ZIO.fail(e)
          case _ => ZIO.succeed(ClaimFilingStatus.PERSIST_SUCCESS)
        }
      },
      onFalse = ZIO.succeed(ClaimFilingStatus.OTHER_CLAIM_EXISTS)
    )
  }


  /**
   * Verify a filed claim. This is used after a claim was filed to check on it after a given period of time
   * to see whether the claim "won", e.g the node is allowed to pick the claimed batch and execute it.
   * Will either return a CLAIM_ACCEPTED, based on which we can exercise the claim (picking the batch to execute
   * and update the processing status), or returns other states indicating why a claim was not accepted.
   *
   * Verify whether a filed claim succeeded and if so (CLAIM_ACCEPTED)
   * indicates that the node can start processing
   *
   * @param job - job identifier
   * @return
   */
  private[impl] def verifyBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimVerifyStatus] = ZIO.attemptBlockingIO {
    val allExistingClaims: Seq[String] = getExistingClaimsForBatch(jobId, batchNr, claimTopic)
      .map(fileName => fileName.split("/").last)
      .sorted
    val allClaimNodeHashes: Seq[String] = allExistingClaims
      .map(claim => {
        val claimAttributes = ClaimFileNameFormat.parse(claim)
        claimAttributes.get[String](NODE_HASH.namedClassTyped.name).get
      })
    val node_hash = AppProperties.config.node_hash
    val nodeHadFiledClaim = allClaimNodeHashes.contains(node_hash)
    val nodeClaimIsFirst = allClaimNodeHashes.head == node_hash
    (nodeHadFiledClaim, nodeClaimIsFirst, allClaimNodeHashes.nonEmpty) match {
      case (_, _, false) => ClaimVerifyStatus.NO_CLAIM_EXISTS
      case (false, _, true) => ClaimVerifyStatus.NODE_CLAIM_DOES_NOT_EXIST
      case (_, false, true) => ClaimVerifyStatus.OTHER_CLAIMED_EARLIER
      case (true, true, true) => ClaimVerifyStatus.CLAIM_ACCEPTED
    }
  }

  /**
   * Writing a file representing the given batch to the in-progress folder.
   * File is only named by the batch number.
   * The content of the file contains additional information about the process state.
   * Note that when the processing has not yet started, the information about number of elements
   * and the like might be at default values (e.g 0).
   */
  private def writeTaskToProgressFolder(jobId: String, batchNr: Int): Task[Any] = {
    (for {
      toInProgressWriteResult <- ZIO.attemptBlockingIO({
        val processingState = ProcessingState(
          jobId,
          batchNr,
          ProcessingStatus.PLANNED,
          0,
          0,
          AppProperties.config.node_hash,
          ProcessingStateUtils.timeInMillisToFormattedTime(System.currentTimeMillis())
        )
        writer.write(
          ProcessingStateJsonProtocol.processingStateFormat.write(processingState).toString,
          getInProgressFilePathForJob(jobId, batchNr, ProcessingStatus.PLANNED)
        )
      })
    } yield toInProgressWriteResult)
      .flatMap({
        case Left(e) => ZIO.fail(e)
        case Right(v) => ZIO.succeed(v)
      })
  }

  // TODO: limit this to specific files
  private def removeFile(file: String): Task[Any] = {
    ZIO.attemptBlockingIO(writer.delete(file))
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
   * @param existingClaimFiles
   * @param claimURIFilter
   * @return
   */
  private def removeClaims(existingClaimFiles: Seq[String], claimURIFilter: String => Boolean): Task[Unit] = {
    for {
      _ <- ZIO.logDebug(s"Available claims: $existingClaimFiles")
      claimsToDelete <- ZIO.attempt({
        existingClaimFiles.filter(claimURIFilter)
          .map(claimPath => claimPath.split("/").last)
          .map(claimNameToPath)
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
  private[impl] def exerciseBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[Unit] = {
    for {
      _ <- writeTaskToProgressFolder(jobId, batchNr)
      openTaskFile <- ZIO.succeed(s"${ZioDIConfig.Directories.jobOpenTasksSubFolder(jobId, isOpenJob = true)}/$batchNr")
      _ <- removeFile(openTaskFile)
      allExistingBatchClaims <- ZIO.attemptBlocking(getExistingClaimsForBatch(jobId, batchNr, claimTopic))
      // first only delete those claims that do not belong to the current node
      _ <- removeClaims(allExistingBatchClaims, x => !isCurrentNodeClaim(x))
      // then delete the claim for the current node (assumption since exercise claim was called is that the current node
      // successfully claimed the batch execution
      _ <- removeClaims(allExistingBatchClaims, isCurrentNodeClaim)
    } yield ()
  }

  /**
   * Over all currently registered jobs, find the claims filed by this node
   */
  private def getAllClaimsByCurrentNode(claimTopic: ClaimTopic): ZIO[Any, Throwable, Set[String]] = {
    for {
      allKnownJobs <- jobStateHandler.fetchState.map(x => x.jobStateSnapshots.values.map(x => x.jobId).toSet)
      allClaims <- ZIO.attemptBlocking({
        allKnownJobs.flatMap(job => getExistingClaimsForJob(job, claimTopic))
      })
      currentNodeClaims <- ZIO.attempt({
        allClaims.filter(claimPath => {
          ClaimFileNameFormat.parse(claimPath.split("/").last)
            .get(NODE_HASH.namedClassTyped.name)
            .get == AppProperties.config.node_hash
        })
      })
    } yield currentNodeClaims
  }

  /**
   * Method to be called by
   * Here we follow the sequence:
   * 1 - verify existing claims and exercise the batches that the node claimed successfully
   * 2 - test whether there is any demand for the node to claim additional batches. If so, file claims
   */
  def manageClaims(claimTopic: ClaimTopic, batchQueue: Queue[JobBatch[_,_]]): Task[Unit] = {
    for {
      // TODO: for filed claims where the jobs are not relevant for the current node anymore
      // make sure to delete the claims (only for this very node) and if we have any
      // jobs in progress, to also delete the in-progress file (missing file to be picked up by WorkHandler
      // to actually stop the execution) and move the batch back to the OPEN-folder
      claimsByCurrentNode <- getAllClaimsByCurrentNode(claimTopic)
      // verify each claim
      claimWithJobIdWithBatchNr <- ZIO.attempt({
        claimsByCurrentNode.map(claim => {
          val claimInfo = ClaimFileNameFormat.parse(claim.split("/").last)
          val jobId: String = JOB_ID.parseFunc(claimInfo.get(JOB_ID.namedClassTyped.name).get)
          val batchNr: Int = BATCH_NR.parseFunc(claimInfo.get(BATCH_NR.namedClassTyped.name).get)
          (claim, jobId, batchNr)
        })
      })
      verifiedClaims <- ZStream.fromIterable(claimWithJobIdWithBatchNr).filterZIO(claimInfo => {
        verifyBatchClaim(claimInfo._2, claimInfo._3, claimTopic)
          .either
          .map({
            case Right(v) => v
            case Left(_) => ClaimVerifyStatus.FAILED_VERIFICATION
          })
          .map(status => status == ClaimVerifyStatus.CLAIM_ACCEPTED)
      })
        .run(ZSink.foldLeft(Seq.empty[(String, String, Int)])((oldState, newElement) => oldState :+ newElement ))
      // Now exercise all verified claims
      _ <- ZStream.fromIterable(verifiedClaims).foreach(claimInfo => {
        exerciseBatchClaim(claimInfo._2, claimInfo._3, claimTopic).map(_ => ())
      })
      openJobsSnapshot <- jobStateHandler.fetchState
      // mapping jobId -> Set[Int] representing the batches claimed by the current node
      existingClaimsForNode <- ZIO.attempt({
        openJobsSnapshot.getExistingClaimsForNode(AppProperties.config.node_hash)
      })
      // fetching all files showing succesfully claimed (exercised claims in the form of
      // files in the in-progress state subfolder) batches as full file paths to the in-progress files
      inProgressStateFilesPerJobForThisNode <- ZIO.attemptBlockingIO(getExistingInProgressFilesForNode(
        openJobsSnapshot.jobStateSnapshots.keys.toSet
      ))
      // from the total number of claims filed and the number of batches already claimed calculate
      // whether there is any need to file more claims
      numberOfNewlyClaimableBatches <- ZIO.attempt({
        val nrOfInProgressFiles = inProgressStateFilesPerJobForThisNode.values.flatten.count(_ => true)
        val nrOfFiledClaims = existingClaimsForNode.values.flatten.count(_ => true)
        math.max(0, maxNrJobsClaimed - (nrOfInProgressFiles + nrOfFiledClaims))
      })
      // if there are is some room to claim additional batches, do so (filing the batch claim,
      // while the verification and possible exercising of each claim will be handled in the next
      // round of the manageClaims call
      _ <- ZIO.attempt(openJobsSnapshot.getNextNOpenBatches(numberOfNewlyClaimableBatches, ignoreClaimedBatches = true))
        .flatMap(openBatchesToClaim => {
          val jobIdToBatchPairs: Seq[(String, Int)] = openBatchesToClaim
            .flatMap(x => {
              x._2.map(batchNr => (x._1.jobName, batchNr))
            })
          ZStream.fromIterable(jobIdToBatchPairs)
            .foreach(el => {
              fileBatchClaim(el._1, el._2, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
            })
        })
    } yield ()
  }

}
