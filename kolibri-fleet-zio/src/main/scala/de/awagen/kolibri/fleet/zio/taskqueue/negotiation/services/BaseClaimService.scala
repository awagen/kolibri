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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics.TaskTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, JobStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, JobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.BaseClaimService._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.TaskStates.Task
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import zio.ZIO
import zio.stream.{ZSink, ZStream}

object BaseClaimService {

  val DUMMY_BATCH_NR: Int = -1

  private def isCurrentNodeClaim(claim: Task): Boolean = {
    claim.nodeId == AppProperties.config.node_hash
  }

  sealed trait ClaimLimit

  case object Unlimited extends ClaimLimit

  sealed case class Limit(count: Int) extends ClaimLimit

}

case class BaseClaimService(claimReader: ClaimReader,
                            claimUpdater: ClaimWriter,
                            workStateReader: WorkStateReader,
                            workStateWriter: WorkStateWriter,
                            jobStateReader: JobStateReader,
                            jobStateWriter: JobStateWriter,
                            taskOverviewService: TaskOverviewService) extends ClaimService {

  /**
   * Given a range of claims, file all of them (e.g "apply for eligibility to perform the claimed task")
   */
  private[services] def fileBatchProcessingClaims(processIds: Seq[ProcessId]): zio.Task[Unit] = {
    for {
      fileBatchClaimResults <- ZStream.fromIterable(processIds).foreach(id => {
        claimUpdater.fileClaim(id, TaskTopics.JobTaskProcessingTask)
      })
    } yield fileBatchClaimResults
  }

  /**
   * Filing claim for resetting a batch processing state for another node.
   */
  private[services] def fileBatchResetClaim(processId: ProcessId, nodeHash: String): zio.Task[ClaimFilingStatus] = {
    claimUpdater.fileClaim(processId, TaskTopics.JobTaskResetTask(nodeHash))
  }

  /**
   * Filing claim for moving the batch status data to done
   */
  private[services] def fileJobToDoneClaim(jobId: String): zio.Task[ClaimFilingStatus] = {
    claimUpdater.fileClaim(ProcessId(jobId, DUMMY_BATCH_NR), TaskTopics.JobWrapUpTask)
  }

  /**
   * Given any claim, pick the right method to file the claim
   */
  private[services] def fileClaim(claim: Task): zio.Task[Unit] = {
    claim.taskTopic match {
      case TaskTopics.JobTaskProcessingTask =>
        fileBatchProcessingClaims(Seq(ProcessId(claim.jobId, claim.batchNr)))
      case TaskTopics.JobTaskResetTask(nodeHash) =>
        fileBatchResetClaim(ProcessId(claim.jobId, claim.batchNr), nodeHash).map(_ => ())
      case TaskTopics.JobWrapUpTask =>
        fileJobToDoneClaim(claim.jobId).map(_ => ())
    }
  }

  /**
   * Extract accepted claims from list of all filed claims for the given claim topic
   */
  private[services] def verifyClaimsAndReturnSuccessful(claims: Set[Task], taskTopic: TaskTopic): ZIO[Any, Nothing, Seq[Task]] = {
    ZStream.fromIterable(claims).filterZIO(claimInfo => {
      claimReader.verifyBatchClaim(claimInfo.jobId, claimInfo.batchNr, taskTopic)
        .either
        .map({
          case Right(v) => v
          case Left(_) => ClaimVerifyStatus.FAILED_VERIFICATION
        })
        .map(status => status == ClaimVerifyStatus.CLAIM_ACCEPTED)
    })
      .run(ZSink.foldLeft(Seq.empty[Task])((oldState, newElement: Task) => oldState :+ newElement))
  }

  /**
   * deletes all claims that match the job / batchNr / taskTopic combination of the
   * passed claim. First deletes those claims from other node and then the one of the current node.
   * The assumption here is that the node calling for deletion of the claims actually won
   * the claim, thus do only use after positive result from verifyClaim
   */
  private[services] def deleteAllClaimsForMatchingJobAndBatchAndTaskTopic(task: Task): zio.Task[Unit] = {
    for {
      // get all claims for the same job / batch / taskTopic combination as the passed claim
      existingClaimsForBatch <- claimReader.getClaimsForBatch(task.jobId, task.batchNr, task.taskTopic)
      // first delete claims belonging to other nodes to avoid a claim for another node remaining if the winner
      // claim is already deleted
      _ <- claimUpdater.removeClaims(existingClaimsForBatch, x => !isCurrentNodeClaim(x))
      // also delete the claim of the current node (the one who won the claim)
      _ <- claimUpdater.removeClaims(existingClaimsForBatch, isCurrentNodeClaim)
    } yield ()
  }


  /**
   * Take distinct exercise action depending on the claim topic files.
   * - JobTaskProcessingClaim: write in-progress state with status PLANNED for the batch for current node,
   * delete the other claims
   * - JobTaskResetClaim: means that some node did not in time update the processing state of a batch it was
   * processing. In that case we need to delete the in-progress file for that particular node, write the batch
   * file back to the "open" folder for the job to be claimed by other nodes.
   * - JobWrapUpClaim: moving job from open-jobs to done-jobs
   * - Unknown: do nothing
   *
   */
  private[services] def exerciseClaim(claim: Task): zio.Task[Unit] = {
    claim.taskTopic match {
      case TaskTopics.JobTaskProcessingTask =>
        for {
          // exercising the claim and deleting the claim files
          _ <- claimUpdater.exerciseBatchClaim(ProcessId(claim.jobId, claim.batchNr))
          // delete claims for respective job
          _ <- deleteAllClaimsForMatchingJobAndBatchAndTaskTopic(claim)
        } yield ()
      case TaskTopics.JobTaskResetTask(nodeHash) =>
        for {
          // write a file for the batch to reset back to open-folder
          _ <- jobStateWriter.writeBatchToOpen(ProcessId(claim.jobId, claim.batchNr))
          _ <- workStateWriter.deleteInProgressState(ProcessId(claim.jobId, claim.batchNr), nodeHash)
          // clean up all claims for this particular task
          _ <- deleteAllClaimsForMatchingJobAndBatchAndTaskTopic(claim)
        } yield ()
      case TaskTopics.JobWrapUpTask =>
        for {
          // wrap up whole job by moving the full job folder to done state
          _ <- jobStateWriter.moveToDone(claim.jobId)
          // remove all claims
          _ <- deleteAllClaimsForMatchingJobAndBatchAndTaskTopic(claim)
        } yield ()
      case _ =>
        for {
          // not a covered claim, thus do nothing but removing existing claims
          _ <- deleteAllClaimsForMatchingJobAndBatchAndTaskTopic(claim)
        } yield ()
    }
  }

  /**
   * Exercise the given claims. This usually involves changes to the persisted processing state.
   */
  private[services] def exerciseClaims(claims: Seq[Task]): zio.Task[Unit] = {
    ZStream.fromIterable(claims).foreach(claimInfo => exerciseClaim(claimInfo))
  }

  /**
   * For specific task topic, calculate the number of additional tasks that can be claimed before
   * reaching the defined limit
   */
  private[services] def limitForTaskType(taskTopic: TaskTopic, openJobsSnapshot: OpenJobsSnapshot): zio.Task[ClaimLimit] = {
    taskTopic match {
      case TaskTopics.JobTaskProcessingTask =>
        for {
          // open job ids used to limit the retrieved state data to
          jobIds <- ZIO.succeed(openJobsSnapshot.jobStateSnapshots.values.map(x => x.jobId).toSet)
          // Set[Claim] representing the claims made by the current node
          existingClaimsForNode <- claimReader.getClaimsByCurrentNode(
            TaskTopics.JobTaskProcessingTask,
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
          _ <- ZIO.logDebug(s"Nr of claimable batches: $numberOfNewlyClaimableBatches")
        } yield Limit(numberOfNewlyClaimableBatches)
      case _ => ZIO.succeed(Unlimited)
    }
  }


  /**
   * Compare already existing claims which those that need filing as per the passed tasks,
   * and for those that do not yet have a claim, file the claim.
   *
   * @param tasks - list of tasks for which to file a claim (if not yet existing)
   * @param openJobsSnapshot - snapshot of open jobs state
   */
  private[services] def fillUpClaims(tasks: Seq[Task], openJobsSnapshot: OpenJobsSnapshot): ZIO[Any, Throwable, Unit] = {
    for {
      // mapping of task topic to jobIds to query existing claims more specifically
      topicAndJobIdsWithOpenTasks <- ZStream.fromIterable(tasks)
        .map(task => (task.taskTopic, task.jobId))
        .runFold[Map[TaskTopic, Set[String]]](Map.empty)((oldMap, newTuple) => {
          oldMap + (newTuple._1 -> (oldMap.getOrElse(newTuple._1, Set.empty) + newTuple._2))
        })
      // jobs that already have a respective claim filed by some node
      jobsWithExistingWrapUpClaims <- ZStream.fromIterable(topicAndJobIdsWithOpenTasks.toSeq)
        .mapZIO(tuple => claimReader.getAllClaims(tuple._2, tuple._1))
        .runFold(Seq.empty[Task])((oldSet, newEntry) => oldSet ++ newEntry)
      // filtering out those claims we do not need to file anymore
      filteredTasksByTopic <- ZIO.attempt({
        tasks
          .filter(task => !jobsWithExistingWrapUpClaims.exists(y => {
            y.jobId == task.jobId && y.batchNr == task.batchNr && y.taskTopic.id == task.taskTopic.id
          }))
          .foldLeft(Map.empty[TaskTopic, Seq[Task]])((oldMap, newEntry) => {
            oldMap + (newEntry.taskTopic -> (oldMap.getOrElse(newEntry.taskTopic, Seq.empty) :+ newEntry))
          })
      })
      // go over each (topic, Seq[Task]) tuple, determine the limit for number of claims for the particular topic
      // and file a claim for all tasks within the defined limits
      _ <- ZStream.fromIterable(filteredTasksByTopic.toSeq).foreach(topicAndTasks => {
        for {
          limit <- limitForTaskType(topicAndTasks._1, openJobsSnapshot).map({
            case Unlimited => 10000
            case Limit(value) => value
          })
          _ <- ZStream.fromIterable(topicAndTasks._2.take(limit)).foreach(task => fileClaim(task))
        } yield ()

      })
    } yield ()

  }

  /**
   * Get those jobIds that shall be ignored on the current node and those claims already filed which correspond
   * to any of those jobIds and remove those. Return the remaining claims which still hold.
   */
  private def cleanupClaimsAndReturnRemaining(openJobsSnapshot: OpenJobsSnapshot, taskTopic: TaskTopic): zio.Task[Set[Task]] = {
    for {
      ignoreJobIdsOnThisNode <- ZIO.succeed(openJobsSnapshot.jobsToBeIgnoredOnThisNode.map(x => x.jobId))
      // extract all (jobId, batchNr) tuples of claims currently filed for the node
      claimsByCurrentNode <- claimReader.getClaimsByCurrentNode(taskTopic, openJobsSnapshot.jobStateSnapshots.values.map(x => x.jobId).toSet)
      //verify each claim
      // remove the claims that are not needed anymore (only for this particular node)
      claimsToBeRemoved <- ZIO.succeed(claimsByCurrentNode.filter(x => ignoreJobIdsOnThisNode.contains(x.jobId)))
      _ <- claimUpdater.removeClaims(claimsToBeRemoved, _ => true)
    } yield claimsByCurrentNode -- claimsToBeRemoved
  }

  /**
   * Method to be called by
   * Here we follow the sequence:
   * 0 - check if any claims were made for any job that is now marked to be ignored by this node. If so,
   * delete the respective claims
   * 1 - verify existing claims and exercise the batches that the node claimed successfully
   * 2 - test whether there is any demand for the node to claim additional batches. If so, file additional claims
   */
  def manageClaims(taskTopic: TaskTopic): zio.Task[Unit] = {
    for {
      openJobsSnapshot <- jobStateReader.fetchOpenJobState
      // housekeeping
      remainingClaimsByCurrentNode <- cleanupClaimsAndReturnRemaining(openJobsSnapshot, taskTopic)
      // verifying remaining claims by this node
      verifiedClaims <- verifyClaimsAndReturnSuccessful(remainingClaimsByCurrentNode, taskTopic)
      // Now exercise all verified claims
      _ <- exerciseClaims(verifiedClaims)
      // to avoid new claims being filed based on old states, reload the current job state here and file new claims
      // based on that state
      _ <- reloadCurrentStateAndFileClaims(taskTopic)
    } yield ()
  }

  // TODO: we gotta move invoking of tasks for a topic out of here
  // and rather manage it centrally in the schedule, in distinct steps,
  // e.g 1) what are the next tasks?, 2) how do I plan those (e.g claiming or direct execution),
  // 3) take care of handling the work state of planned tasks
  private def reloadCurrentStateAndFileClaims(taskTopic: TaskTopic): ZIO[Any, Throwable, Unit] = {
    for {
      // to avoid new claims being filed based on old states, reload the current job state here
      openJobsSnapshot <- jobStateReader.fetchOpenJobState
      // extract all processing states
      processingStates <- workStateReader.getInProgressStateForAllNodes(openJobsSnapshot.jobStateSnapshots.keySet)
        .map(x => x.values.flatMap(y => y.values).flatten.toSet)
      // add claims for given taskTopic (if any)
      tasksForTopic <- taskTopic match {
        case TaskTopics.JobTaskProcessingTask =>
          taskOverviewService.getBatchProcessingTasks(openJobsSnapshot, AppProperties.config.maxNrJobsClaimed)
        case TaskTopics.JobTaskResetTask(hash) =>
          taskOverviewService.getTaskResetTasks(processingStates, hash)
        case TaskTopics.JobWrapUpTask =>
          taskOverviewService.getJobToDoneTasks(openJobsSnapshot)
        case _ => ZIO.succeed(Seq.empty)
      }
      _ <- fillUpClaims(tasksForTopic, openJobsSnapshot)
    } yield ()
  }

  override def getAllClaims(jobIds: Set[String], taskTopic: TaskTopic): zio.Task[Set[Task]] = claimReader.getAllClaims(jobIds, taskTopic)
}
