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


package de.awagen.kolibri.fleet.akka.actors.clusterinfo

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}
import de.awagen.kolibri.base.processing.ProcessingMessages.JobStatusInfo
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.BatchStateActor._
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.GetJobWorkerStatus
import de.awagen.kolibri.fleet.akka.actors.work.manager.JobProcessingState.emptyJobStatusInfo
import de.awagen.kolibri.fleet.akka.actors.work.worker.RunnableExecutionActor
import de.awagen.kolibri.fleet.akka.actors.work.worker.RunnableExecutionActor.BatchProcessStateResult
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.kolibriDispatcherName
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

  case object ProvideAllRunningJobStates extends BatchStateActorMsg

  case class AllCurrentBatchStates(states: Seq[BatchProcessStateResult]) extends BatchStateActorMsg


  case object GetStatusForWorkers extends BatchStateActorMsg

  case class WorkerStatusResponse(result: Seq[BatchProcessStateResult]) extends BatchStateActorMsg

  case class ProvideJobStatus(jobId: String) extends BatchStateActorMsg
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

  val batchStatusDesc: String = "Batch Status"
  var batchStates: mutable.Map[(String, Int), BatchProcessStateResult] = mutable.Map.empty
  var lastBatchUpdates: mutable.Map[(String, Int), Double] = mutable.Map.empty

  val jobStatusDesc: String = "Job Status"
  var jobStates: mutable.Map[String, JobStatusInfo] = mutable.Map.empty
  var lastJobUpdates: mutable.Map[String, Double] = mutable.Map.empty

  val workerStatusDesc: String = "Worker Status"
  var workerStateAndLastUpdateTimestamp: Option[(WorkerStatusResponse, Double)] = None


  val houseKeepingSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = houseKeepingIntervalInSeconds seconds,
    interval = houseKeepingIntervalInSeconds seconds,
    receiver = self,
    message = HouseKeeping)

  def deleteBatchKey(key: (String, Int)): Unit = {
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

  def housekeeping[T, U](dataMap: mutable.Map[T, U], lastUpdateMap: mutable.Map[T, Double], dataDesc: String): Unit = {
    dataMap.keys.foreach(x => {
      lastUpdateMap.get(x).foreach(time => {
        val expiredTime = (System.currentTimeMillis() - time) / 1000.0
        if (expiredTime > houseKeepingMaxNonUpdateTimeInSeconds) {
          log.debug(s"didnt receive any update for data with type '$dataDesc' and key '$x', removing from state tracking")
          dataMap -= x
          lastUpdateMap -= x
        }
      })
    })
  }

  def isExpired[T](data: (T, Double), dataDesc: String): Boolean = {
    val expiredTime = (System.currentTimeMillis() - data._2) / 1000.0
    if (expiredTime > houseKeepingMaxNonUpdateTimeInSeconds) {
      log.debug(s"didnt receive any update for data with type '$dataDesc', removing from state tracking")
      true
    }
    else {
      false
    }
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
      deleteBatchKey(batchKey)
    case HouseKeeping =>
      housekeeping(batchStates, lastBatchUpdates, batchStatusDesc)
      housekeeping(jobStates, lastJobUpdates, jobStatusDesc)
      workerStateAndLastUpdateTimestamp = workerStateAndLastUpdateTimestamp
        .flatMap(x => {
          if (isExpired(x, workerStatusDesc)) None
          else Some(x)
        })
    case GetAllCurrentBatchStates =>
      sender() ! AllCurrentBatchStates(batchStates.values.toSeq)
    case msg: JobStatusInfo =>
      log.debug(s"received job state update: $msg")
      jobStates += (msg.jobId -> msg)
      lastJobUpdates += (msg.jobId -> System.currentTimeMillis())
    case msg: ProvideJobStatus =>
      log.info("received provide job status msg")
      val response = jobStates.getOrElse(msg.jobId, emptyJobStatusInfo)
      log.info(s"ProvideJobStatus response: $response")
      sender() ! response
    case msg: WorkerStatusResponse =>
      log.debug(s"received worker state update: $msg")
      workerStateAndLastUpdateTimestamp = Some((msg, System.currentTimeMillis()))
    case GetJobWorkerStatus(jobId) =>
      sender() ! workerStateAndLastUpdateTimestamp.map(x => x._1)
        .map(x => WorkerStatusResponse(x.result.filter(state => state.jobId == jobId)))
        .getOrElse(WorkerStatusResponse(Seq.empty))
    case ProvideAllRunningJobStates =>
      sender() ! jobStates.values.toSeq

  }
}
