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
import de.awagen.kolibri.definitions.directives.Resource
import de.awagen.kolibri.definitions.io.json.ResourceJsonProtocol.AnyResourceFormat
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{JobBatch, JobDefinition}
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.WorkStateWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.BaseWorkHandlerService.{BatchAndProcessingState, addTasksToHistory, compareRunningBatchesWithSnapshotAndInterruptIfIndicated, getCompletedBatches, removeKeysFromMappings}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus.ProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DataTypeUtils
import spray.json.DefaultJsonProtocol.immSetFormat
import spray.json.enrichAny
import zio.stream.ZStream
import zio.{Chunk, Exit, Fiber, Queue, Ref, Task, UIO, ZIO}


object BaseWorkHandlerService {

  case class BatchAndProcessingState(batch: JobBatch[_, _, _ <: WithCount], processingState: ProcessingState)

  /**
   * Logic to interrupt running jobs that are not present in the
   * persisted processing states anymore. This might mean that a claimed job was revoked
   * due to node inactivity or similar. Ensures consistency of processing with persisted
   * processing state
   *
   * @param processingIds         - the processing states currently persisted
   * @param processToFiberMapping - the mapping of processIds to the actual Fiber on which they are currently processed.
   *                              This reflects the jobs that are actually running right now (or that completed already).
   *                              There can be an inconsistency between currently running jobs and those for which
   *                              an in-progress state exists, e.g in case the node did not update the in-progress state
   *                              frequent enough, in which case other nodes can claim the right to revoke the
   *                              claimed batch for this node by means of removing the in-progress state for the current
   *                              node and moving the batch back to "open", for all nodes to claim
   */
  private def compareRunningBatchesWithSnapshotAndInterruptIfIndicated(processingIds: Seq[ProcessId],
                                                                       processToFiberMapping: Map[ProcessId, Fiber.Runtime[Throwable, Unit]]): Task[Chunk[(ProcessId, Exit[Throwable, Unit])]] = {
    for {
      runningBatchesWithoutInProgressFile <- ZIO.attempt(processToFiberMapping.keys.filter(batch => !processingIds.contains(batch)).toSeq)
      fiberInterruptResults <- interruptRunningProcessIds(runningBatchesWithoutInProgressFile, processToFiberMapping)
    } yield fiberInterruptResults
  }

  /**
   * NOTE: in case a blocking operation is wrapped in ZIO.attemptBlocking, a fiber interrupt will not cause interruption
   * of the execution. To ensure this we need ZIO.attemptBlockingInterrupt or attemptBlockingCancelable
   * (https://zio.dev/reference/interruption/).
   * Note that the former adds overhead, the latter needs manually adding
   * some kill-switch (e.g atomic reference used as flag to handle stopping of execution
   * excplicitly or similar).
   */
  private[services] def interruptRunningProcessIds(processIds: Seq[ProcessId],
                                                   processToFiberMapping: Map[ProcessId, Fiber.Runtime[Throwable, Unit]]): UIO[Chunk[(ProcessId, Exit[Throwable, Unit])]] = {
    for {
      _ <- ZIO.logDebug(s"Trying to interrupt running processes: $processIds")
      fiberInterruptResults <- ZStream.fromIterable(processIds)
        .tap(processId => ZIO.logDebug(s"Trying to interrupt fiber for processId: $processId"))
        .map(processId => (processId, processToFiberMapping(processId)))
        .mapZIO(processIdAndFiber => {
          processIdAndFiber._2
            .interrupt
            .map(x => (processIdAndFiber._1, x))
        })
        .runCollect
    } yield fiberInterruptResults
  }

  /**
   * Remove set of keys from passed mapping references
   */
  private[services] def removeKeysFromMappings[T](keys: Set[T], mappings: Seq[Ref[Map[T, Any]]]): ZIO[Any, Nothing, Unit] = {
    ZStream.fromIterable(mappings)
      .mapZIO(mapRef => {
        ZStream.fromIterable(keys)
          .foreach(key => mapRef.update(map => map - key))
      })
      .runDrain
  }

  /**
   * Check the fiber runtimes corresponding to the tasks in progress and return those with status DONE
   */
  private def getCompletedBatches(processFiberMapping: Map[ProcessId, Fiber.Runtime[Throwable, Unit]]): Task[Chunk[ProcessId]] = for {
    completedProcesses <- ZStream.fromIterable(processFiberMapping.toSeq)
      .mapZIO(tuple => for {
        status <- tuple._2.status
      } yield (tuple._1, status))
      .filter(x => x._2 == Fiber.Status.Done)
      .map(x => x._1)
      .runCollect
  } yield completedProcesses

  /**
   * adding tasks to history. The history is just a safe-guard against adding single batches multiple times in
   * case state updating failed for some reason and thus the status of planned tasks fail to be updated.
   */
  private def addTasksToHistory(tasks: Seq[ProcessId], historyRef: Ref[Seq[ProcessId]]): ZIO[Any, Nothing, Unit] = {
    ZStream.fromIterable(tasks)
      .foreach(task => {
        historyRef.update(
          history => (history :+ task).takeRight(AppProperties.config.pulledTaskHistorySize)
        )
      })
  }

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
case class BaseWorkHandlerService[U <: TaggedWithType with DataPoint[Any], V <: WithCount](claimReader: ClaimReader,
                                                                                           workStateReader: WorkStateReader,
                                                                                           workStateWriter: WorkStateWriter,
                                                                                           queue: Queue[JobBatch[_, _, _ <: WithCount]],
                                                                                           addedBatchesHistoryRef: Ref[Seq[ProcessId]],
                                                                                           processIdToAggregatorRef: Ref[Map[ProcessId, Ref[Aggregator[U, V]]]],
                                                                                           processIdToFiberRef: Ref[Map[ProcessId, Fiber.Runtime[Throwable, Unit]]],
                                                                                           resourceToJobIdsRef: Ref[Map[Resource[Any], Set[String]]],
                                                                                           jobIdToJobDefRef: Ref[Map[String, JobDefinition[_, _, _ <: WithCount]]]) extends WorkHandlerService {

  /**
   * Given a current snapshot of the open job state and mapping of ProcessId to Fiber,
   * interrupt processes that do not match the current persisted state.
   *
   * @param currentOpenJobIds     - ids of all open jobs known persisted at current time
   * @param processToFiberMapping - mapping of process Id to the Fiber.Runtime the job corresponding to the processId
   *                              is running on
   */
  private def cleanUpProcessesWithMissingProcessingStates(currentOpenJobIds: Set[String],
                                                          processToFiberMapping: Map[ProcessId, Fiber.Runtime[Throwable, Unit]]): Task[Unit] = {
    for {
      // get all process states for the current jobs
      existingProcessStatesForJobs <- workStateReader.getInProgressStateForCurrentNode(currentOpenJobIds)
      // interrupt all running processes for which there is no progress state persisted
      fiberInterruptResults <- compareRunningBatchesWithSnapshotAndInterruptIfIndicated(existingProcessStatesForJobs.values.flatten.map(x => x.stateId).toSeq, processToFiberMapping)
      _ <- ZIO.when(fiberInterruptResults.nonEmpty)(ZIO.logInfo(s"Cleaning up processes: ${fiberInterruptResults.map(x => x._1)}"))
      // remove the cancelled processIds from the aggregator and fiber mappings
      _ <- removeKeysFromMappings(fiberInterruptResults.map(x => x._1).toSet, Seq(processIdToFiberRef, processIdToAggregatorRef).map(x => x.asInstanceOf[Ref[Map[ProcessId, Any]]]))
      _ <- removeKeysFromMappings(fiberInterruptResults.map(x => x._1.jobId).toSet, Seq(jobIdToJobDefRef).map(x => x.asInstanceOf[Ref[Map[String, Any]]]))
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
      processStates <- workStateReader.getInProgressStateForCurrentNode(jobIdToJobDefAsRelevantForNode.keySet)
      _ <- ZIO.logDebug(s"Process states: $processStates")
      // planned tasks are those we wanna add to the job-queue
      plannedTasks <- ZIO.succeed(
        processStates.values.flatten
          .filter(x => x.processingInfo.processingStatus == ProcessingStatus.PLANNED)
          .filter(x => !historySeq.contains(x.stateId))
          .toSeq
      )
      _ <- ZIO.logDebug(s"Planned tasks: $plannedTasks")
      // for all planned tasks, generate the JobBatch object
      tasksToAdd <- ZIO.attempt(
        plannedTasks
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

  private def movePlannedJobsToQueued(batches: Seq[BatchAndProcessingState]): ZIO[Any, Nothing, Chunk[Either[Throwable, ProcessId]]] = {
    // update processing states and add planned tasks to queue
    ZStream.fromIterable(batches)
      .mapZIO(batchAndState => changeStateToQueuedAndAddToQueue(batchAndState).either)
      .runCollect
  }

  /**
   * Handling planned task by updating its processing status to state "QUEUED" and then adding the task to the
   * queue. If updating the processing status fails, the task will not be added to the queue for further processing.
   * Note that the number of elements to be processed in total is also filled in here
   */
  private def changeStateToQueuedAndAddToQueue(batchAndProcessingState: BatchAndProcessingState): Task[ProcessId] = {
    for {
      // extract the nr of elements to be processed in the batch
      numElementsInBatch <- ZIO.attempt(
        batchAndProcessingState.batch.job.batches
          .get(batchAndProcessingState.processingState.stateId.batchNr)
          .map(x => x.data.size).getOrElse(-1)
      )
      // update the processingState with information about the contained elements to be processed
      // (NOTE: this could be done before reaching the PLANNED state here, yet the task planners are
      // reasoning without necessary needing to connect to details of execution, thus we initialize the info here)
      processingStateWithElementCount <- ZIO.attempt(
        batchAndProcessingState.processingState.copy(processingInfo = batchAndProcessingState.processingState.processingInfo.copy(numItemsTotal = numElementsInBatch))
      )
      _ <- persistProcessingStateUpdate(Seq(processingStateWithElementCount), ProcessingStatus.QUEUED).map(x => x.head).flatMap({
        case true => ZIO.succeed(true)
        case false => ZIO.fail(new RuntimeException("Could not change processing state"))
      })
      addResult <- addTasksToQueue(Seq(batchAndProcessingState.batch), Seq(batchAndProcessingState.processingState.stateId))
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
  private def addTasksToQueue(tasksToAdd: Seq[JobBatch[_, _, _ <: WithCount]], plannedTasks: Seq[ProcessId]): ZIO[Any, Throwable, Seq[ProcessId]] = {
    for {
      _ <- ZIO.when(tasksToAdd.nonEmpty)(ZIO.logDebug(s"""Adding tasks to queue: ${tasksToAdd.map(x => ProcessId(x.job.jobName, x.batchNr)).mkString(",")}"""))
      queueAddResult <- {
        DataTypeUtils.addElementsToQueue(tasksToAdd, queue).map(x => {
          val onlySuccesses = x.filter(identity)
          val processIds = tasksToAdd
            .take(onlySuccesses.size)
            .map(batch => plannedTasks.find(tasks => tasks.jobId == batch.job.jobName && tasks.batchNr == batch.batchNr))
            .filter(x => x.nonEmpty)
            .map(x => x.get)
          processIds
        })
      }
    } yield queueAddResult
  }

  /**
   * For the passed processing state, write an updated in-progress file with passed status value.
   * Return sequence of booleans, each indicating whether changing process state was successful for
   * the particular batch. Besides the state and status update, lastUpdate timestamp is updated.
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

  private def addResourceJobMapping(jobId: String, resources: Seq[Resource[Any]]): Task[Unit] = {
    ZStream.fromIterable(resources)
      .foreach(resource => resourceToJobIdsRef.update(map => {
        map + (resource -> (map.getOrElse(resource, Set.empty[String]) + jobId))
      }))
  }

  /**
   * Pick next job to process, and initialize processing, keep ref to aggregator and
   * the fiber the processing is running on to be able to interrupt the computation.
   *
   * In case there is no next element, this call will finish successfully with boolean value false,
   * and log a warning
   */
  private def processNextBatch: Task[Boolean] = {
    (for {
      // pick job from queue and start processing
      job <- queue.poll.flatMap(x => ZIO.fromOption(x))
      // add mapping of resource to job names of jobs utilizing the resource
      _ <- addResourceJobMapping(job.job.jobName, job.job.resourceSetup.map(x => x.resource))
      processingStateForJobOpt <- workStateReader.processIdToProcessState(ProcessId(job.job.jobName, job.batchNr),
        AppProperties.config.node_hash)
      res <- ZIO.ifZIO(ZIO.succeed(processingStateForJobOpt.nonEmpty))(
        onTrue = {
          val processingEffect = for {
            _ <- persistProcessingStateUpdate(
              Seq(processingStateForJobOpt.get),
              ProcessingStatus.IN_PROGRESS
            )
            _ <- TaskWorker.work(job)
              .forEachZIO(aggAndFiber => {
                for {
                  _ <- processIdToAggregatorRef.update(oldMap =>
                    oldMap + (ProcessId(job.job.jobName, job.batchNr) -> aggAndFiber._1.asInstanceOf[Ref[Aggregator[U, V]]])
                  )
                  _ <- processIdToFiberRef.update(oldMap =>
                    oldMap + (ProcessId(job.job.jobName, job.batchNr) -> aggAndFiber._2))
                  jobIdToJobDef <- jobIdToJobDefRef.get
                  _ <- ZIO.when(!jobIdToJobDef.contains(job.job.jobName))({
                    jobIdToJobDefRef.update(oldMap => oldMap + (job.job.jobName -> job.job))
                  })
                } yield ()
              })
          } yield true
          ZIO.logInfo(s"Starting processing job '${job.job.jobName}', batch ${job.batchNr}") *> processingEffect
        },
        onFalse = ZIO.succeed(false)
      )
    } yield res).catchAll(err => {
      ZIO.logWarning(s"processing next batch not successful:\n$err") *>
        ZIO.succeed(false)
    })
  }

  /**
   * Remove the in-progress state files for the passed processIDs and persist the corresponding batches
   * to done folder for the corresponding job.
   * Further remove the processIDs corresponding to the completed batches from the aggregation and fiber runtime mappings.
   */
  private def moveInProgressFileToDoneAndCleanupBatches(batches: Seq[ProcessId]): Task[Unit] = {
    ZStream.fromIterable(batches)
      .foreach(processId => {
        for {
          _ <- workStateWriter.moveToDone(processId, AppProperties.config.node_hash)
          _ <- processIdToAggregatorRef.update(map => map - processId)
          _ <- processIdToFiberRef.update(map => map - processId)
          _ <- jobIdToJobDefRef.update(map => map - processId.jobId)
        } yield ()
      })
  }

  override def manageBatches(openJobSnapshot: OpenJobsSnapshot): Task[Unit] = {
    for {
      // get currently running processes
      runningProcesses <- processIdToFiberRef.get
      // cleanup orphaned running processes
      _ <- cleanUpProcessesWithMissingProcessingStates(openJobSnapshot.jobStateSnapshots.keySet, runningProcesses)

      // check Fiber.Runtime for each running process and if completed both remove processing status of batch and move
      // batch to "done" and remove the process from the process mapping
      processFiberMapping <- processIdToFiberRef.get
      completedProcessIds <- getCompletedBatches(processFiberMapping)
      // for all remaining running processes (those that have a corresponding in-progress state), update the current
      // state (means: persisting the current state with updated timestamp)
      _ <- updateProcessStates
      // move / delete the completed batches
      _ <- ZIO.when(completedProcessIds.nonEmpty)(ZIO.logInfo(s"Completed processes: $completedProcessIds"))
      _ <- moveInProgressFileToDoneAndCleanupBatches(completedProcessIds)

      // pick the tasks in PLANNED state for the current node
      plannedTasksForNode <- pullPlannedTasks(openJobSnapshot)
      _ <- ZIO.when(plannedTasksForNode.nonEmpty)(ZIO.logInfo(s"""Adding planned tasks to queue:\n${plannedTasksForNode.map(x => x.processingState.stateId).mkString("\n")}"""))
      // for planned tasks try to change processing status to queued and add to queue
      addedToQueue <- movePlannedJobsToQueued(plannedTasksForNode)
      // add the tasks added to queue to history record
      _ <- addTasksToHistory(addedToQueue.filter(x => x.isRight).map(x => x.toOption.get), addedBatchesHistoryRef)

      // fill empty slots for processing with tasks from queue, see processNextBatch
      nrOpenProcessSlots <- getNrOpenProcessSlots
      elementsInQueue <- queue.size
      numItemsToPullIntoProcessing <- ZIO.succeed(math.max(0, math.min(elementsInQueue, nrOpenProcessSlots)))
      _ <- ZIO.when(numItemsToPullIntoProcessing > 0)(ZIO.logInfo(s"Filling open processing slots - capacity: $nrOpenProcessSlots, available queued tasks: $elementsInQueue, items to pull from queue to processing: $numItemsToPullIntoProcessing"))
      _ <- ZIO.loop(numItemsToPullIntoProcessing)(_ > 0, _ - 1)(_ => processNextBatch)

      // clean up loaded resources that are not needed anymore
      _ <- cleanUpGlobalResources

    } yield ()
  }

  /**
   * Get nr of open slots for processing.
   * This nr of tasks could at max be pulled from queue and started processing.
   */
  private def getNrOpenProcessSlots: ZIO[Any, Nothing, Int] = {
    for {
      nrOpenSlots <- processIdToAggregatorRef.get
        .map(x => x.toSet.count(_ => true))
        .map(runningJobCount => AppProperties.config.maxNrJobsProcessing - runningJobCount)
    } yield nrOpenSlots
  }

  /**
   * Clean up jobName -> resource references and remove those global resources which has no assigned jobName anymore
   */
  private def cleanUpGlobalResources: Task[Unit] = {
    for {
      // remove all job names for which current node neither has running batch nor filed any claim for such
      // from resourceToJobIdsRef mappings and free resources with no jobId assignment.
      jobsWithResourceMappings <- resourceToJobIdsRef.get.map(x => x.values.flatten.toSet)
      runningJobs <- processIdToAggregatorRef.get.map(x => x.keySet.map(y => y.jobId))
      // for jobs with current resource assignment, get those jobs for which the current node has any claim filed
      jobsWithClaims <- claimReader.getClaimsByCurrentNodeForTopicAndJobIds(TaskTopics.JobTaskProcessingTask, jobsWithResourceMappings)
        .map(x => x.map(y => y.jobId))
      // remove all resource - jobName assignments for jobs that are neither running anymore nor part of any claim
      _ <- ZStream.fromIterable(jobsWithResourceMappings -- runningJobs -- jobsWithClaims)
        .foreach(removeJobId => {
          resourceToJobIdsRef.update(map => map.toSeq.map(x => (x._1, x._2 - removeJobId)).toMap)
        })
      // delete those resources for which no mapping to a running or claimed job exists
      resourcesWithoutJobs <- resourceToJobIdsRef.get.map(mapping => mapping.filter(x => x._2.isEmpty).keys.toSet)
      _ <- ZIO.when(resourcesWithoutJobs.nonEmpty)(ZIO.logInfo(s"Cleaning up resources since no reference to any job exists anymore: " +
        s"${resourcesWithoutJobs.toJson.prettyPrint}"))
      _ <- ZStream.fromIterable(resourcesWithoutJobs)
        .foreach(resource => {
          ZIO.attempt(NodeResourceProvider.removeResource(resource)) <* (for {
            _ <- resourceToJobIdsRef.update(x => x - resource)
          } yield ())
        })
    } yield ()
  }

  /**
   * Query the batch state from the current state of aggregators to update both the update timestamp
   * and the numbers of elements processed in the persisted in-progress files.
   *
   * Note: state update will only be persisted if the in-progress state info still exists.
   * If the entry is not found, the update will not do anything, since the task is up for being
   * removed from processing due to mismatch to the in-progress state by the cleanup methods.
   */
  override def updateProcessStates: Task[Unit] = {
    for {
      processIdToAggregatorMapping <- processIdToAggregatorRef.get
      processIdToFiberMapping <- processIdToFiberRef.get
      _ <- ZStream.fromIterable(processIdToAggregatorMapping.keys)
        .foreach(processId => for {
          processAggregationState <- processIdToAggregatorMapping(processId).get
          processFiberStatus <- processIdToFiberMapping(processId).status
          processingStateOpt <- workStateReader.processIdToProcessState(processId, AppProperties.config.node_hash)
          _ <- ZIO.ifZIO(ZIO.succeed(processingStateOpt.nonEmpty))(
            onTrue = {
              for {
                processingState <- ZIO.attempt(processingStateOpt.get)
                updatedProcessingState <- ZIO.attempt(processingState.copy(processingInfo = processingState.processingInfo.copy(numItemsProcessed = processAggregationState.aggregation.count)))
                _ <- persistProcessingStateUpdate(
                  Seq(updatedProcessingState),
                  if (processFiberStatus == Fiber.Status.Done) ProcessingStatus.DONE else processingState.processingInfo.processingStatus
                )
              } yield ()
            },
            onFalse = {
              ZIO.logWarning(s"In-Progress state for processId '$processId' could not be updated due to" +
                s" missing in-progress state")
            })
        } yield ())
    } yield ()
  }

}
