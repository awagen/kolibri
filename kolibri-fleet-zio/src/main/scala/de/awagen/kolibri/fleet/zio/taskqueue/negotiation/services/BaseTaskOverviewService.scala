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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics.{NodeHealthRemovalTask, TaskTopic}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{JobStateReader, NodeStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.ClaimBasedTaskPlannerService.{DUMMY_BATCH_NR, DUMMY_JOB}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.TaskStates._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingState}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DateUtils
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DateUtils.timeStringToTimeInMillis
import zio.ZIO
import zio.stream.ZStream

case class BaseTaskOverviewService(jobStateReader: JobStateReader,
                                   workStateReader: WorkStateReader,
                                   nodeStateReader: NodeStateReader) extends TaskOverviewService {

  private[services] def processIdToTask(processId: ProcessId,
                                        taskTopic: TaskTopic,
                                        nodeHash: String = AppProperties.config.node_hash,
                                        placedTimeInMillis: => Long = System.currentTimeMillis()): Task = {
    Task(
      processId.jobId,
      processId.batchNr,
      nodeHash,
      placedTimeInMillis,
      taskTopic
    )
  }

  /**
   * Note that this call returns at maximum maxNrTasks tasks corresponding
   * to a batch execution.
   * Note that it is on the handling service (e.g claim-based or just executing)
   * to decide how many can actually be used, taking into account
   * what has been claimed or is in progress already
   */
  override def getBatchProcessingTasks(openJobsSnapshot: OpenJobsSnapshot, maxNrTasks: Int = AppProperties.config.maxNrJobsClaimed): zio.Task[Seq[Task]] = {
    for {
      nNextOpenBatches <- ZIO.attempt(openJobsSnapshot.getNextNOpenBatches(maxNrTasks, Map.empty))
      nNextOpenProcessIds <- ZIO.attempt(nNextOpenBatches.flatMap(defWithBatches => {
        defWithBatches._2.map(batchNr => ProcessId(defWithBatches._1.jobName, batchNr))
      }))
      fileBatchClaimResults <- ZStream.fromIterable(nNextOpenProcessIds)
        .map(id => processIdToTask(id, TaskTopics.JobTaskProcessingTask))
        .runCollect
    } yield fileBatchClaimResults
  }

  override def getTaskResetTasks(processingStates: Set[ProcessingState], nodeHash: String): zio.Task[Seq[Task]] = {
    for {
      // current time to filter out those in-progress states that were not updated for too long
      currentTimeInMillis <- ZIO.succeed(System.currentTimeMillis())
      // filtering for in-progress states with overdue updates
      overdueProcessingStates <- ZStream.fromIterable(processingStates)
        .filter(state => {
          val timeUpdated: Long = DateUtils.timeStringToTimeInMillis(state.processingInfo.lastUpdate)
          val timeHasElapsed: Boolean = (currentTimeInMillis - timeUpdated) / 1000.0 > AppProperties.config.maxTimeBetweenProgressUpdatesInSeconds
          state.processingInfo.processingNode == nodeHash && timeHasElapsed
        })
        .runCollect
      resetTasks <- ZStream.fromIterable(overdueProcessingStates).map(state =>
        processIdToTask(
          state.stateId,
          TaskTopics.JobTaskResetTask(state.processingInfo.processingNode),
          state.processingInfo.processingNode
        ))
        .runCollect
    } yield resetTasks
  }

  override def getJobToDoneTasks(openJobsSnapshot: OpenJobsSnapshot): zio.Task[Seq[Task]] = {
    for {
      // extract all jobIds that are in open state but that have no open or in-progress batches anymore
      jobIdsWithoutUnfinishedBusiness <- ZIO.attempt({
        openJobsSnapshot.jobStateSnapshots.values.filter(x => {
          !x.batchesToState.values.exists(state => state != BatchProcessingStates.Done)
        }).map(x => x.jobId).toSet
      })
      // file claims
      tasks <- ZStream.fromIterable(jobIdsWithoutUnfinishedBusiness)
        .map(jobId =>
          processIdToTask(
            ProcessId(jobId, DUMMY_BATCH_NR),
            TaskTopics.JobWrapUpTask,
            AppProperties.config.node_hash
          ))
        .runCollect
    } yield tasks
  }

  override def getNodeHealthRemoveTasks: zio.Task[Seq[Task]] = {
    for {
      nodesStates <- nodeStateReader.readNodeStates
      tasks <- ZStream.fromIterable(nodesStates)
        .map(healthState => {
          (healthState.nodeId, timeStringToTimeInMillis(healthState.lastUpdate))
        })
        .map(x => (x._1, (System.currentTimeMillis() - x._2) / 1000.0))
        .filter(x => x._2 > AppProperties.config.maxTimeBetweenHealthUpdatesInSeconds)
        .map(x => {
          processIdToTask(
            ProcessId(DUMMY_JOB, DUMMY_BATCH_NR),
            NodeHealthRemovalTask(x._1),
            AppProperties.config.node_hash
          )
        })
        .tap(task => ZIO.logDebug(s"node health remove task: $task"))
        .runCollect
    } yield tasks
  }
}
