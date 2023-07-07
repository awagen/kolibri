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

package de.awagen.kolibri.fleet.akka.actors.work.aboveall

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Cancellable, OneForOneStrategy, PoisonPill, Props, Terminated}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.stores.mutable.PriorityStores.{BasePriorityStore, PriorityStore}
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.Aggregator
import de.awagen.kolibri.definitions.domain.jobdefinitions.Batch
import de.awagen.kolibri.definitions.processing.JobMessages.{JobDefinition, SearchEvaluationDefinition}
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{JobStatusInfo, ProcessingMessage, ProcessingResult}
import de.awagen.kolibri.definitions.processing.classifier.Mapper.FilteringMapper
import de.awagen.kolibri.definitions.processing.execution.SimpleTaskExecution
import de.awagen.kolibri.definitions.processing.execution.expectation.ExecutionExpectations.finishedJobExecutionExpectation
import de.awagen.kolibri.definitions.processing.execution.expectation._
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.processing.execution.task.Task
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor._
import de.awagen.kolibri.fleet.akka.actors.work.manager.JobManagerActor
import de.awagen.kolibri.fleet.akka.actors.work.manager.JobManagerActor._
import de.awagen.kolibri.fleet.akka.actors.work.worker.TaskExecutionWorkerActor
import de.awagen.kolibri.fleet.akka.config.AppProperties._
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.{kolibriBlockingDispatcherName, kolibriDispatcherName}
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnable
import de.awagen.kolibri.fleet.akka.execution.task.utils.TaskUtils
import de.awagen.kolibri.fleet.akka.processing.JobMessagesImplicits.{SearchEvaluationImplicits, TestPiCalcToRunnable}
import de.awagen.kolibri.storage.io.writer.Writers.Writer

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContextExecutor, Future}


object SupervisorActor {

  def props(returnResponseToSender: Boolean): Props = Props(SupervisorActor(returnResponseToSender))

  val jobSuccessCriterion: FinishedJobEvent => Boolean = e => e.jobStatusInfo.resultSummary.result == ProcessingResult.SUCCESS
  val jobFailCriterion: FinishedJobEvent => Boolean = e => e.jobStatusInfo.resultSummary.result == ProcessingResult.FAILURE

  val JOB_HISTORY_PRIORITY_STORE_KEY = "jobs"
  val finishedJobStateOrdering: Ordering[JobStatusInfo] = (x, y) => {
    val order: Option[Int] = for (
      endTime1 <- x.endTime;
      endTime2 <- y.endTime
    ) yield endTime1.compareTo(endTime2)
    order.getOrElse(0)
  }

  def createTaskExecutionWorkerProps(finalResultKey: ClassTyped[_]): Props = {
    TaskExecutionWorkerActor.props
  }

  def createJobManagerActor[T, U <: WithCount](jobId: String,
                                               perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                               perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                               writer: Writer[U, Tag, _],
                                               allowedTimeForJob: FiniteDuration,
                                               allowedTimeForBatch: FiniteDuration)(implicit context: ActorContext): ActorRef = {
    context.actorOf(JobManagerActor.props[T, U](
      experimentId = jobId,
      perBatchAggregatorSupplier = perBatchAggregatorSupplier,
      perJobAggregatorSupplier = perJobAggregatorSupplier,
      writer = writer,
      maxProcessDuration = allowedTimeForJob,
      maxBatchDuration = allowedTimeForBatch).withDispatcher(kolibriDispatcherName),
      name = JobManagerActor.name(jobId))
  }

  def runnableJobCmdToJobManager[M <: WithCount](jobId: String, eval: SearchEvaluationDefinition)(implicit context: ActorContext): ActorRef = {
    createJobManagerActor(
      jobId,
      eval.getBatchAggregationSupplier,
      eval.perJobAggregationSupplier,
      eval.getWriter,
      FiniteDuration(eval.allowedTimeForJobInSeconds, SECONDS),
      FiniteDuration(eval.allowedTimePerBatchInSeconds, SECONDS))
  }

  def runnableJobCmdToJobManager[M <: WithCount](jobId: String, cmd: ProcessActorRunnableJobCmd[_, _, _, M])(implicit context: ActorContext): ActorRef = {
    createJobManagerActor(
      jobId,
      cmd.perBatchAggregatorSupplier,
      cmd.perJobAggregatorSupplier,
      cmd.writer,
      cmd.allowedTimeForJob,
      cmd.allowedTimePerBatch)
  }

  case class ActorSetup(executing: ActorRef, jobSender: ActorRef)

  sealed trait SupervisorMsg extends KolibriSerializable

  sealed trait SupervisorCmd extends SupervisorMsg

  sealed trait SupervisorEvent extends SupervisorMsg

  case object KillAllChildren extends SupervisorCmd

  case class GetJobWorkerStatus(job: String) extends SupervisorCmd

  type ActorRunnableJobGenerator[U, V, V1, W <: WithCount] = IndexedGenerator[ActorRunnable[U, V, V1, W]]
  type TaggedTypeTaggedMapBatch = Batch[TypeTaggedMap with TaggedWithType]
  type BatchTypeTaggedMapGenerator = IndexedGenerator[TaggedTypeTaggedMapBatch]

  // usual starting point for batch executions would be OrderedMultiValues, which by help of BatchGenerator can be split
  // e.g by parameter values for selected parameter or batch size and by utilizing OrderedMultiValuesImplicits
  // this can be mapped to actual Iterators over the parameter values contained in the split
  // OrderedMultiValues
  case class ProcessActorRunnableJobCmd[V, V1, V2, U <: WithCount](jobId: String,
                                                                   processElements: ActorRunnableJobGenerator[V, V1, V2, U],
                                                                   perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[V2], U],
                                                                   perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[V2], U],
                                                                   writer: Writer[U, Tag, _],
                                                                   allowedTimePerBatch: FiniteDuration,
                                                                   allowedTimeForJob: FiniteDuration) extends SupervisorCmd

  case class ProcessActorRunnableTaskJobCmd[U <: WithCount](jobId: String,
                                                            dataIterable: BatchTypeTaggedMapGenerator,
                                                            tasks: Seq[Task[_]],
                                                            resultKey: ClassTyped[ProcessingMessage[Any]],
                                                            perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[Any], U],
                                                            perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[Any], U],
                                                            writer: Writer[U, Tag, _],
                                                            filteringSingleElementMapperForAggregator: FilteringMapper[ProcessingMessage[Any], ProcessingMessage[Any]],
                                                            filterAggregationMapperForAggregator: FilteringMapper[U, U],
                                                            filteringMapperForResultSending: FilteringMapper[U, U],
                                                            allowedTimePerBatch: FiniteDuration,
                                                            allowedTimeForJob: FiniteDuration,
                                                            sendResultsBack: Boolean) extends SupervisorCmd

  case class ProvideJobState(jobId: String) extends SupervisorCmd

  case object ProvideAllRunningJobIDs extends SupervisorCmd

  case object ProvideJobHistory extends SupervisorCmd

  case class StopJob(jobId: String) extends SupervisorCmd

  private case object JobHousekeeping extends SupervisorCmd

  case class RunningJobs(jobIDs: Seq[String]) extends SupervisorEvent

  case class JobHistory(jobs: Seq[JobStatusInfo]) extends SupervisorEvent

  case class ExpectationForJob(jobId: String, expectation: ExecutionExpectation) extends SupervisorEvent

  case class JobNotFound(jobId: String) extends SupervisorEvent

  case class FinishedJobEvent(jobId: String, jobStatusInfo: JobStatusInfo) extends SupervisorEvent

}


case class SupervisorActor(returnResponseToSender: Boolean) extends Actor with ActorLogging with KolibriSerializable {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  val jobIdToActorRefAndExpectation: mutable.Map[String, (ActorSetup, ExecutionExpectation)] = mutable.Map.empty

  val finishedJobStateHistory: PriorityStore[String, JobStatusInfo] = BasePriorityStore(10, finishedJobStateOrdering,
    _ => JOB_HISTORY_PRIORITY_STORE_KEY)

  // schedule the housekeeping, checking each jobId
  val houseKeepingCancellable: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.supervisorHousekeepingInterval,
    interval = config.supervisorHousekeepingInterval,
    receiver = self,
    message = JobHousekeeping)

  override def postStop(): Unit = {
    houseKeepingCancellable.cancel()
    super.postStop()
  }

  val informationProvidingReceive: Receive = {
    case ProvideJobHistory =>
      sender() ! JobHistory(finishedJobStateHistory.result.getOrElse(JOB_HISTORY_PRIORITY_STORE_KEY, Seq.empty))
    case ProvideAllRunningJobIDs =>
      sender() ! RunningJobs(jobIdToActorRefAndExpectation.keys.toSeq)
  }

  val jobStartingReceive: Receive = {
    case job: ProcessActorRunnableJobCmd[_, _, _, _] =>
      val jobSender = sender()
      val jobId = job.jobId
      if (jobIdToActorRefAndExpectation.contains(jobId)) {
        log.warning("Job with id {} is still running, thus not starting that here", jobId)
      }
      else {
        log.info("Creating and sending job to JobManager, jobId: {}", jobId)
        val actor = createJobManagerActor(
          jobId = jobId,
          perBatchAggregatorSupplier = job.perBatchAggregatorSupplier,
          perJobAggregatorSupplier = job.perJobAggregatorSupplier,
          writer = job.writer,
          allowedTimeForJob = job.allowedTimeForJob,
          allowedTimeForBatch = job.allowedTimePerBatch)
        // registering actor to receive Terminated messages in case a
        // job manager actor stopped
        context.watch(actor)
        actor ! ProcessJobCmd(job.processElements)
        val expectation = finishedJobExecutionExpectation(
          job.allowedTimeForJob,
          jobSuccessCriterion,
          jobFailCriterion
        )
        expectation.init
        jobIdToActorRefAndExpectation(jobId) = (ActorSetup(actor, jobSender), expectation)
      }
    case e: JobDefinition =>

      val jobSender = sender()
      val jobId = e.jobName
      if (jobIdToActorRefAndExpectation.contains(jobId)) {
        log.warning("Job with id {} is still running, thus not starting that here", jobId)
      }
      else {
        log.info("Creating and sending job to JobManager, jobId: {}", jobId)
        e match {
          case msg: TestPiCalcToRunnable =>
            val runnable = msg.toRunnable
            val actor = runnableJobCmdToJobManager(e.jobName, runnable)
            context.watch(actor)
            actor ! e
            val expectation = finishedJobExecutionExpectation(
              runnable.allowedTimeForJob,
              jobSuccessCriterion,
              jobFailCriterion
            )
            expectation.init
            jobIdToActorRefAndExpectation(e.jobName) = (ActorSetup(actor, jobSender), expectation)
          case msg: SearchEvaluationDefinition =>
            val actor = runnableJobCmdToJobManager(msg.jobName, msg)
            context.watch(actor)
            actor ! e
            val expectation = finishedJobExecutionExpectation(
              FiniteDuration(msg.allowedTimeForJobInSeconds, SECONDS),
              jobSuccessCriterion,
              jobFailCriterion
            )
            expectation.init
            jobIdToActorRefAndExpectation(e.jobName) = (ActorSetup(actor, jobSender), expectation)
        }
      }
    case job: ProcessActorRunnableTaskJobCmd[WithCount] =>
      val jobSender: ActorRef = sender()
      val jobId = job.jobId
      if (jobIdToActorRefAndExpectation.contains(jobId)) {
        log.warning("Experiment with id {} is still running, thus not starting that here", job.jobId)
        ()
      }
      else {
        val mappedIterable: IndexedGenerator[ActorRunnable[SimpleTaskExecution[_], Any, Any, WithCount]] =
          TaskUtils.tasksToActorRunnable(
            jobId = jobId,
            resultKey = job.resultKey,
            mapGenerator = job.dataIterable,
            tasks = job.tasks,
            aggregatorSupplier = job.perBatchAggregatorSupplier,
            taskExecutionWorkerProps = createTaskExecutionWorkerProps(finalResultKey = job.resultKey),
            timeoutPerRunnable = job.allowedTimePerBatch,
            1 minute,
            job.sendResultsBack)
        log.info("Creating and sending job to JobManager, jobId: {}", jobId)
        val actor = createJobManagerActor(
          jobId,
          job.perBatchAggregatorSupplier,
          job.perJobAggregatorSupplier,
          job.writer,
          job.allowedTimeForJob,
          job.allowedTimePerBatch)
        context.watch(actor)
        actor ! ProcessJobCmd(mappedIterable)
        val expectation = finishedJobExecutionExpectation(
          job.allowedTimeForJob,
          jobSuccessCriterion,
          jobFailCriterion
        )
        expectation.init
        jobIdToActorRefAndExpectation(jobId) = (ActorSetup(actor, jobSender), expectation)
      }
    case execution: Execution[Any] =>
      // use the executor for blocking ops for executions. They can contain any kind of aggregations and the like and thus
      // take up time
      implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriBlockingDispatcherName)
      Future {
        execution.execute
      }
  }

  val stateKeepingReceive: Receive = {
    case StopJob(jobId) =>
      val requestingActor = sender()
      val actorSetupAndExpectation: Option[(ActorSetup, ExecutionExpectation)] = jobIdToActorRefAndExpectation.get(jobId)
      actorSetupAndExpectation.foreach(x => {
        // kill the executing actor
        x._1.executing ! PoisonPill
        // send current expectation back to the initial sender
        requestingActor ! ExpectationForJob(jobId, x._2)
      })
      if (actorSetupAndExpectation.isEmpty) {
        requestingActor ! JobNotFound(jobId: String)
      }
    case JobHousekeeping =>
      // check the existing jobs and their condition
      val checkJobIds: Seq[String] = jobIdToActorRefAndExpectation.keys.toSeq
      checkJobIds.foreach(x => {
        val expectation = jobIdToActorRefAndExpectation(x)._2
        if (expectation.succeeded) {
          log.info(s"job with id '$x' suceeded")
          // just confirm to JobManager that expectation is met, the removal from tracking will be handled within
          // the received Terminated message
          jobIdToActorRefAndExpectation(x)._1.executing ! ExpectationMet
        }
        else if (expectation.failed) {
          log.info(s"job with id '$x' failed with description: ${jobIdToActorRefAndExpectation(x)._2.statusDesc}")
          jobIdToActorRefAndExpectation(x)._1.executing ! ExpectationFailed
        }
      })
    // housekeeping about watched job-processing actors
    case Terminated(actorRef: ActorRef) =>
      log.info("received Terminated message for actorRef {}", actorRef.path.name)
      val removeKey: Option[String] = jobIdToActorRefAndExpectation.keys
        .find(x => jobIdToActorRefAndExpectation(x)._1.executing == actorRef)
      removeKey.foreach(x => {
        log.info("removing job {} because processing actor {} stopped",
          x,
          actorRef.path.name)
        jobIdToActorRefAndExpectation -= x
      })
    case MaxTimeExceededEvent(jobId) =>
      log.info("received MaxTimeExceeded message for jobId {}, thus respective JobManager is expected to kill himself," +
        "which will be reflected in another Terminated message", jobId)

    case event: FinishedJobEvent =>
      log.info("Experiment with id {} finished processing", event.jobId)
      jobIdToActorRefAndExpectation.get(event.jobId).foreach(x => x._2.accept(event))
      val actorSetup: Option[ActorSetup] = jobIdToActorRefAndExpectation.get(event.jobId).map(x => x._1)
      if (returnResponseToSender) {
        actorSetup.foreach(x => x.jobSender ! event)
      }
      jobIdToActorRefAndExpectation -= event.jobId
      finishedJobStateHistory.addEntry(event.jobStatusInfo)
    case KillAllChildren => context.children.foreach(x => context.stop(x))
    case e =>
      log.warning("Unknown message (will be ignored): {}", e)
  }

  override def receive: Receive = informationProvidingReceive.orElse(jobStartingReceive).orElse(stateKeepingReceive)

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = config.supervisorMaxNumOfRetries, withinTimeRange = config.supervisorMaxNumOfRetriesWithinTime) {
      case e: Exception =>
        log.warning("Child of supervisor actor threw exception: {}; stopping", e)
        Stop
    }
}

