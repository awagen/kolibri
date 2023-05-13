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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.WorkHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DataTypeUtils.addElementsToQueueIfEmptySlots
import zio.{Queue, Task, ZIO}


/**
 * a) Accepts batches to work on up to limit as given by the queue
 */
case class FileStorageWorkHandler(batchQueue: Queue[JobBatch[_, _]]) extends WorkHandler {

  override def updateProcessStatus(): Task[ProcessUpdateStatus] = ???

  def numFreeSlots: ZIO[Any, Nothing, Int] = batchQueue.size.map(size =>  batchQueue.capacity - size)

  /**
   * Trying to add a single batch. Note that this deviates from the queue.offer case where
   * on size limited queues the fiber would stall till there is room for an element.
   * Here we check first whether queue is full, and avoid waiting in case queue is full.
   */
  override def addBatch(batch: JobBatch[_,_]): Task[Boolean] = {
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
  override def addBatches(batches: Seq[JobBatch[_,_]]): Task[Seq[Boolean]] = {
    addElementsToQueueIfEmptySlots(batches, batchQueue)
  }

  // TODO: now we have the means to fill the job queue,
  // yet we also need to consume the tasks to create TaskWorkers
  // which process them, using TaskExecution instance

}
