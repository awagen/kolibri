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
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.FileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, CREATION_TIME_IN_MILLIS, JOB_ID, NODE_HASH, TOPIC}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl.FileStorageClaimHandler.{ClaimFileNameFormat, InProgressTaskFileNameFormat}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessingState, ProcessingStateUtils, ProcessingStatus}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.{ClaimFilingStatus, ClaimVerifyStatus}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic.{ClaimTopic, UNKNOWN}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.{ClaimHandler, JobStateHandler}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig.Directories
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import zio.stream.{ZSink, ZStream}
import zio.{Task, ZIO}

import scala.collection.mutable

object FileStorageClaimHandler {

  case object OpenTaskFileNameFormat extends FileNameFormat(Seq(BATCH_NR), "_") {
    def getFileName(batchNr: Int): String = {
      this.format(TypedMapStore(mutable.Map(BATCH_NR.namedClassTyped -> batchNr)))
    }
  }

  case object InProgressTaskFileNameFormat extends FileNameFormat(Seq(BATCH_NR, NODE_HASH), "__") {
    def getFileName(batchNr: Int): String = {
      this.format(TypedMapStore(mutable.Map(
        BATCH_NR.namedClassTyped -> batchNr,
        NODE_HASH.namedClassTyped -> AppProperties.config.node_hash,
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
 */
case class FileStorageClaimHandler(filterToOverviewReader: (String => Boolean) => DataOverviewReader,
                                   writer: Writer[String, String, _],
                                   reader: Reader[String, Seq[String]],
                                   jobHandler: JobStateHandler) extends ClaimHandler {

  private[this] val overviewReader: DataOverviewReader = filterToOverviewReader(_ => true)

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

  private def getInProgressFileForJob(jobId: String, batchNr: Int): String = {
    val fileName: String = InProgressTaskFileNameFormat.getFileName(batchNr)
    s"${Directories.jobTasksInProgressStateSubFolder(jobId, isOpenJob = true)}/$fileName"
  }

  /**
   * Write a claim for a given job for a given claim topic (e.g claiming an execution, a cleanup or the like).
   * NOTE that jobId here means [jobName]_[timePlacedInMillis]
   */
  override def fileBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimFilingStatus] = {
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
   * Verify whether a filed claim succeeded and if so (CLAIM_ACCEPTED)
   * indicates that the node can start processing
   *
   * @param job - job identifier
   * @return
   */
  override def verifyBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[ClaimVerifyStatus] = ZIO.attemptBlockingIO {
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
          ProcessingStatus.QUEUED,
          0,
          0,
          AppProperties.config.node_hash,
          ProcessingStateUtils.timeInMillisToFormattedTime(System.currentTimeMillis())
        )
        writer.write(ProcessingStateJsonProtocol.processingStateFormat.write(processingState).toString, getInProgressFileForJob(jobId, batchNr))
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

  private def isCurrentNodeClaim(claimURI: String): Boolean = {
    val claimAttributes: WeaklyTypedMap[String] = ClaimFileNameFormat.parse(claimURI.split("/").last)
    claimAttributes.get(NODE_HASH.namedClassTyped.name).getOrElse("UNDEFINED") == AppProperties.config.node_hash
  }

  private[this] def claimNameToPath(name: String): String = {
    val parsedName: WeaklyTypedMap[String] = ClaimFileNameFormat.parse(name)
    val jobId = JOB_ID.parseFunc(parsedName.get(JOB_ID.namedClassTyped.name).get)
    s"${Directories.jobClaimSubFolder(jobId, isOpenJob = true)}/$name"
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
   * if winner hash corresponds to the current node, file an in-progress note,
   * remove all claims and start working, if not, ignore so that the node
   * that is the winner can start
   *
   * Steps:
   * 1) move the batch-file from open to in-progress (file is simply named by the nr of the batch, without any content)
   * 2) add to tasks to process for node
   * 3) write process status file (e.g IN_QUEUE, IN_PROGRESS,...)
   * 4) remove all claim-files for the corresponding batch
   * // TODO: add writing of process file (point 3))
   */
  override def exerciseBatchClaim(jobId: String, batchNr: Int, claimTopic: ClaimTopic): Task[Unit] = {
    for {
      openTaskFile <- ZIO.succeed(s"${ZioDIConfig.Directories.jobOpenTasksSubFolder(jobId, isOpenJob = true)}/$batchNr")
      // TODO: also add task to processQueue for the current node
      _ <- writeTaskToProgressFolder(jobId, batchNr)
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
      allKnownJobs <- jobHandler.fetchState.map(x => x.jobStateSnapshots.values.map(x => x.jobId).toSet)
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
  def manageClaims(claimTopic: ClaimTopic): Task[Unit] = {
    for {
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
        verifyBatchClaim(claimInfo._2, claimInfo._3, claimTopic).map(status => status == ClaimVerifyStatus.CLAIM_ACCEPTED)
      }).run(ZSink.foldLeft(Seq.empty[(String, String, Int)])((oldState, newElement) => oldState :+ newElement ))
      // Now exercise all verified claims
      claimExerciseStatus <- ZStream.fromIterable(verifiedClaims).foreach(claimInfo => {
        exerciseBatchClaim(claimInfo._2, claimInfo._3, claimTopic)
      })
      // TODO: Now check the need to actually file more batch claims (if nr of claimed items is below a claimLimit)
      // TODO: for this we need some work manager that keeps record of the work items claimed
    } yield ()
  }

}
