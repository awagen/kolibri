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

import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.WorkStateReader
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.WorkStateWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DataTypeUtils
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import zio.stream.ZStream
import zio.{Chunk, Exit, Fiber, Queue, Ref, Task, ZIO}


object BaseWorkHandlerService {

}


/**
 * Uses WorkStateReader to pick new planned tasks to put them in
 * processing queue and update status to QUEUED as soon as new files
 * are found.
 * Picks tasks from queue as soon as free slots for work available.
 * On starting the processing, needs to load job config and
 * load the defined resources (if any).
 * For the latter utilize the global resource manager that can take
 * care of deleting resources that are not needed anymore and keeps
 * track of jobs needing a particular resource.
 */
case class BaseWorkHandlerService(workStateReader: WorkStateReader,
                                  workStateWriter: WorkStateWriter,
                                  queue: Queue[JobBatch[_, _, _]],
                                  addedBatchesHistoryRef: Ref[Seq[ProcessId]],
                                  processIdToAggregatorRef: Ref[Map[ProcessId, Ref[Aggregator[TaggedWithType with DataPoint[_], _]]]],
                                  processIdToFiberRef: Ref[Map[ProcessId, Fiber.Runtime[Nothing, Unit]]]) extends WorkHandlerService {


  /**
   * Logic to interrupt running jobs that are not present in the
   * persisted processing states anymore. This might mean that a claimed job was revoked
   * due to node inactivity or similar. Ensures consistency of processing with persisted
   * processing state
   */
  def compareRunningBatchesWithSnapshotAndInterruptIfIndicated(processingStates: Seq[ProcessingState]): Task[Chunk[Exit[Nothing, Unit]]] = {
    for {
      processToFiberMapping <- processIdToFiberRef.get
      runningBatches <- ZIO.succeed(processToFiberMapping.keys)
      batchesWithProgressFile <- ZIO.attempt(processingStates.map(x => x.stateId))
      runningBatchesWithoutInProgressFile <- ZIO.attempt(runningBatches.filter(batch => !batchesWithProgressFile.contains(batch)))
      fiberInterruptResults <- ZStream.fromIterable(runningBatchesWithoutInProgressFile)
        .tap(processId => ZIO.logWarning(s"Trying to interrupt fiber for processId: $processId"))
        .map(processId => processToFiberMapping(processId))
        .mapZIO(fiber => fiber.interrupt)
        .tap({
          case Exit.Success(value) => ZIO.logWarning(s"Fiber interruption succeeded with value:\n$value")
          case Exit.Failure(cause) => ZIO.logWarning(s"Fiber interruption failed with cause:\n$cause")
        })
        .runCollect
    } yield fiberInterruptResults
  }

  /**
   * Looks into the in-progress folders of currently open jobs and adds those
   * JobBatches to the queue for which the in-progress file indicates it is in status
   * PLANNED
   */
  private def pullPlannedTasks(openJobSnapshot: OpenJobsSnapshot): Task[Seq[(Boolean, ProcessingState)]] = {
    for {
      // get the recent history of batches added for processing, to avoid adding the same
      // multiple times
      historySeq <- addedBatchesHistoryRef.get
      // retrieve the jobId -> JobDefinition mapping as relevant for the current node
      jobIdToJobDefAsRelevantForNode <- ZIO.attempt(
        openJobSnapshot.jobsForThisNodeSortedByPriority.map(x => (x.jobId, x.jobDefinition))
          .toMap
      )
      // for the relevant jobIds, pick the current processing states
      processStates <- workStateReader.getInProgressStateForNode(jobIdToJobDefAsRelevantForNode.keySet)
      // planned tasks are those we wanna add to the job-queue
      plannedTasks <- ZIO.succeed(
        processStates.values.flatten
          .filter(x => x.processingInfo.processingStatus == ProcessingStatus.PLANNED)
          .filter(x => !historySeq.contains(x.stateId))
          .toSeq
      )
      // for all planned tasks, generate the JobBatch object
      tasksToAdd <- ZIO.attempt(plannedTasks
        .filter(task => jobIdToJobDefAsRelevantForNode.contains(task.stateId.jobId))
        .map(task => JobBatch(jobIdToJobDefAsRelevantForNode(task.stateId.jobId), task.stateId.batchNr)))

      // TODO: for each planned task, first try to update the processing state
      // and then add to queue, such that when state update is not successful,
      // the task is also not started. Bake this into single handleBatch method that
      // combines both

      // add planned tasks to queue
      queueAddResult <- addPlannedTasksToQueue(tasksToAdd, plannedTasks)
    } yield queueAddResult
  }

  private def addPlannedTasksToQueue(tasksToAdd: Seq[JobBatch[_,_,_]], plannedTasks: Seq[ProcessingState]): Task[Seq[(Boolean, ProcessingState)]] = {
    for {
      queueAddResult <- {
        DataTypeUtils.addElementsToQueue(tasksToAdd, queue).map(x => {
          val onlySuccesses = x.filter(identity)
          val processIds = tasksToAdd
            .take(onlySuccesses.size)
            .map(batch => plannedTasks.find(tasks => tasks.stateId.jobId == batch.job.jobName && tasks.stateId.batchNr == batch.batchNr))
            .filter(x => x.nonEmpty)
            .map(x => x.get)
          processIds.foreach(id => addedBatchesHistoryRef.update(
            history => (history :+ id.stateId).takeRight(AppProperties.config.pulledTaskHistorySize)
          ))
          x.zip(processIds)
        })
      }
    } yield queueAddResult
  }

  /**
   * For the passed processIds, write an updated in-progress file with status
   * "QUEUED"
   */
  private def changeProcessingStateToQueued(processingStates: Seq[ProcessingState]): Task[Unit] = {
    val updatedState = processingStates.map(x => x.copy(processingInfo = x.processingInfo.copy(
      processingStatus = ProcessingStatus.QUEUED,
      lastUpdate = ProcessingStateUtils.timeInMillisToFormattedTime(System.currentTimeMillis())
    )))
    ZStream.fromIterable(updatedState)
      .foreach(workStateWriter.updateInProgressState)
  }

  /**
   * Pick next job to process, and initialize processing, keep ref to aggregator and
   * the fiber the processing is running on to be able to interrupt the computation.
   */
  private def processNextBatch: Task[Unit] = for {
    // pick
    job <- ZIO.ifZIO(queue.isEmpty)(
      onTrue = queue.take,
      onFalse = ZIO.fail(new RuntimeException("no element available"))
    )
    _ <- TaskWorker.work(job)
      .forEachZIO(aggAndFiber => {
        processIdToAggregatorRef.update(oldMap =>
          oldMap + (ProcessId(job.job.jobName, job.batchNr) -> aggAndFiber._1)) *>
          processIdToFiberRef.update(oldMap =>
            oldMap + (ProcessId(job.job.jobName, job.batchNr) -> aggAndFiber._2))
      })
  } yield ()

  override def manageBatches(openJobSnapshot: OpenJobsSnapshot): Task[Unit] = {
    for {
      // pick the tasks in PLANNED state and fill the queue with those
      addPlannedTaskResults <- pullPlannedTasks(openJobSnapshot)
      // change the in-progress file state to QUEUED. As soon as the batch is actually
      // picked up for processing, needs another change to IN_PROGRESS and when completed
      // to DONE, when aborted to ABORTED
      _ <- changeProcessingStateToQueued(
        addPlannedTaskResults
          .filter(x => x._1)
          .map(x => x._2)
      )
    } yield ()
  }

  // pick the newest processing states for all processed batches and update the respective files
  override def persistProcessStates: Task[Unit] = ???
}
