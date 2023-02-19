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

import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.{Batch, currentTimeZonedInstance}
import de.awagen.kolibri.base.actors.work.manager.JobProcessingState.JobStatusInfo
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages._
import de.awagen.kolibri.base.config.AppProperties.config
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.consume.Consumers.{ExecutionConsumer, getExpectingAggregatingWritingExecutionConsumer}
import de.awagen.kolibri.base.processing.distribution.Distributors.getRetryingDistributor
import de.awagen.kolibri.base.processing.distribution.{DistributionStates, Distributor}
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectations.jobExecutionExpectation
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import org.slf4j.{Logger, LoggerFactory}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Objects
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

object JobProcessingState {

  def emptyJobStatusInfo: JobStatusInfo = JobStatusInfo(
    jobId = "", jobType = "", startTime = "", endTime = Some(""), resultSummary = unknownJobResultSummary
  )

  case class JobStatusInfo(jobId: String,
                           jobType: String,
                           startTime: String,
                           endTime: Option[String],
                           resultSummary: ResultSummary) extends KolibriSerializable

}

case class JobProcessingState[U](jobId: String) {

  private[this] val log: Logger = LoggerFactory.getLogger(this.getClass)
  // distributor of tasks
  private[this] var batchDistributor: Distributor[Batch, U] = _
  // consumer of results with expectation for the whole job
  private[this] var executionConsumer: ExecutionConsumer = _
  // mapping batchNr to ExecutionExpectation for the respective batch
  private[this] val executionExpectationMap: mutable.Map[Int, ExecutionExpectation] = mutable.Map.empty
  // for those batches we havent yet received a confirmation that it is processed,
  // and timeout for wait has not yet passed after which itll be moved to failedACKReceiveBatchNumbers
  private[this] var batchesSentWaitingForACK: Set[Int] = Set.empty
  private[this] var failedACKReceiveBatchNumbers: Set[Int] = Set.empty
  // the batches to be distributed to the workers
  private[this] var jobToProcess: IndexedGenerator[Batch] = _
  // start time of processing
  private[this] var executionStartZonedDateTime: ZonedDateTime = _
  // end time of processing
  private[this] var executionEndZonedDateTime: ZonedDateTime = _
  //    var usedTasksCount: Int = config.runningTasksPerJobDefaultCount
  // job type identifier for reporting purposes
  private[this] var jobType: String = _

  def initJobToProcess[T](jobId: String,
                          jobTypeName: String,
                          job: IndexedGenerator[Batch],
                          aggregator: Aggregator[ProcessingMessage[T], U],
                          writer: Writer[U, Tag, _],
                          requestedParallelTasks: Int,
                          maxProcessDuration: FiniteDuration,
                          expectResultsFromBatchCalculations: Boolean): Unit = {
    jobType = jobTypeName
    jobToProcess = job
    val parallelTasks = math.min(requestedParallelTasks, config.runningTasksPerJobMaxCount)
    executionStartZonedDateTime = currentTimeZonedInstance()
    executionConsumer = getExpectingAggregatingWritingExecutionConsumer(jobId, jobExecutionExpectation(job.size, maxProcessDuration, expectResultsFromBatchCalculations), aggregator, writer)
    batchDistributor = getRetryingDistributor(jobToProcess, config.maxNrBatchRetries, parallelTasks)
    executionStartZonedDateTime = currentTimeZonedInstance()
  }

  def expectationState: String = {
    executionConsumer.expectation.statusDesc
  }

  def batchDistributorHasCompleted: Boolean = batchDistributor.hasCompleted
  def executionExpectationMapIsEmpty: Boolean = executionExpectationMap.keys.isEmpty

  def isJobToProcessSet: Boolean = Objects.nonNull(jobToProcess)

  def numBatches: Int = if (isJobToProcessSet) jobToProcess.size else 0

  def nrResultsAccepted: Int = batchDistributor.nrResultsAccepted

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

  def nextBatchesOrState: Either[DistributionStates.DistributionState, Seq[Batch]] = batchDistributor.next

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
    // the only expectation here is that we get a single AggregationState
    // the expectation of the runnable is actually handled within the runnable
    val expectation = ExecutionExpectations.jobExecutionExpectation(1, maxBatchDuration, expectResultsFromBatchCalculations)
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

  def jobStatusInfo(state: ProcessingResult.Value): JobStatusInfo = JobStatusInfo(
    jobId = jobId,
    jobType = jobType,
    startTime = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(executionStartZonedDateTime),
    endTime = Option.apply(executionEndZonedDateTime).map(x => DateTimeFormatter.ISO_ZONED_DATE_TIME.format(x)),
    resultSummary = resultSummary(state))
}