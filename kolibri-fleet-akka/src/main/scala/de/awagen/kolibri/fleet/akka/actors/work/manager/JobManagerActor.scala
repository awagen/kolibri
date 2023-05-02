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

package de.awagen.kolibri.fleet.akka.actors.work.manager


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.cluster.ddata.Replicator.UpdateSuccess
import akka.pattern.ask
import akka.util.Timeout
import de.awagen.kolibri.definitions.processing.ProcessingMessages._
import de.awagen.kolibri.definitions.processing.distribution.DistributionStates
import de.awagen.kolibri.definitions.processing.distribution.Distributors.Batch
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.resources.{ResourceAlreadyExists, ResourceOK}
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.DDResourceStateUtils
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ResourceToJobMappingClusterStateManagerActor.{CheckedResourceDirectivesAndReadyForProcessing, ProcessResourceDirectives, ProcessedResourceDirectives, RemoveValueFromAllMappings}
import de.awagen.kolibri.fleet.akka.actors.resources.BatchFreeSlotResourceCheckingActor.AddToRunningBaselineCount
import de.awagen.kolibri.fleet.akka.actors.routing.Routers.createWorkerRoutingServiceForJob
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.{ActorRunnableJobGenerator, FinishedJobEvent}
import de.awagen.kolibri.fleet.akka.actors.work.manager.JobManagerActor._
import de.awagen.kolibri.fleet.akka.actors.work.manager.JobProcessingState.emptyJobStatusInfo
import de.awagen.kolibri.fleet.akka.actors.work.manager.WorkManagerActor.JobBatchMsg
import de.awagen.kolibri.fleet.akka.actors.work.worker.RunnableExecutionActor.BatchProcessStateResult
import de.awagen.kolibri.fleet.akka.cluster.ClusterNode
import de.awagen.kolibri.fleet.akka.config.AppProperties._
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.fleet.akka.jobdefinitions.TestJobDefinitions.MapWithCount
import de.awagen.kolibri.definitions.processing.JobMessages.{SearchEvaluationDefinition, TestPiCalculationDefinition}
import de.awagen.kolibri.fleet.akka.processing.JobMessagesImplicits.{SearchEvaluationImplicits, TestPiCalcToRunnable}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import org.slf4j.{Logger, LoggerFactory}

import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object JobManagerActor {

  val log: Logger = LoggerFactory.getLogger(JobManagerActor.getClass)

  // in case jobId contains a dir delimiter, that conflicts with valid actor paths and thus can
  // not be used as name for an actor. To work around, the slashes are replaced here
  final def name(jobId: String) = s"jobManager-${jobId.replace("/", "_")}"

  def props[T, U <: WithCount](experimentId: String,
                               perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                               perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                               writer: Writer[U, Tag, _],
                               maxProcessDuration: FiniteDuration,
                               maxBatchDuration: FiniteDuration): Props =
    Props(new JobManagerActor[T, U](experimentId, perBatchAggregatorSupplier, perJobAggregatorSupplier, writer, maxProcessDuration, maxBatchDuration))

  def currentTimeZonedInstance(): ZonedDateTime = {
    val instant = Instant.ofEpochMilli(System.currentTimeMillis())
    ZonedDateTime.ofInstant(instant, ZoneId.of("CET"))
  }

  // date format to submit job start times
  val dateFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")

  // cmds telling JobManager to do sth
  sealed trait ExternalJobManagerCmd extends KolibriSerializable

  case class ProcessJobCmd[U, V, V1, W <: WithCount](job: ActorRunnableJobGenerator[U, V, V1, W]) extends ExternalJobManagerCmd

  case object ExpectationMet extends ExternalJobManagerCmd

  case object ExpectationFailed extends ExternalJobManagerCmd

  private sealed trait InternalJobManagerCmd extends KolibriSerializable

  private case object WriteResultAndSendFailNoteAndTakePoisonPillCmd extends InternalJobManagerCmd

  private case object DistributeBatches extends InternalJobManagerCmd

  private case class CheckIfJobAckReceivedAndRemoveIfNot(batchId: Int) extends InternalJobManagerCmd

  private case object UpdateStateAndCheckForCompletion extends InternalJobManagerCmd

  // cmds providing some event info to JobManagerActor (e.g in case some result finished computing))
  sealed trait JobManagerEvent extends KolibriSerializable

  case class ACK(jobId: String, batchNr: Int, sender: ActorRef) extends JobManagerEvent

  case class MaxTimeExceededEvent(jobId: String) extends JobManagerEvent

  case class WorkerKilled(batchNr: Int) extends JobManagerEvent

}


class JobManagerActor[T, U <: WithCount](val jobId: String,
                                         val perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                         val perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                         val writer: Writer[U, Tag, _],
                                         val maxProcessDuration: FiniteDuration,
                                         val maxBatchDuration: FiniteDuration) extends Actor with ActorLogging with KolibriSerializable {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  val SEARCH_EVALUATION_JOB_NAME = "SearchEvaluation"
  val TEST_PI_CALCULATION_JOB_NAME = "TestPiCalculation"

  // processing state keeping track of received results and distribution of new batches
  val jobProcessingState: JobProcessingState[U] = JobProcessingState[U](jobId)
  var runningBatchesState: Map[Int, BatchProcessStateResult] = Map.empty

  // if set to true, job manager will expect batches to send back actual results, otherwise will not look at returned data in messages but only at success criteria
  // e.g makes sense in case sending results back is not needed since each batch corresponds to an result by itself
  var expectResultsFromBatchCalculations: Boolean = true

  // actor to which to send notification of completed processing
  var reportResultsTo: ActorRef = _

  var wrapUpFunction: Option[Execution[Any]] = None

  val workerServiceRouter: ActorRef = createWorkerRoutingServiceForJob(JobManagerActor.name(jobId))

  var scheduleCancellables: Seq[Cancellable] = Seq.empty
  // schedule sending regular status updates to local state distributor which takes care
  // of distributing the updates to right actor in cluster
  val jobStatusUpdateCancellable: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = FiniteDuration(config.batchStateUpdateInitialDelayInSeconds, SECONDS),
    interval = FiniteDuration(config.batchStateUpdateIntervalInSeconds, SECONDS))(() => {
    if (!jobProcessingState.isJobToProcessSet) {
      ClusterNode.getSystemSetup.localStateDistributorActor ! emptyJobStatusInfo
    }
    else {
      ClusterNode.getSystemSetup.localStateDistributorActor ! jobProcessingState.jobStatusInfo(ProcessingResult.RUNNING)
    }
  })

  val checkForCompletionCancellable: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = FiniteDuration(30, SECONDS),
    interval = FiniteDuration(30, SECONDS))(() => {
      checkForCompletionAndWrapUpIfDone()
    })

  def checkForCompletionAndWrapUpIfDone(): Boolean = {
    log.debug(s"execution expectation for job '$jobId': ${jobProcessingState.expectationState}")
    log.debug(s"batchDistributorHasCompleted: ${jobProcessingState.batchDistributorHasCompleted}")
    log.debug(s"executionExpectationMapIsEmpty: ${jobProcessingState.executionExpectationMapIsEmpty}")
    if (jobProcessingState.completed) {
      wrapUp()
      true
    }
    else false
  }

  def acceptResultMsg(msg: AggregationState[U]): Unit = {
    log.debug(s"received aggregation state: $msg")
    // NOTE: the accept call does seem to always update the state immediately, which could lead to the doneAndWrappedUp
    // call to not yet opt for the wrap-up. Thus find above additionally a schedule calling the
    // checkForCompletionAndWrapUpIfDone in intervals irrespective of receiving any aggregation state
    jobProcessingState.accept(msg)
    log.info(s"msg receive: expectationState: ${jobProcessingState.expectationState}")
    log.info(s"msg receive: batchDistributorHasCompleted: ${jobProcessingState.batchDistributorHasCompleted}")
    log.info(s"msg receive: executionExpectationMapIsEmpty: ${jobProcessingState.executionExpectationMapIsEmpty}")
    val doneAndWrappedUp: Boolean = checkForCompletionAndWrapUpIfDone()
    if (!doneAndWrappedUp) fillUpFreeSlots()
  }

  def fillUpFreeSlots(): Unit = {
    val nextBatchesOrState: Either[DistributionStates.DistributionState, Seq[Batch]] = jobProcessingState.nextBatchesOrState
    nextBatchesOrState match {
      case Left(state) =>
        log.debug(s"state received from distributor: $state")
        ()
      case Right(batches) =>
        log.debug(s"batches received from distributor (size: ${batches.size}): ${batches.map(x => x.batchNr)}")
        submitNextBatches(batches)
    }
  }

  // stop schedules
  override def postStop(): Unit = {
    scheduleCancellables.foreach(x => x.cancel())
    jobStatusUpdateCancellable.cancel()
    checkForCompletionCancellable.cancel()
    // tell local resource manager that global resource data needs an update
    DDResourceStateUtils.DD_RESOURCETYPE_TO_KEY_MAPPING.values.foreach(value => {
      ClusterNode.getSystemSetup.localResourceManagerActor ! RemoveValueFromAllMappings(value, jobId)
    })
    super.postStop()
  }

  def wrapUp(): Unit = {
    wrapUpFunction.foreach(x => x.execute match {
      case Left(e) => log.info(s"wrap up function execution failed, result: $e")
      case Right(e) => log.info(s"wrap up function execution succeeded, result: $e")
    })
    jobProcessingState.wrapUp()
    // cancel set schedules
    scheduleCancellables.foreach(x => x.cancel())
    if (jobProcessingState.expectationSucceeded) {
      log.info(s"job with jobId '$jobId' finished successfully, sending response to supervisor")
      reportResultsTo ! FinishedJobEvent(jobId, jobProcessingState.jobStatusInfo(ProcessingResult.SUCCESS))
      context.become(ignoringAll)
      self ! PoisonPill
    }
    else {
      log.info(s"job with jobId '$jobId' failed, sending response to supervisor")
      reportResultsTo ! FinishedJobEvent(jobId, jobProcessingState.jobStatusInfo(ProcessingResult.FAILURE))
      context.become(ignoringAll)
      self ! PoisonPill
    }
  }

  def checkIfJobAckReceivedAndRemoveIfNot(batchNr: Int): Unit = {
    jobProcessingState.checkIfJobAckReceivedAndRemoveIfNot(batchNr)
    fillUpFreeSlots()
  }

  def submitNextBatches(batches: Seq[Batch]): Unit = {
    batches.foreach(batch => {
      jobProcessingState.addExpectationForBatch(batch.batchNr, maxBatchDuration, expectResultsFromBatchCalculations)
      workerServiceRouter ! batch
      jobProcessingState.addBatchWaitingForACK(batch.batchNr)
      context.system.scheduler.scheduleOnce(config.batchMaxTimeToACKInMs, self, CheckIfJobAckReceivedAndRemoveIfNot(batch.batchNr))
    })

  }

  def handleProcessJobCmd(cmd: ProcessJobCmd[_, _, _, U], jobTypeName: String): Unit = {
    log.debug(s"received job to process: $cmd")
    reportResultsTo = sender()

    // initialize the job to process
    jobProcessingState.initJobToProcess[T](
      jobId = jobId,
      jobTypeName = jobTypeName,
      job = cmd.job,
      perJobAggregatorSupplier.apply(),
      writer,
      config.runningTasksPerJobDefaultCount,
      maxProcessDuration,
      expectResultsFromBatchCalculations)

    switchToProcessingStateAndSetScheduler()
    log.info(s"started processing of job '$jobId'")
    ()
  }

  def switchToProcessingStateAndSetScheduler(): Unit = {
    context.become(processingState)
    scheduleCancellables = scheduleCancellables :+ context.system.scheduler.scheduleOnce(maxProcessDuration, self,
      WriteResultAndSendFailNoteAndTakePoisonPillCmd)
    val cancellableDistributeBatchSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(0 second,
      config.batchDistributionInterval, self, DistributeBatches)
    scheduleCancellables = scheduleCancellables :+ cancellableDistributeBatchSchedule
  }

  def startState: Receive = {
    case UpdateSuccess(key, _) =>
      log.info(s"successful distributed data update for key: $key")
    case testJobMsg: TestPiCalculationDefinition =>
      log.debug(s"received job to process: $testJobMsg")
      reportResultsTo = sender()
      expectResultsFromBatchCalculations = true
      val jobMsg: SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]] = testJobMsg.toRunnable
      val numberBatches: Int = jobMsg.processElements.size
      val jobToProcess = ByFunctionNrLimitedIndexedGenerator(numberBatches, batchNr => Some(JobBatchMsg(jobMsg.jobId, batchNr, testJobMsg)))
      jobProcessingState.initJobToProcess[T](
        jobId = jobId,
        jobTypeName = TEST_PI_CALCULATION_JOB_NAME,
        job = jobToProcess,
        perJobAggregatorSupplier.apply(),
        writer,
        testJobMsg.requestTasks,
        maxProcessDuration,
        expectResultsFromBatchCalculations)
      log.debug(s"job contains ${jobToProcess.size} batches")
      switchToProcessingStateAndSetScheduler()
      log.info(s"started processing of job '$jobId'")
      ()
    case searchJobMsg: SearchEvaluationDefinition =>
      log.info(s"received job to process: $searchJobMsg")
      val jobSender: ActorRef = sender()
      reportResultsTo = jobSender
      // register needed resources and for directives in distributed data
      (searchJobMsg.resources ++ searchJobMsg.resourceDirectives.map(x => x.resource))
        .foreach(resource => {
          DDResourceStateUtils.ddResourceJobMappingUpdateAdd(
            ClusterNode.getSystemSetup.ddSelfUniqueAddress,
            resource,
            jobId
          ).foreach(msg => ClusterNode.getSystemSetup.ddReplicator ! msg)
        })
      // send request to initialize the per-node resource directives
      val directives = searchJobMsg.resourceDirectives
      log.info(s"processing resource directives for job '${searchJobMsg.jobName}', this might take a few moments")
      implicit val timeout: Timeout = FiniteDuration(config.maxResourceDirectiveLoadTimeInMinutes, MINUTES)
      val resultFuture: Future[Any] = ClusterNode.getSystemSetup.localResourceManagerActor ? ProcessResourceDirectives(directives, searchJobMsg.jobName)
      resultFuture.onComplete({
        case Success(value) =>
          val processedDirectiveMsg = value.asInstanceOf[ProcessedResourceDirectives]
          log.info(s"resource directive processing results: ${processedDirectiveMsg.states}")
          val mustStopExecution: Boolean = processedDirectiveMsg.states.exists(state => !Seq(ResourceOK, ResourceAlreadyExists).contains(state))
          if (mustStopExecution) {
            log.warning(s"could not process all resource directives, stopping execution of job ${searchJobMsg.jobName}: ${processedDirectiveMsg.states}")
          }
          else {
            self.tell(CheckedResourceDirectivesAndReadyForProcessing(searchJobMsg), jobSender)
          }
        case Failure(exception) =>
          log.error(s"processing resource directives for job '${searchJobMsg.jobName}' failed, stopping execution", exception)
      })
    case CheckedResourceDirectivesAndReadyForProcessing(searchJobMsg: SearchEvaluationDefinition) =>
      wrapUpFunction = searchJobMsg.wrapUpFunction
      val jobMsg: SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = searchJobMsg.toRunnable
      val jobToProcess = ByFunctionNrLimitedIndexedGenerator(
        jobMsg.processElements.size,
        batchNr => Some(
          JobBatchMsg(
            jobMsg.jobId,
            batchNr,
            searchJobMsg)
        )
      )
      expectResultsFromBatchCalculations = searchJobMsg.expectResultsFromBatchCalculations
      log.info(s"expectResultsFromBatchCalculations: $expectResultsFromBatchCalculations")
      // TODO: this is same as handled in handleProcessJobCmd, unify
      jobProcessingState.initJobToProcess[T](
        jobId = jobId,
        jobTypeName = SEARCH_EVALUATION_JOB_NAME,
        job = jobToProcess,
        perJobAggregatorSupplier.apply(),
        writer,
        searchJobMsg.requestTasks,
        maxProcessDuration,
        expectResultsFromBatchCalculations)
      switchToProcessingStateAndSetScheduler()
      log.info(s"started processing of job '$jobId'")
      ()
    case cmd: ProcessJobCmd[_, _, _, U] =>
      handleProcessJobCmd(cmd, "GeneralCommand")
    case a: Any =>
      log.warning(s"received invalid message: $a")
  }

  def processingState: Receive = {
    case e: ACK =>
      log.debug(s"received ACK: $e")
      jobProcessingState.removeBatchWaitingForACK(e.batchNr)
    case UpdateSuccess(key, _) =>
      log.info(s"successful distributed data update for key: $key")
    case CheckIfJobAckReceivedAndRemoveIfNot(batchNr) =>
      checkIfJobAckReceivedAndRemoveIfNot(batchNr)
    case UpdateStateAndCheckForCompletion =>
      log.debug("received UpdateStateAndCheckForCompletion")
      jobProcessingState.updateSingleBatchExpectations()
      if (jobProcessingState.completed) {
        log.debug("UpdateStateAndCheckForCompletion: completed")
        wrapUp()
      }
    case DistributeBatches =>
      log.debug("received DistributeBatches")
      jobProcessingState.updateSingleBatchExpectations()
      fillUpFreeSlots()
      if (jobProcessingState.completed) {
        log.debug("UpdateStateAndCheckForCompletion: completed")
        wrapUp()
      }
    case ExpectationMet =>
      log.debug("received ExpectationMet, which means we can stop executing")
      self ! PoisonPill
    case ExpectationFailed =>
      log.debug("received ExpectationFailed, which means we can stop executing")
      self ! PoisonPill
    case AddToRunningBaselineCount(count) =>
      log.debug(s"received AddToRunningBaselineCount, count: $count")
      jobProcessingState.addToRunningTaskCount(count)
      fillUpFreeSlots()
    case e: AggregationState[U] =>
      jobProcessingState.removeBatchWaitingForACK(e.batchNr)
      runningBatchesState -= e.batchNr
      log.debug("received aggregation (batch finished) - jobId: {}, batchNr: {} ", e.jobID, e.batchNr)
      acceptResultMsg(e)
      if (jobProcessingState.nrResultsAccepted % 10 == 0 ||
        jobProcessingState.nrResultsAccepted == jobProcessingState.numBatches) {
        log.info(s"received nr of results: ${jobProcessingState.nrResultsAccepted}")
      }
    case WriteResultAndSendFailNoteAndTakePoisonPillCmd =>
      log.warning("received WriteResultAndSendFailNoteAndTakePoisonPillCmd")
      reportResultsTo ! MaxTimeExceededEvent(jobId)
      context.become(ignoringAll)
      self ! PoisonPill
    // react to status updates retrieved by the workers
    case result: BatchProcessStateResult =>
      log.debug(s"received update on batch state: $result")
      runningBatchesState = runningBatchesState + (result.batchNr -> result)
    case WorkerKilled(batchNr) =>
      jobProcessingState.removeExpectationForBatchId(batchNr)
      runningBatchesState -= batchNr
      fillUpFreeSlots()
    case e =>
      log.warning(s"Unknown message '$e', ignoring")
  }

  def ignoringAll: Receive = {
    case _ =>
  }

  override def receive: Receive = startState
}
