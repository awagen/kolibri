/**
 * Copyright 2021 Andreas Wagenmann
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


package de.awagen.kolibri.base.actors.clusterinfo

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}
import de.awagen.kolibri.base.actors.clusterinfo.BatchStateActor.{AllCurrentBatchStates, BatchFinishedEvent, GetAllCurrentBatchStates, HouseKeeping}
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor.BatchProcessStateResult
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.datatypes.io.KolibriSerializable

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


object BatchStateActor {

  def props(houseKeepingIntervalInSeconds: Int, houseKeepingMaxNonUpdateTimeInSeconds: Int): Props =
    Props(BatchStateActor(houseKeepingIntervalInSeconds, houseKeepingMaxNonUpdateTimeInSeconds))

  trait BatchStateActorMsg extends KolibriSerializable

  case class BatchFinishedEvent(jobId: String, batchNr: Int) extends BatchStateActorMsg

  case object HouseKeeping extends BatchStateActorMsg

  case object GetAllCurrentBatchStates extends BatchStateActorMsg

  case class AllCurrentBatchStates(states: Seq[BatchProcessStateResult]) extends BatchStateActorMsg

}

/**
 * Actor acting as central receiver of batch job state messages.
 * Does some internal housekeeping after defined time. Batches that did not receive any updates within
 * a given time will be removed from tracked state
 *
 * @param houseKeepingIntervalInSeconds         - The interval between successive housekeepings
 * @param houseKeepingMaxNonUpdateTimeInSeconds - The maximal time that can pass between two status updates. If exceeded, batch state is removed from tracking.
 */
case class BatchStateActor(houseKeepingIntervalInSeconds: Int, houseKeepingMaxNonUpdateTimeInSeconds: Int) extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  var batchStates: mutable.Map[(String, Int), BatchProcessStateResult] = mutable.Map.empty
  var lastBatchUpdates: mutable.Map[(String, Int), Double] = mutable.Map.empty

  val houseKeepingSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = houseKeepingIntervalInSeconds seconds,
    interval = houseKeepingIntervalInSeconds seconds,
    receiver = self,
    message = HouseKeeping)

  def deleteKey(key: (String, Int)): Unit = {
    batchStates -= key
    lastBatchUpdates -= key
  }

  /**
   * Cancels the houseKeepingSchedule and then proceeds with normal postStop.
   * We wanna avoid generating dead letters to self
   */
  override def postStop(): Unit = {
    houseKeepingSchedule.cancel()
    super.postStop()
  }

  override def receive: Receive = {
    case batchState: RunnableExecutionActor.BatchProcessStateResult =>
      log.debug(s"received batch state update: $batchState")
      val batchKey: (String, Int) = (batchState.jobId, batchState.batchNr)
      batchStates += (batchKey -> batchState)
      lastBatchUpdates += (batchKey -> System.currentTimeMillis())
    case BatchFinishedEvent(jobId, batchNr) =>
      log.debug(s"received batch finished event for jobId '$jobId' and batchNr '$batchNr'")
      val batchKey: (String, Int) = (jobId, batchNr)
      deleteKey(batchKey)
    case HouseKeeping =>
      batchStates.keys.foreach(x => {
        lastBatchUpdates.get(x).foreach(time => {
          val expiredTime = (System.currentTimeMillis() - time) / 1000.0
          if (expiredTime > houseKeepingMaxNonUpdateTimeInSeconds) {
            log.debug(s"didnt receive any update for batch with jobId '${x._1}', batchNr '${x._2}', removing from state tracking")
            deleteKey(x)
          }
        })
      })
    case GetAllCurrentBatchStates =>
      sender() ! AllCurrentBatchStates(batchStates.values.toSeq)
  }
}
