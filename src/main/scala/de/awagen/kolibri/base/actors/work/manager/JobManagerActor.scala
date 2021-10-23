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


import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import de.awagen.kolibri.base.actors.resources.BatchFreeSlotResourceCheckingActor.AddToRunningBaselineCount
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{ActorRunnableJobGenerator, FinishedJobEvent, ProcessingResult}
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor._
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor.{ExecutionType, GetWorkerStatus, JobBatchMsg}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, _}
import de.awagen.kolibri.base.config.AppProperties._
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions.MapWithCount
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluation, TestPiCalculation}
import de.awagen.kolibri.base.processing.JobMessagesImplicits._
import de.awagen.kolibri.base.processing.consume.Consumers.{BaseExecutionConsumer, ExecutionConsumer}
import de.awagen.kolibri.base.processing.distribution.{DistributionStates, Distributor, RetryingDistributor}
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import org.slf4j.{Logger, LoggerFactory}

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.Objects
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object JobManagerActor {

  val log: Logger = LoggerFactory.getLogger(JobManagerActor.getClass)

  final def name(jobId: String) = s"jobManager-$jobId"

  type BatchType = Any with WithBatchNr

  def props[T, U <: WithCount](experimentId: String,
                               perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                               perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                               writer: Writer[U, Tag, _],
                               maxProcessDuration: FiniteDuration,
                               maxBatchDuration: FiniteDuration): Props =
    Props(new JobManagerActor[T, U](experimentId, perBatchAggregatorSupplier, perJobAggregatorSupplier, writer, maxProcessDuration, maxBatchDuration))

  // NOTE: do not require the routees to have any props arguments.
  // this can lead to failed serialization on routee creation
  // (even if its just a case class with primitives, maybe due to disabled
  // java serialization and respective msg not being of KolibriSerializable.
  // Not fully clear yet though
  def createWorkerRoutingServiceForJob(jobId: String)(implicit context: ActorContext): ActorRef = {
    context.actorOf(
      ClusterRouterPool(
        RoundRobinPool(0),
        // right now we want to have a single WorkManager per node. Just distributes the batches
        // to workers on its respective node and keeps state of running workers
        ClusterRouterPoolSettings(
          totalInstances = 10,
          maxInstancesPerNode = 2,
          allowLocalRoutees = true,
          useRoles = Set("compute")))
        .props(WorkManagerActor.props),
      name = s"jobBatchRouter-$jobId")
  }

  def currentTimeZonedInstance(): ZonedDateTime = {
    val instant = Instant.ofEpochMilli(System.currentTimeMillis())
    ZonedDateTime.ofInstant(instant, ZoneId.of("CET"))
  }

  def unknownJobResultSummary: ResultSummary = {
    ResultSummary(
      result = ProcessingResult.UNKNOWN,
      nrOfBatchesTotal = 0,
      nrOfBatchesSentForProcessing = 0,
      nrOfResultsReceived = 0,
      failedBatches = Seq.empty
    )
  }

  /**
    * Consumer of incoming results, taking care of updating expectations, aggregations and result writing
    * on an overall job level
    *
    * @param executionExpectation
    * @return
    */
  def getExecutionConsumer[T, U](jobId: String, executionExpectation: ExecutionExpectation,
                                 aggregator: Aggregator[ProcessingMessage[T], U],
                                 writer: Writer[U, Tag, _]): ExecutionConsumer[U] = {
    BaseExecutionConsumer(
      jobId = jobId,
      expectation = executionExpectation,
      aggregator = aggregator,
      writer = writer
    )
  }

  /**
    * Distributor distributing the single batches on nodes. Needs updates on batches considered to be failed
    * (e.g due to failing ACK or by failing the single batch expectation,...). Allows retry on failed batches.
    *
    * @param dataGen
    * @return
    */
  def getDistributor[U](dataGen: IndexedGenerator[BatchType], numRetries: Int, maxParallelTasks: Int): Distributor[BatchType, U] = {
    new RetryingDistributor[BatchType, U](
      maxParallel = maxParallelTasks,
      generator = dataGen,
      maxNrRetries = numRetries)
  }

  def jobExpectation(numberBatches: Int, maxProcessDuration: FiniteDuration, expectResultsFromBatchCalculations: Boolean): ExecutionExpectation = {
    val failExpectations: Seq[Expectation[Any]] = Seq(TimeExpectation(maxProcessDuration))
    BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(
        ClassifyingCountExpectation(Map("finishResponse" -> {
          case _: AggregationStateWithData[_] =>
            if (!expectResultsFromBatchCalculations) {
              log.warn(s"received AggregationState with data but expectResultsFromBatchCalculations=$expectResultsFromBatchCalculations")
            }
            true
          case _: AggregationStateWithoutData[_] =>
            if (expectResultsFromBatchCalculations) {
              log.warn(s"received AggregationState without data but expectResultsFromBatchCalculations=$expectResultsFromBatchCalculations")
              false
            }
            else true
          case _ => false
        }), Map("finishResponse" -> numberBatches))
      ),
      fulfillAnyForFail = failExpectations)
  }

  // date format to submit job start times
  val dateFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")

  // cmds telling JobManager to do sth
  sealed trait ExternalJobManagerCmd extends KolibriSerializable

  case class ProcessJobCmd[U, V, V1, W <: WithCount](job: ActorRunnableJobGenerator[U, V, V1, W]) extends ExternalJobManagerCmd

  case object ProvideJobStatus extends ExternalJobManagerCmd

  case object ExpectationMet extends ExternalJobManagerCmd

  case object ExpectationFailed extends ExternalJobManagerCmd

  private sealed trait InternalJobManagerCmd extends KolibriSerializable

  private case object WriteResultAndSendFailNoteAndTakePoisonPillCmd extends InternalJobManagerCmd

  private case object DistributeBatches extends InternalJobManagerCmd

  private case class CheckIfJobAckReceivedAndRemoveIfNot(batchId: Int) extends InternalJobManagerCmd

  private case object UpdateStateAndCheckForCompletion extends InternalJobManagerCmd

  // cmds providing some event info to JobManagerActor (e.g in case some result finished computing))
  sealed trait JobManagerEvent extends KolibriSerializable

  case class JobStatusInfo(jobId: String,
                           jobType: String,
                           startTime: String,
                           endTime: Option[String],
                           resultSummary: ResultSummary) extends JobManagerEvent

  case class ACK(jobId: String, batchNr: Int, sender: ActorRef) extends JobManagerEvent

  case class MaxTimeExceededEvent(jobId: String) extends JobManagerEvent

  case object GetStatusForWorkers extends ExternalJobManagerCmd

  case class WorkerStatusResponse[U](result: Either[Throwable, Seq[AggregationState[U]]]) extends JobManagerEvent

  case class WorkerKilled(batchNr: Int) extends JobManagerEvent

  case class JobManagerState[U](jobId: String) {
    var batchDistributor: Distributor[BatchType, U] = _
    var executionConsumer: ExecutionConsumer[U] = _

    // execution expectation
    val executionExpectationMap: mutable.Map[Int, ExecutionExpectation] = mutable.Map.empty
    // for those batches we havent yet received a confirmation that it is processed,
    // and timeout for wait has not yet passed after which itll be moved to failedACKReceiveBatchNumbers
    var batchesSentWaitingForACK: Set[Int] = Set.empty
    var failedACKReceiveBatchNumbers: Set[Int] = Set.empty
    // the batches to be distributed to the workers
    var jobToProcess: IndexedGenerator[BatchType] = _
    var executionStartZonedDateTime: ZonedDateTime = _
    var executionEndZonedDateTime: ZonedDateTime = _
    var usedTasksCount: Int = config.runningTasksPerJobDefaultCount
    var jobType: String = _

    def initJobToProcess[T](jobId: String,
                            jobTypeName: String,
                            job: IndexedGenerator[BatchType],
                            aggregator: Aggregator[ProcessingMessage[T], U],
                            writer: Writer[U, Tag, _],
                            requestedParallelTasks: Int,
                            maxProcessDuration: FiniteDuration,
                            expectResultsFromBatchCalculations: Boolean): Unit = {
      jobType = jobTypeName
      jobToProcess = job
      val parallelTasks = math.min(requestedParallelTasks, config.runningTasksPerJobMaxCount)
      executionStartZonedDateTime = currentTimeZonedInstance()
      executionConsumer = getExecutionConsumer(jobId, jobExpectation(job.size, maxProcessDuration, expectResultsFromBatchCalculations), aggregator, writer)
      batchDistributor = getDistributor(jobToProcess, config.maxNrBatchRetries, parallelTasks)
      executionStartZonedDateTime = currentTimeZonedInstance()
    }

    def runningBatches: Seq[Int] = executionExpectationMap.keys.toSeq

    def addToRunningTaskCount(addCount: Int): Unit = {
      batchDistributor.setMaxParallelCount(math.min(config.runningTasksPerJobMaxCount, batchDistributor.maxInParallel + addCount))
    }

    def removeExpectationForBatchId(batchId: Int): Unit = {
      executionExpectationMap -= batchId
    }

    def accept(aggregationState: AggregationState[U]) {
      executionConsumer.applyFunc.apply(aggregationState)
      batchDistributor.accept(aggregationState)
    }

    def checkIfJobAckReceivedAndRemoveIfNot(batchNr: Int): Unit = {
      batchesSentWaitingForACK.find(nr => nr == batchNr).foreach(nr => {
        log.warn("batch still waiting for ACK by WorkManager, removing from processed")
        batchesSentWaitingForACK = batchesSentWaitingForACK - nr
        failedACKReceiveBatchNumbers = failedACKReceiveBatchNumbers + nr
        executionExpectationMap -= nr
        // also update the distributor that batch is considered failed
        batchDistributor.markAsFail(nr)
      })
    }

    def expectationSucceeded: Boolean = executionConsumer.expectation.succeeded

    def wrapUp(): Unit = {
      executionEndZonedDateTime = currentTimeZonedInstance()
      log.info(s"wrapping up execution of jobId '$jobId'")
      log.info(s"distributed batches: ${batchDistributor.nrDistributed}, " +
        s"received results: ${batchDistributor.nrResultsAccepted}, " +
        s"failed results: ${batchDistributor.idsFailed.size}, " +
        s"in progress: ${batchDistributor.idsInProgress}")
      executionConsumer.wrapUp
    }

    def resultSummary(result: ProcessingResult.Value): ResultSummary = {
      ResultSummary(
        result = result,
        nrOfBatchesTotal = jobToProcess.size,
        nrOfBatchesSentForProcessing = batchDistributor.nrDistributed,
        nrOfResultsReceived = batchDistributor.nrResultsAccepted,
        failedBatches = batchDistributor.idsFailed
      )
    }

    def completed: Boolean = batchDistributor.hasCompleted && executionExpectationMap.keys.isEmpty

    def nextBatchesOrState: Either[DistributionStates.DistributionState, Seq[BatchType]] = batchDistributor.next

    def updateSingleBatchExpectations(): Unit = {
      val batchKeys: Seq[Int] = executionExpectationMap.keys.toSeq
      batchKeys.foreach(x => {
        val expectationOpt: Option[ExecutionExpectation] = executionExpectationMap.get(x)
        expectationOpt.foreach(expectation => {
          if (expectation.succeeded) {
            executionExpectationMap -= x
          }
          else if (expectation.failed) {
            batchDistributor.markAsFail(x)
            executionExpectationMap -= x
          }
        })
      })
    }

    def addExpectationForBatch(batchNr: Int, maxBatchDuration: FiniteDuration, expectResultsFromBatchCalculations: Boolean): Unit = {
      val expectation = expectationForNextBatch(maxBatchDuration, expectResultsFromBatchCalculations)
      expectation.init
      executionExpectationMap(batchNr) = expectation
    }

    def addBatchWaitingForACK(batchNr: Int): Unit = {
      // first place the batch in waiting for acknowledgement for processing by worker
      batchesSentWaitingForACK = batchesSentWaitingForACK + batchNr
    }

    def addBatchFailedACK(batchNr: Int): Unit = {
      failedACKReceiveBatchNumbers = failedACKReceiveBatchNumbers + batchNr
    }

    def removeBatchWaitingForACK(batchNr: Int): Unit = {
      batchesSentWaitingForACK = batchesSentWaitingForACK - batchNr
    }

    def expectationForNextBatch(maxBatchDuration: FiniteDuration, expectResultsFromBatchCalculations: Boolean): ExecutionExpectation = {
      // the only expectation here is that we get a single AggregationState
      // the expectation of the runnable is actually handled within the runnable
      // allowed time per batch is handled where the actual execution happens,
      // thus we set no TimeExpectation here
      val failExpectations: Seq[Expectation[Any]] = Seq(TimeExpectation(maxBatchDuration))
      BaseExecutionExpectation(
        fulfillAllForSuccess = Seq(
          ClassifyingCountExpectation(Map("finishResponse" -> {
            case _: AggregationStateWithData[_] =>
              if (!expectResultsFromBatchCalculations) {
                log.warn(s"received AggregationState with data but expectResultsFromBatchCalculations=$expectResultsFromBatchCalculations")
              }
              true
            case _: AggregationStateWithoutData[_] =>
              if (expectResultsFromBatchCalculations) {
                log.warn(s"received AggregationState without data but expectResultsFromBatchCalculations=$expectResultsFromBatchCalculations")
                false
              }
              else true
            case _ => false
          }), Map("finishResponse" -> 1))
        ),
        fulfillAnyForFail = failExpectations)
    }

    def jobStatusInfo(state: ProcessingResult.Value): JobStatusInfo = JobStatusInfo(
      jobId = jobId,
      jobType = jobType,
      startTime = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(executionStartZonedDateTime),
      endTime = Option.apply(executionEndZonedDateTime).map(x => DateTimeFormatter.ISO_ZONED_DATE_TIME.format(x)),
      resultSummary = resultSummary(state))
  }

}


class JobManagerActor[T, U <: WithCount](val jobId: String,
                                         val perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                         val perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                         val writer: Writer[U, Tag, _],
                                         val maxProcessDuration: FiniteDuration,
                                         val maxBatchDuration: FiniteDuration) extends Actor with ActorLogging with KolibriSerializable {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  // TODO: need to initialize consumer and distributor
  val jobManagerState: JobManagerState[U] = JobManagerState[U](jobId)

  // if set to true, job manager will expect batches to send back actual results, otherwise will not look at returned data in messages but only at success criteria
  // e.g makes sense in case sending results back is not needed since each batch corresponds to an result by itself
  var expectResultsFromBatchCalculations: Boolean = true

  // actor to which to send notification of completed processing
  var reportResultsTo: ActorRef = _

  var wrapUpFunction: Option[Execution[Any]] = None

  val workerServiceRouter: ActorRef = createWorkerRoutingServiceForJob(jobId)

  var scheduleCancellables: Seq[Cancellable] = Seq.empty

  def acceptResultMsg(msg: AggregationState[U]): Unit = {
    logger.debug(s"received aggregation state: $msg")
    jobManagerState.accept(msg)
    if (jobManagerState.completed) {
      wrapUp
    }
    else {
      fillUpFreeSlots()
    }
  }

  def fillUpFreeSlots(): Unit = {
    val nextBatchesOrState: Either[DistributionStates.DistributionState, Seq[BatchType]] = jobManagerState.nextBatchesOrState
    nextBatchesOrState match {
      case Left(state) =>
        log.debug(s"state received from distributor: $state")
        ()
      case Right(batches) =>
        log.debug(s"batches received from distributor (size: ${batches.size}): ${batches.map(x => x.batchNr)}")
        submitNextBatches(batches)
    }
  }

  def wrapUp: Unit = {
    wrapUpFunction.foreach(x => x.execute match {
      case Left(e) => log.info(s"wrap up function execution failed, result: $e")
      case Right(e) => log.info(s"wrap up function execution succeeded, result: $e")
    })
    jobManagerState.wrapUp()
    // cancel set schedules
    scheduleCancellables.foreach(x => x.cancel())
    if (jobManagerState.expectationSucceeded) {
      log.info(s"job with jobId '$jobId' finished successfully, sending response to supervisor")
      reportResultsTo ! FinishedJobEvent(jobId, jobManagerState.jobStatusInfo(ProcessingResult.SUCCESS))
      context.become(ignoringAll)
      self ! PoisonPill
    }
    else {
      log.info(s"job with jobId '$jobId' failed, sending response to supervisor")
      reportResultsTo ! FinishedJobEvent(jobId, jobManagerState.jobStatusInfo(ProcessingResult.FAILURE))
      context.become(ignoringAll)
      self ! PoisonPill
    }
  }

  def checkIfJobAckReceivedAndRemoveIfNot(batchNr: Int): Unit = {
    jobManagerState.checkIfJobAckReceivedAndRemoveIfNot(batchNr)
    fillUpFreeSlots()
  }

  def submitNextBatches(batches: Seq[BatchType]): Unit = {
    batches.foreach(batch => {
      jobManagerState.addExpectationForBatch(batch.batchNr, maxBatchDuration, expectResultsFromBatchCalculations)
      workerServiceRouter ! batch
      jobManagerState.addBatchWaitingForACK(batch.batchNr)
      context.system.scheduler.scheduleOnce(config.batchMaxTimeToACKInMs, self, CheckIfJobAckReceivedAndRemoveIfNot(batch.batchNr))
    })

  }

  def handleProcessJobCmd(cmd: ProcessJobCmd[_, _, _, U], jobTypeName: String): Unit = {
    log.debug(s"received job to process: $cmd")
    log.debug(s"job contains ${cmd.job.size} batches")
    reportResultsTo = sender()

    // initialize the job to process
    jobManagerState.initJobToProcess[T](
      jobId = jobId,
      jobTypeName = jobTypeName,
      job = cmd.job,
      perJobAggregatorSupplier.apply(),
      writer,
      // TODO: might wanna add some task request count to ProcessJobCmd
      config.runningTasksPerJobDefaultCount,
      maxProcessDuration,
      expectResultsFromBatchCalculations)

    switchToProcessingStateAndSetScheduler()
    logger.info(s"started processing of job '$jobId'")
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
    case testJobMsg: TestPiCalculation =>
      log.debug(s"received job to process: $testJobMsg")
      reportResultsTo = sender()
      expectResultsFromBatchCalculations = true
      val jobMsg: SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]] = testJobMsg.toRunnable
      val numberBatches: Int = jobMsg.processElements.size
      val jobToProcess = ByFunctionNrLimitedIndexedGenerator(numberBatches, batchNr => Some(JobBatchMsg(jobMsg.jobId, batchNr, testJobMsg)))
      jobManagerState.initJobToProcess[T](
        jobId = jobId,
        jobTypeName = "TestPiCalculation",
        job = jobToProcess,
        perJobAggregatorSupplier.apply(),
        writer,
        testJobMsg.requestTasks,
        maxProcessDuration,
        expectResultsFromBatchCalculations)
      log.debug(s"job contains ${jobToProcess.size} batches")
      switchToProcessingStateAndSetScheduler()
      logger.info(s"started processing of job '$jobId'")
      ()
    case searchJobMsg: SearchEvaluation =>
      log.debug(s"received job to process: $searchJobMsg")
      wrapUpFunction = searchJobMsg.wrapUpFunction
      implicit val timeout: Timeout = Timeout(10 minutes)
      reportResultsTo = sender()
      expectResultsFromBatchCalculations = searchJobMsg.expectResultsFromBatchCalculations
      log.info(s"expectResultsFromBatchCalculations: $expectResultsFromBatchCalculations")
      val jobMsg: SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = searchJobMsg.toRunnable

      val jobToProcess = ByFunctionNrLimitedIndexedGenerator(jobMsg.processElements.size, batchNr => Some(JobBatchMsg(jobMsg.jobId, batchNr, searchJobMsg)))
      jobManagerState.initJobToProcess[T](
        jobId = jobId,
        jobTypeName = "SearchEvaluation",
        job = jobToProcess,
        perJobAggregatorSupplier.apply(),
        writer,
        searchJobMsg.requestTasks,
        maxProcessDuration,
        expectResultsFromBatchCalculations)

      switchToProcessingStateAndSetScheduler()
      logger.info(s"started processing of job '$jobId'")
      ()
    case cmd: ProcessJobCmd[_, _, _, U] =>
      handleProcessJobCmd(cmd, "GeneralCommand")
    case a: Any =>
      log.warning(s"received invalid message: $a")
  }

  def emptyJobStatusInfo: JobStatusInfo = JobStatusInfo(
    jobId = "", jobType = "", startTime = "", endTime = Some(""), resultSummary = unknownJobResultSummary
  )

  def processingState: Receive = {
    case e: ACK =>
      log.debug(s"received ACK: $e")
      jobManagerState.removeBatchWaitingForACK(e.batchNr)
    case CheckIfJobAckReceivedAndRemoveIfNot(batchNr) =>
      checkIfJobAckReceivedAndRemoveIfNot(batchNr)
    case ProvideJobStatus =>
      if (Objects.isNull(jobManagerState.jobToProcess)) {
        sender() ! emptyJobStatusInfo
      }
      else {
        sender() ! jobManagerState.jobStatusInfo(ProcessingResult.RUNNING)
      }
    case UpdateStateAndCheckForCompletion =>
      log.debug("received UpdateStateAndCheckForCompletion")
      jobManagerState.updateSingleBatchExpectations()
      if (jobManagerState.completed) {
        log.debug("UpdateStateAndCheckForCompletion: completed")
        wrapUp
      }
    case DistributeBatches =>
      log.debug("received DistributeBatches")
      jobManagerState.updateSingleBatchExpectations()
      fillUpFreeSlots()
      if (jobManagerState.completed) {
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
      jobManagerState.addToRunningTaskCount(count)
      fillUpFreeSlots()
    case e: AggregationState[U] =>
      jobManagerState.addBatchFailedACK(e.batchNr)
      jobManagerState.removeBatchWaitingForACK(e.batchNr)
      log.debug("received aggregation (batch finished) - jobId: {}, batchNr: {} ", e.jobID, e.batchNr)
      acceptResultMsg(e)
      if (jobManagerState.batchDistributor.nrResultsAccepted % 10 == 0 ||
        jobManagerState.batchDistributor.nrResultsAccepted == jobManagerState.jobToProcess.size) {
        log.info(s"received nr of results: ${jobManagerState.batchDistributor.nrResultsAccepted}")
      }
    case WriteResultAndSendFailNoteAndTakePoisonPillCmd =>
      log.warning("received WriteResultAndSendFailNoteAndTakePoisonPillCmd")
      reportResultsTo ! MaxTimeExceededEvent(jobId)
      context.become(ignoringAll)
      self ! PoisonPill
    // retrieve the currently processing actors and retrieve from all the status
    case GetStatusForWorkers =>
      val reportTo: ActorRef = sender()
      // send GetWorkerStatus messages
      val runningBatches = jobManagerState.runningBatches
      val messages = runningBatches.map(batchNr => GetWorkerStatus(ExecutionType.RUNNABLE, jobId, batchNr))
      implicit val timeout: Timeout = Timeout(1 second)
      val responseFuture: Future[Seq[Any]] = Future.sequence(messages.map(x => workerServiceRouter.ask(x)))
      responseFuture.onComplete({
        case Success(value) =>
          reportTo ! WorkerStatusResponse[U](Right(value = value.asInstanceOf[Seq[AggregationState[U]]]))
        case Failure(e) =>
          reportTo ! WorkerStatusResponse[U](Left(e))
      })
    case WorkerKilled(batchNr) =>
      jobManagerState.removeExpectationForBatchId(batchNr)
      fillUpFreeSlots()
    case e =>
      log.warning(s"Unknown message '$e', ignoring")
  }

  def ignoringAll: Receive = {
    case _ =>
  }

  override def receive: Receive = startState
}
