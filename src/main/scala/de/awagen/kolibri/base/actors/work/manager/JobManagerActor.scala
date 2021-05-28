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

package de.awagen.kolibri.base.actors.work.manager


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import de.awagen.kolibri.base.actors.resources.BatchFreeSlotResourceCheckingActor.AddToRunningBaselineCount
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.ProcessingResult.RUNNING
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{ActorRunnableJobGenerator, FinishedJobEvent, ProcessingResult}
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor._
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor.{ExecutionType, GetWorkerStatus}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, ResultSummary}
import de.awagen.kolibri.base.config.AppConfig._
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object JobManagerActor {

  final def name(jobId: String) = s"jobManager-$jobId"

  def props[T, U](experimentId: String,
               runningTaskBaselineCount: Int,
               aggregatorSupplier: SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[T], U]],
               writer: Writer[U, Tag, _],
               maxProcessDuration: FiniteDuration,
               maxBatchDuration: FiniteDuration): Props =
    Props(new JobManagerActor(experimentId, runningTaskBaselineCount, aggregatorSupplier, writer, maxProcessDuration, maxBatchDuration))


  // cmds telling JobManager to do sth
  sealed trait ExternalJobManagerCmd extends KolibriSerializable

  case class ProcessJobCmd[U, V, V1, W](job: ActorRunnableJobGenerator[U, V, V1, W]) extends ExternalJobManagerCmd

  case object ProvideJobStatus extends ExternalJobManagerCmd

  case object ExpectationMet extends ExternalJobManagerCmd

  case object ExpectationFailed extends ExternalJobManagerCmd

  private sealed trait InternalJobManagerCmd extends KolibriSerializable

  private case object WriteResultAndSendFailNoteAndTakePoisonPillCmd extends InternalJobManagerCmd

  private case object DistributeBatches extends InternalJobManagerCmd

  private case object UpdateStateAndCheckForCompletion extends InternalJobManagerCmd

  // cmds providing some event info to JobManagerActor (e.g in case some result finished computing))
  sealed trait JobManagerEvent extends KolibriSerializable

  case class JobStatusInfo(jobId: String, resultSummary: ResultSummary) extends JobManagerEvent

  case class ACK(jobId: String, batchNr: Int, sender: ActorRef) extends JobManagerEvent

  case class MaxTimeExceededEvent(jobId: String) extends JobManagerEvent

  case object GetStatusForWorkers extends ExternalJobManagerCmd

  case class WorkerStatusResponse[U](result: Either[Throwable, Seq[AggregationState[U]]]) extends JobManagerEvent

  case class WorkerKilled(batchNr: Int) extends JobManagerEvent

}

class JobManagerActor[T, U](val jobId: String,
                         runningTaskBaselineCount: Int,
                         val aggregatorSupplier: SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[T], U]],
                         val writer: Writer[U, Tag, _],
                         val maxProcessDuration: FiniteDuration,
                         val maxBatchDuration: FiniteDuration) extends Actor with ActorLogging with KolibriSerializable {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // same aggregator also passed to batch processing. Within batches aggregates single elements
  // here aggregates single aggregations (one result per batch)
  var aggregator: Aggregator[TaggedWithType[Tag] with DataStore[T], U] = aggregatorSupplier.get()
  // nr of concurrent batches in processing
  var continuousRunningTaskCount: Int = runningTaskBaselineCount
  // nr of total batches to process, set after job cmd is sent here
  var nrOfBatchesTotal: Int = 0
  // counts of batches sent to process already
  var nrOfBatchesSent: Int = 0
  // counts of batch process results received so far
  var nrOfResultsReceived: Int = 0
  // actor to which to send notification of completed processing
  var reportResultsTo: ActorRef = _
  // execution expectation
  val executionExpectationMap: mutable.Map[Int, ExecutionExpectation] = mutable.Map.empty

  // for those batches we havent yet received a confirmation that it is processed,
  // and timeout for wait has not yet passed after which itll be moved to failedACKReceiveBatchNumbers
  var batchesSentWaitingForACK: Set[Int] = Set.empty
  var failedACKReceiveBatchNumbers: Set[Int] = Set.empty
  var batchesConfirmedProcessingAndRunning: Set[Int] = Set.empty

  var failedBatches: Seq[Int] = Seq.empty

  var jobToProcess: ActorRunnableJobGenerator[_, _, _, U] = _
  var jobBatchesIterator: Iterator[ActorRunnable[_, _, _, U]] = _

  val workerService: ActorRef = createWorkerRoutingService

  val maxRetries: Int = 1
  var currentNrRetries: Int = 0

  var scheduleCancellables: Seq[Cancellable] = Seq.empty

  def createWorkerRoutingService: ActorRef = {
    context.actorOf(
      ClusterRouterPool(
        RoundRobinPool(0),
        ClusterRouterPoolSettings(totalInstances = 20, maxInstancesPerNode = 7, allowLocalRoutees = true))
        .props(Props[WorkManagerActor]),
      name = s"jobBatchRouter-$jobId")
  }

  def updateExpectationForMsg(msg: Any): Unit = msg match {
    case e: AggregationState[_] =>
      // if the batchNr is within existing expectations, update the expectations
      executionExpectationMap.get(e.batchNr).foreach(x => {
        x.accept(msg)
      })
      updateExpectations(Seq(e.batchNr))
  }

  def updateExpectations(keys: Seq[Int]): Unit = {
    val batchKeys: Seq[Int] = executionExpectationMap.keys.filter(x => keys.contains(x)).toSeq
    batchKeys.foreach(x => {
      val expectationOpt: Option[ExecutionExpectation] = executionExpectationMap.get(x)
      expectationOpt.foreach(expectation => {
        if (expectation.succeeded) {
          nrOfResultsReceived += 1
          executionExpectationMap -= x
        }
        else if (expectation.failed) {
          failedBatches = failedBatches :+ x
          nrOfResultsReceived += 1
          executionExpectationMap -= x
        }
      })
    })
  }

  def acceptResultMsg(msg: Any): Unit = {
    updateExpectationForMsg(msg)
    if (completed) {
      wrapUp
    }
    else {
      fillUpFreeSlots()
    }
  }

  def fillUpFreeSlots(): Unit = {
    // check if were under the baseline of tasks running at a time, only then add new tasks
    val isIterationFinished = !jobBatchesIterator.hasNext
    if (isIterationFinished && retryNeeded) {
      log.info(s"retrying missing batches: ${failedBatches ++ failedACKReceiveBatchNumbers}")
      jobBatchesIterator = getBatchesToBeRetried
      currentNrRetries += 1
    }
    while (freeSlots() > 0 && jobBatchesIterator.hasNext) {
      submitNextBatch()
    }
  }

  def getBatchesToBeRetried: Iterator[ActorRunnable[_, _, _, U]] = {
    val retryBatches: Set[Int] = Set.empty ++ failedACKReceiveBatchNumbers ++ failedBatches
    val retryRunnables: Set[ActorRunnable[_, _, _, U]] = retryBatches.map(batchNr => jobToProcess.get(batchNr).get)
    log.info(s"batches to be retried: ${retryRunnables.map(x => x.batchNr).toSeq}")
    retryRunnables.iterator
  }

  def retryNeeded: Boolean = {
    (failedACKReceiveBatchNumbers.nonEmpty || failedBatches.nonEmpty) && currentNrRetries < maxRetries
  }

  def completed: Boolean = {
    val allSent: Boolean = nrOfBatchesTotal - nrOfBatchesSent == 0
    val needsRetry = currentNrRetries < maxRetries && retryNeeded
    executionExpectationMap.keys.isEmpty && allSent && !needsRetry
  }

  def resultSummary(result: ProcessingResult.Value): ResultSummary = {
    ResultSummary(
      result = result,
      nrOfBatchesTotal = nrOfBatchesTotal,
      nrOfBatchesSentForProcessing = nrOfBatchesSent,
      nrOfResultsReceived = nrOfResultsReceived,
      leftoverExpectationsMap = Map(executionExpectationMap.toSeq: _*),
      failedBatches = failedBatches
    )
  }

  def wrapUp: Unit = {
    log.info(s"wrapping up execution of jobId '$jobId'")
    writer.write(aggregator.aggregation, StringTag(jobId))
    // cancel set schedules
    scheduleCancellables.foreach(x => x.cancel())
    if (failedBatches.isEmpty) {
      log.info(s"job with jobId '$jobId' finished successfully, sending response to supervisor")
      reportResultsTo ! FinishedJobEvent(jobId, resultSummary(ProcessingResult.SUCCESS))
      context.become(ignoringAll)
      self ! PoisonPill
    }
    else {
      log.info(s"job with jobId '$jobId' failed, sending response to supervisor")
      reportResultsTo ! FinishedJobEvent(jobId, resultSummary(ProcessingResult.FAILURE))
      context.become(ignoringAll)
      self ! PoisonPill
    }
  }

  def freeSlots(): Int = {
    // right now we add the expectation to the map when we send the execution message,
    // thus those waiting for ack and confirmed are both in the current executionExpectation
    val running: Int = executionExpectationMap.keys.size
    continuousRunningTaskCount - running
  }

  def expectationForNextBatch(): ExecutionExpectation = {
    val failExpectations: Seq[Expectation[Any]] = Seq(TimeExpectation(maxBatchDuration))
    BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(
        ClassifyingCountExpectation(Map("finishResponse" -> {
          case _: AggregationState[U] => true
          case _ => false
        }), Map("finishResponse" -> 1))
      ),
      fulfillAnyForFail = failExpectations)
  }

  def checkIfJobAckReceivedAndRemoveIfNot(batchNr: Int): Unit = {
    batchesSentWaitingForACK.find(nr => nr == batchNr).foreach(nr => {
      log.info("batch still waiting for ACK by WorkManager, removing from processed")
      batchesSentWaitingForACK = batchesSentWaitingForACK - nr
      failedACKReceiveBatchNumbers = failedACKReceiveBatchNumbers + nr
      executionExpectationMap -= nr
    })
    fillUpFreeSlots()
  }

  def submitNextBatch(): Unit = {
    val nextBatch: ActorRunnable[_, _, _, U] = jobBatchesIterator.next()
    // the only expectation here is that we get a single AggregationState
    // the expectation of the runnable is actually handled within the runnable
    // allowed time per batch is handled where the actual execution happens,
    // thus we set no TimeExpectation here
    val nextExpectation: ExecutionExpectation = expectationForNextBatch()
    nextExpectation.init
    executionExpectationMap(nextBatch.batchNr) = nextExpectation
    batchesSentWaitingForACK = batchesSentWaitingForACK - nextBatch.batchNr
    nrOfBatchesSent += 1
    workerService ! nextBatch
    // first place the batch in waiting for acknowledgement for processing by worker
    batchesSentWaitingForACK = batchesSentWaitingForACK + nextBatch.batchNr
    val checkAckInTimeRunnable: Runnable = () => checkIfJobAckReceivedAndRemoveIfNot(nextBatch.batchNr)
    context.system.scheduler.scheduleOnce(200 millis, checkAckInTimeRunnable)
  }

  def startState: Receive = {
    case cmd: ProcessJobCmd[_, _, _, U] =>
      log.debug(s"received job to process: $cmd")
      reportResultsTo = sender()
      jobToProcess = cmd.job
      jobBatchesIterator = jobToProcess.iterator
      nrOfBatchesTotal = jobToProcess.nrOfElements
      context.become(processingState)
      // if a maximal process duration is set, schedule message to self to
      // write result and send a fail note, then take PoisonPill
      scheduleCancellables = scheduleCancellables :+ context.system.scheduler.scheduleOnce(maxProcessDuration, self,
        WriteResultAndSendFailNoteAndTakePoisonPillCmd)
      // distribute batches at fixed rate
      val cancellableDistributeBatchSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(0 second,
        config.batchDistributionInterval, self, DistributeBatches)
      scheduleCancellables = scheduleCancellables :+ cancellableDistributeBatchSchedule
      ()
    case a: Any =>
      log.warning(s"received invalid message: $a")
  }

  def processingState: Receive = {
    case e: ACK =>
      log.debug(s"received ACK: $e")
      batchesSentWaitingForACK = batchesSentWaitingForACK - e.batchNr
      batchesConfirmedProcessingAndRunning = batchesConfirmedProcessingAndRunning + e.batchNr
    case ProvideJobStatus =>
      sender() ! JobStatusInfo(jobId = jobId, resultSummary = resultSummary(RUNNING))
    case UpdateStateAndCheckForCompletion =>
      log.debug("received UpdateStateAndCheckForCompletion")
      updateExpectations(executionExpectationMap.keys.toSeq)
      if (completed) {
        log.debug("UpdateStateAndCheckForCompletion: completed")
        wrapUp
      }
    case DistributeBatches =>
      log.debug("received DistributeBatches")
      updateExpectations(executionExpectationMap.keys.toSeq)
      fillUpFreeSlots()
      if (completed) {
        log.debug("UpdateStateAndCheckForCompletion: completed")
        wrapUp
      }
    case ExpectationMet =>
      log.debug("received ExpectationMet, which means we can stop executing")
      self ! PoisonPill
    case ExpectationFailed =>
      log.debug("received ExpectationFailed, which means we can stop executing")
      self ! PoisonPill
    case AddToRunningBaselineCount(count) =>
      log.debug(s"received AddToRunningBaselineCount, count: $count")
      continuousRunningTaskCount += math.max(0, continuousRunningTaskCount + count)
      fillUpFreeSlots()
    case e: AggregationState[U] =>
      val receivedBatchNr: Int = e.batchNr
      if (!executionExpectationMap.contains(receivedBatchNr) && failedACKReceiveBatchNumbers.contains(receivedBatchNr)) {
        jobToProcess.get(receivedBatchNr).foreach(batch => {
          executionExpectationMap(batch.batchNr) = expectationForNextBatch()
        })
      }
      failedACKReceiveBatchNumbers = failedACKReceiveBatchNumbers - e.batchNr
      batchesSentWaitingForACK = batchesSentWaitingForACK - e.batchNr
      batchesConfirmedProcessingAndRunning = batchesConfirmedProcessingAndRunning - e.batchNr
      aggregator.addAggregate(e.data)
      log.info("received aggregation (batch finished) - aggregation: {}, jobId: {}, batchNr: {} ", e.data, e.jobID, e.batchNr)
      acceptResultMsg(e)
    case WriteResultAndSendFailNoteAndTakePoisonPillCmd =>
      log.warning("received WriteResultAndSendFailNoteAndTakePoisonPillCmd")
      writer.write(aggregator.aggregation, StringTag(jobId))
      reportResultsTo ! MaxTimeExceededEvent(jobId)
      context.become(ignoringAll)
      self ! PoisonPill
    // retrieve the currently processing actors and retrieve from all the status
    case GetStatusForWorkers =>
      val reportTo: ActorRef = sender()
      // send GetWorkerStatus messages
      val runningBatches = executionExpectationMap.keys.toSeq
      val messages = runningBatches.map(batchNr => GetWorkerStatus(ExecutionType.RUNNABLE, jobId, batchNr))
      implicit val timeout: Timeout = Timeout(1 second)
      val responseFuture: Future[Seq[Any]] = Future.sequence(messages.map(x => workerService.ask(x)))
      responseFuture.onComplete({
        case Success(value) =>
          reportTo ! WorkerStatusResponse[U](Right(value = value.asInstanceOf[Seq[AggregationState[U]]]))
        case Failure(e) =>
          reportTo ! WorkerStatusResponse[U](Left(e))
      })
    case WorkerKilled(batchNr) =>
      executionExpectationMap -= batchNr
      fillUpFreeSlots()
    case e =>
      log.warning(s"Unknown message '$e', ignoring")
  }

  def ignoringAll: Receive = {
    case _ =>
  }

  override def receive: Receive = startState
}
