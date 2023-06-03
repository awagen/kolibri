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
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.WorkStateReader
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.WorkStateWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.BaseWorkHandlerService.BatchAndProcessingState
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus.ProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DataTypeUtils
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import zio.stream.ZStream
import zio.{Chunk, Exit, Fiber, Queue, Ref, Task, ZIO}


object BaseWorkHandlerService {

  case class BatchAndProcessingState(batch: JobBatch[_, _, _ <: WithCount], processingState: ProcessingState)

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
                                  queue: Queue[JobBatch[_, _, _ <: WithCount]],
                                  addedBatchesHistoryRef: Ref[Seq[ProcessId]],
                                  processIdToAggregatorRef: Ref[Map[ProcessId, Ref[Aggregator[TaggedWithType with DataPoint[_], _ <: WithCount]]]],
                                  processIdToFiberRef: Ref[Map[ProcessId, Fiber.Runtime[Throwable, Unit]]]) extends WorkHandlerService {


  /**
   * Logic to interrupt running jobs that are not present in the
   * persisted processing states anymore. This might mean that a claimed job was revoked
   * due to node inactivity or similar. Ensures consistency of processing with persisted
   * processing state
   *
   * @param processingStates      - the processing states currently persisted
   * @param processToFiberMapping - the mapping of processIds to the actual Fiber on which they are currently processed.
   *                              This reflects the jobs that are actually running right now (or that completed already).
   *                              There can be an inconsistency between currently running jobs and those for which
   *                              an in-progress state exists, e.g in case the node did not update the in-progress state
   *                              frequent enough, in which case other nodes can claim the right to revoke the
   *                              claimed batch for this node by means of removing the in-progress state for the current
   *                              node and moving the batch back to "open", for all nodes to claim
   */
  private def compareRunningBatchesWithSnapshotAndInterruptIfIndicated(processingStates: Seq[ProcessingState],
                                                                       processToFiberMapping: Map[ProcessId, Fiber.Runtime[_, Unit]]): Task[Chunk[Exit[_, Unit]]] = {
    for {
      batchesWithProgressFile <- ZIO.attempt(processingStates.map(x => x.stateId))
      runningBatchesWithoutInProgressFile <- ZIO.attempt(processToFiberMapping.keys.filter(batch => !batchesWithProgressFile.contains(batch)))
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
   * Given a current snapshot of the open job state and mapping of ProcessId to Fiber,
   * interrupt processes that do not match the current persisted state.
   */
  private def cleanUpProcessesWithMissingProcessingStates(openJobsSnapshot: OpenJobsSnapshot,
                                                          processToFiberMapping: Map[ProcessId, Fiber.Runtime[_, Unit]]): Task[Unit] = {
    for {
      // pick all known jobIds for the current snapshot
      currentOpenJobIds <- ZIO.succeed(openJobsSnapshot.jobStateSnapshots.keySet)
      // get all process states for the current jobs
      existingProcessStatesForJobs <- workStateReader.getInProgressStateForNode(currentOpenJobIds)
      // interrupt all running processes for which there is no progress state persisted
      _ <- compareRunningBatchesWithSnapshotAndInterruptIfIndicated(
        existingProcessStatesForJobs.values.flatten.toSeq,
        processToFiberMapping
      )
    } yield ()
  }


  /**
   * Looks into the in-progress folders of currently open jobs and picks those
   * JobBatches for which the in-progress file indicates it is in status
   * PLANNED and it is relevant for the current node (taking job directives into account)
   */
  private def pullPlannedTasks(openJobSnapshot: OpenJobsSnapshot): Task[Seq[BatchAndProcessingState]] = {
    for {
      // get the recent history of batches added for processing, to avoid adding the same
      // multiple times
      historySeq <- addedBatchesHistoryRef.get
      // jobIds relevant for the current node
      jobIdToJobDefAsRelevantForNode <- ZIO.attempt(
        openJobSnapshot.jobsForThisNodeSortedByPriority.map(x => (x.jobId, x.jobDefinition))
          .toMap
      )
      // for the relevant jobIds, pick the current processing states (where available; since we do not check here
      // explicitly, the workStateReader needs to be able to tolerate if for some passed jobIds no progress info is
      // available)
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
        .map(task =>
          BatchAndProcessingState(
            JobBatch(jobIdToJobDefAsRelevantForNode(task.stateId.jobId), task.stateId.batchNr),
            task
          )
        )
      )
    } yield tasksToAdd
  }

  private def movePlannedJobsToQueued(batches: Seq[BatchAndProcessingState]): ZIO[Any, Nothing, Chunk[Either[Throwable, ProcessingState]]] = {
    // update processing states and add planned tasks to queue
    ZStream.fromIterable(batches)
      .mapZIO(batchAndState => changeStateToQueuedAndAddToQueue(batchAndState).either)
      .runCollect
  }

  /**
   * Handling planned task by updating its processing status to state "QUEUED" and then adding the task to the
   * queue. If updating the processing status fails, the task will not be added to the queue for further processing
   */
  private def changeStateToQueuedAndAddToQueue(batchAndProcessingState: BatchAndProcessingState): Task[ProcessingState] = {
    for {
      _ <- persistProcessingStateUpdate(Seq(batchAndProcessingState.processingState), ProcessingStatus.QUEUED).map(x => x.head).flatMap({
        case true => ZIO.succeed(true)
        case false => ZIO.fail(new RuntimeException("Could not change processing state"))
      })
      addResult <- addPlannedTasksToQueueAndHistory(
        Seq(batchAndProcessingState.batch),
        Seq(batchAndProcessingState.processingState)
      )
    } yield addResult.head

  }

  /**
   * Given a sequence of batches, add each to the processing queue and to the history of added batches
   * (to avoid same batches being added multiple times).
   *
   * @param tasksToAdd   - all batches to add
   * @param plannedTasks - all tasks that are right now in "PLANNED" status, e.g intended to be added.
   *                     Note that right now for every batch code looks for matching state in plannedTasks,
   *                     would be more explicit bundling them in tuples or separate case class
   * @return for every batch that could succesfully be added to the queue return the ProcessingState. Those not contained
   *         were not added.
   */
  private def addPlannedTasksToQueueAndHistory(tasksToAdd: Seq[JobBatch[_, _, _ <: WithCount]], plannedTasks: Seq[ProcessingState]): Task[Seq[ProcessingState]] = {
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
          processIds
        })
      }
    } yield queueAddResult
  }

  /**
   * For the passed processing state, write an updated in-progress file with status
   * "QUEUED". Return sequence of booleans, each indicating whether changing process state was successful for
   * the particular batch
   */
  private def persistProcessingStateUpdate(processingStates: Seq[ProcessingState], processingStatus: ProcessingStatus): ZIO[Any, Nothing, Seq[Boolean]] = {
    val updatedState = processingStates.map(x => x.copy(processingInfo = x.processingInfo.copy(
      processingStatus = processingStatus,
      lastUpdate = ProcessingStateUtils.timeInMillisToFormattedTime(System.currentTimeMillis())
    )))
    ZStream.fromIterable(updatedState)
      .mapZIO(workStateWriter.updateInProgressState)
      .either
      .mapZIO({
        case Right(_) =>
          ZIO.succeed(true)
        case Left(error) =>
          ZIO.logError(s"Failed updating in-progress state:\n$error") *>
            ZIO.succeed(false)
      })
      .runFold(Seq.empty[Boolean])((oldSeq, newBool) => oldSeq :+ newBool)
  }

  /**
   * Pick next job to process, and initialize processing, keep ref to aggregator and
   * the fiber the processing is running on to be able to interrupt the computation.
   *
   * TODO: note that this is not safe in case we pick a Job from the queue and then
   * the further processing fails. Check baking it into an STM or change from Queue to size-limited Ref where
   * the element is only removed after all actions to initiate processing are finished
   *
   * TODO: we also need to instantiate resources in case this is the first batch for the respective job.
   * The same on finish of a job, we need to ensure resources are cleared again
   */
  private def processNextBatch: Task[Unit] = for {
    // pick
    job <- ZIO.ifZIO(queue.isEmpty)(
      onFalse = queue.take,
      onTrue = ZIO.fail(new RuntimeException("no element available"))
    )
    processingStateForJob <- workStateReader.processIdToProcessState(ProcessId(job.job.jobName, job.batchNr))
    _ <- persistProcessingStateUpdate(Seq(processingStateForJob), ProcessingStatus.IN_PROGRESS)
    _ <- TaskWorker.work(job)
      .forEachZIO(aggAndFiber => {
        processIdToAggregatorRef.update(oldMap =>
          oldMap + (ProcessId(job.job.jobName, job.batchNr) -> aggAndFiber._1.asInstanceOf[Ref[Aggregator[TaggedWithType with DataPoint[_], _ <: WithCount]]])
        ) *>
          processIdToFiberRef.update(oldMap =>
            oldMap + (ProcessId(job.job.jobName, job.batchNr) -> aggAndFiber._2))
      })
  } yield ()

  /**
   * Query the batch state from the current state of aggregators to update both the update timestamp
   * and the numbers of elements processed.
   * NOTE: needs to be invoked before checking for completion and removal.
   * NOTE: we might wanna keep the last process update after completion, as means to record processing
   * details.
   */
  private def updateProcessStates: Task[Unit] = {
    for {
      processIdToAggregatorMapping <- processIdToAggregatorRef.get
      _ <- ZStream.fromIterable(processIdToAggregatorMapping.keys)
        .foreach(processId => for {
          processAggregationState <- processIdToAggregatorMapping(processId).get
          processingState <- workStateReader.processIdToProcessState(processId)
          _ <- persistProcessingStateUpdate(
            Seq(processingState.copy(processingInfo = processingState.processingInfo.copy(numItemsProcessed = processAggregationState.aggregation.count))),
            processingState.processingInfo.processingStatus
          )
        } yield ()
        )
    } yield ()
  }

  /**
   * Check the fiber runtimes corresponding to the tasks in progress and return those with status DONE
   */
  private def getCompletedBatches: Task[Chunk[ProcessId]] = for {
    processFiberMapping <- processIdToFiberRef.get
    completedProcesses <- ZStream.fromIterable(processFiberMapping.toSeq)
      .mapZIO(tuple => for {
        status <- tuple._2.status
      } yield (tuple._1, status))
      .filter(x => x._2 == Fiber.Status.Done)
      .map(x => x._1)
      .runCollect
  } yield completedProcesses

  /**
   * Remove the in-progress state files for the passed processIDs and persist the corresponding batches
   * to done folder for the corresponding job.
   * Further remove the processIDs corresponding to the completed batches from the aggregation and fiber runtime mappings.
   */
  private def removeCompletedBatches(completedBatches: Seq[ProcessId]): Task[Unit] = {
    ZStream.fromIterable(completedBatches)
      .foreach(processId => {
        for {
          _ <- workStateWriter.deleteInProgressState(processId)
          _ <- workStateWriter.writeToDone(processId)
          _ <- processIdToAggregatorRef.update(map => map - processId)
          _ <- processIdToFiberRef.update(map => map - processId)
        } yield ()
      })
  }

  override def manageBatches(openJobSnapshot: OpenJobsSnapshot): Task[Unit] = {
    for {
      // get currently running processes
      runningProcesses <- processIdToFiberRef.get
      // cleanup orphaned running processes
      _ <- cleanUpProcessesWithMissingProcessingStates(openJobSnapshot, runningProcesses)

      // for all remaining running processes (those that have a corresponding in-progress state), update the current
      // state
      _ <- updateProcessStates

      // check Fiber.Runtime for each running process and if completed both remove processing status of batch and move
      // batch to "done" and remove the process from the process mapping
      completedProcessIds <- getCompletedBatches
      _ <- removeCompletedBatches(completedProcessIds)

      // pick the tasks in PLANNED state for the current node
      plannedTasksForNode <- pullPlannedTasks(openJobSnapshot)
      // for planned tasks try to change processing status to queued and add to queue
      _ <- movePlannedJobsToQueued(plannedTasksForNode)

      // fill empty slots for processing with tasks from queue, see processNextBatch
      nrOpenProcessSlots <- processIdToAggregatorRef.get
        .map(x => x.toSet.count(_ => true))
        .map(runningJobCount => AppProperties.config.maxNrJobsProcessing - runningJobCount)
      _ <- ZStream(Range(0, nrOpenProcessSlots, 1))
        .foreach(_ => processNextBatch)
    } yield ()
  }

  // pick the newest processing states for all processed batches and update the respective files
  override def persistProcessStates: Task[Unit] = ???
}
