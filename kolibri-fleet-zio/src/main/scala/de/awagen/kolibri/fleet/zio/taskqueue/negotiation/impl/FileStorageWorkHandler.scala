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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ProcessUpdateStatus.ProcessUpdateStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.{JobStateHandler, WorkHandler}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DataTypeUtils.addElementsToQueueIfEmptySlots
import zio.{Queue, Task, ZIO}


/**
 * a) Accepts batches to work on up to limit as given by the queue
 */
case class FileStorageWorkHandler(batchQueue: Queue[JobBatch[_, _, _]], jobStateHandler: JobStateHandler) extends WorkHandler {

  override def updateProcessStatus(): Task[ProcessUpdateStatus] = ???

  def numFreeSlots: ZIO[Any, Nothing, Int] = batchQueue.size.map(size => batchQueue.capacity - size)

  /**
   * Trying to add a single batch. Note that this deviates from the queue.offer case where
   * on size limited queues the fiber would stall till there is room for an element.
   * Here we check first whether queue is full, and avoid waiting in case queue is full.
   */
  override def addBatch(batch: JobBatch[_, _, _]): Task[Boolean] = {
    addBatches(Seq(batch)).map(x => x.head)
  }

  /**
   * Try to add multiple batches.
   * Deviates from the original queue.offerAll since we want the addition to stop on the
   * first fail instead of back-pressuring and adding later.
   * The returned sequence contains true for each element where offering to the queue
   * succeeded, and false where it failed. Note that there will be at most one
   * element with value false, and this would be the last element of the sequence,
   * since we abort further offerings in case an offering was not successful.
   */
  override def addBatches(batches: Seq[JobBatch[_, _, _]]): Task[Seq[Boolean]] = {
    addElementsToQueueIfEmptySlots(batches, batchQueue)
  }

  // TODO: now we have the means to fill the job queue,
  // yet we also need to consume the tasks to create TaskWorkers
  // which process them, using TaskExecution instance


  /**
   * Fetch the in-progress state information to retrieve info a) which batches to stop
   * (job level directives from root job folder), b) which to pull in for processing,
   * c) which to write into done folder. On each round, also update the processing state file
   * even if the state itself remains the same. The timestamp here will be used by cleanup handler
   * to decide if a node timed out, which means deleting the in-progress file and writing the batch back
   * to open-folder. The missing in-progress file should then be picked up by this node sth the processing
   * is actually stopped
   */
  def fetchInProgressState: Task[Unit] = ???

  /**
   * Manage the running tasks. If a batch execution runs and flag is set to ignore the related job
   * on this node, stop execution, delete the in-progress state and file the batch file again in the
   * open folder so that other nodes can pick them up.
   * If free workers available, pull a claimed batch, e.g a process-state file with marked status
   * PLANNED into the execution queue and regularly update the status for each execution.
   */
  def manageWork(): Task[Unit] = {
    for {
      // TODO:
      openJobsSnapshot <- jobStateHandler.fetchOpenJobState

    } yield ()

  }

}
