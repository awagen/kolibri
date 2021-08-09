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

package de.awagen.kolibri.base.actors.work.aboveall

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, PoisonPill, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor._
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor._
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{ProcessingMessage, ResultSummary}
import de.awagen.kolibri.base.actors.work.worker.TaskExecutionWorkerActor
import de.awagen.kolibri.base.config.AppConfig._
import de.awagen.kolibri.base.config.AppConfig.config.{kolibriDispatcherName, maxNrBatchRetries}
import de.awagen.kolibri.base.domain.jobdefinitions.Batch
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions.MapWithCount
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluation, TestPiCalculation}
import de.awagen.kolibri.base.processing.classifier.Mapper.FilteringMapper
import de.awagen.kolibri.base.processing.execution.SimpleTaskExecution
import de.awagen.kolibri.base.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.processing.execution.task.utils.TaskUtils
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.types.WithCount
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{DurationInt, FiniteDuration}


object SupervisorActor {

  def props(returnResponseToSender: Boolean): Props = Props(SupervisorActor(returnResponseToSender))

  sealed trait SupervisorMsg extends KolibriSerializable

  sealed trait SupervisorCmd extends SupervisorMsg

  sealed trait SupervisorEvent extends SupervisorMsg

  case object KillAllChildren extends SupervisorCmd

  case class GetJobWorkerStatus(job: String)

  type ActorRunnableJobGenerator[U, V, V1, W <: WithCount] = IndexedGenerator[ActorRunnable[U, V, V1, W]]
  type TaggedTypeTaggedMapBatch = Batch[TypeTaggedMap with TaggedWithType[Tag]]
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

  case class StopJob(jobId: String) extends SupervisorCmd

  private case object JobHousekeeping extends SupervisorCmd

  object ProcessingResult extends Enumeration {
    val SUCCESS, FAILURE, RUNNING = Value
  }

  case class RunningJobs(jobIDs: Seq[String]) extends SupervisorEvent

  case class ExpectationForJob(jobId: String, expectation: ExecutionExpectation) extends SupervisorEvent

  case class JobNotFound(jobId: String) extends SupervisorEvent

  case class FinishedJobEvent(jobId: String, resultSummary: ResultSummary) extends SupervisorEvent

}


case class SupervisorActor(returnResponseToSender: Boolean) extends Actor with ActorLogging with KolibriSerializable {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  case class ActorSetup(executing: ActorRef, jobSender: ActorRef)

  val jobIdToActorRefAndExpectation: mutable.Map[String, (ActorSetup, ExecutionExpectation)] = mutable.Map.empty

  def createJobManagerActor[T, U <: WithCount](jobId: String,
                                               perBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                               perJobAggregatorSupplier: () => Aggregator[ProcessingMessage[T], U],
                                               writer: Writer[U, Tag, _],
                                               allowedTimeForJob: FiniteDuration,
                                               allowedTimeForBatch: FiniteDuration,
                                               maxNumRetries: Int): ActorRef = {
    context.actorOf(JobManagerActor.props[T, U](
      experimentId = jobId,
      runningTaskBaselineCount = config.runningTasksBaselineCount,
      perBatchAggregatorSupplier = perBatchAggregatorSupplier,
      perJobAggregatorSupplier = perJobAggregatorSupplier,
      writer = writer,
      maxProcessDuration = allowedTimeForJob,
      maxBatchDuration = allowedTimeForBatch,
      maxNumRetries).withDispatcher(kolibriDispatcherName),
      name = JobManagerActor.name(jobId))
  }

  def createTaskExecutionWorkerProps(finalResultKey: ClassTyped[_]): Props = {
    TaskExecutionWorkerActor.props
  }

  // we only expect one FinishedJobEvent per job
  // StopExpectation met if an FinishedJobEvent has FAILURE result type, ignores all other messages
  // except FinishedJobEvent; also sets a TimeoutExpectation to abort
  // jobs on exceeding it
  def createJobExecutionExpectation(allowedDuration: FiniteDuration): ExecutionExpectation = {
    BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(ClassifyingCountExpectation(Map("finishResponse" -> {
        case e: FinishedJobEvent if e.resultSummary.result == ProcessingResult.SUCCESS => true
        case _ => false
      }), Map("finishResponse" -> 1))),
      fulfillAnyForFail = Seq(
        StopExpectation(
          overallElementCount = 1,
          errorClassifier = {
            case e: FinishedJobEvent if e.resultSummary.result == ProcessingResult.SUCCESS => SuccessAndErrorCounts(1, 0)
            case e: FinishedJobEvent if e.resultSummary.result == ProcessingResult.FAILURE => SuccessAndErrorCounts(0, 1)
            case _ => SuccessAndErrorCounts(0, 0)
          },
          overallCountToFailCountFailCriterion = new SerializableFunction1[(Int, Int), Boolean] {
            override def apply(v1: (Int, Int)): Boolean = v1._2 > 0
          }),
        TimeExpectation(allowedDuration))
    )
  }

  // schedule the housekeeping, checking each jobId
  context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.supervisorHousekeepingInterval,
    interval = config.supervisorHousekeepingInterval,
    receiver = self,
    message = JobHousekeeping)


  override def receive: Receive = {
    case ProvideAllRunningJobIDs =>
      sender() ! RunningJobs(jobIdToActorRefAndExpectation.keys.toSeq)
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
    case ProvideJobState(jobId) =>
      val requestingActor = sender()
      val actorSetupAndExpectation: Option[(ActorSetup, ExecutionExpectation)] = jobIdToActorRefAndExpectation.get(jobId)
      actorSetupAndExpectation.foreach(x => {
        // forward the status-requesting message to processing actor, which will send his response to the initial sender
        x._1.executing forward ProvideJobStatus
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
          allowedTimeForBatch = job.allowedTimePerBatch,
          maxNumRetries = maxNrBatchRetries)
        // registering actor to receive Terminated messages in case a
        // job manager actor stopped
        context.watch(actor)
        actor ! ProcessJobCmd(job.processElements)
        val expectation = createJobExecutionExpectation(job.allowedTimeForJob)
        expectation.init
        jobIdToActorRefAndExpectation(jobId) = (ActorSetup(actor, jobSender), expectation)
      }
    case e: TestPiCalculation =>
      val jobSender = sender()
      val jobId = e.jobName
      if (jobIdToActorRefAndExpectation.contains(jobId)) {
        log.warning("Job with id {} is still running, thus not starting that here", jobId)
      }
      else {
        log.info("Creating and sending job to JobManager, jobId: {}", jobId)
        import de.awagen.kolibri.base.processing.JobMessagesImplicits._
        val runnableJob: ProcessActorRunnableJobCmd[Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]] = e.toRunnable
        val actor = createJobManagerActor(
          jobId,
          runnableJob.perBatchAggregatorSupplier,
          runnableJob.perJobAggregatorSupplier,
          runnableJob.writer,
          runnableJob.allowedTimeForJob,
          runnableJob.allowedTimePerBatch, maxNrBatchRetries)
        context.watch(actor)
        actor ! e
        val expectation = createJobExecutionExpectation(runnableJob.allowedTimeForJob)
        expectation.init
        jobIdToActorRefAndExpectation(jobId) = (ActorSetup(actor, jobSender), expectation)
      }
    case e: SearchEvaluation =>
      val jobSender = sender()
      val jobId = e.jobName
      if (jobIdToActorRefAndExpectation.contains(jobId)) {
        log.warning("Job with id {} is still running, thus not starting that here", jobId)
      }
      else {
        log.info("Creating and sending job to JobManager, jobId: {}", jobId)
        import de.awagen.kolibri.base.processing.JobMessagesImplicits._
        implicit val timeout: Timeout = 10 minutes
        val runnableJob: ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = e.toRunnable
        val actor = createJobManagerActor(
          jobId,
          runnableJob.perBatchAggregatorSupplier,
          runnableJob.perJobAggregatorSupplier,
          runnableJob.writer,
          runnableJob.allowedTimeForJob,
          runnableJob.allowedTimePerBatch,
          maxNrBatchRetries)
        context.watch(actor)
        actor ! e
        val expectation = createJobExecutionExpectation(runnableJob.allowedTimeForJob)
        expectation.init
        jobIdToActorRefAndExpectation(jobId) = (ActorSetup(actor, jobSender), expectation)
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
          job.allowedTimePerBatch,
          maxNrBatchRetries)
        context.watch(actor)
        actor ! ProcessJobCmd(mappedIterable)
        val expectation = createJobExecutionExpectation(job.allowedTimeForJob)
        expectation.init
        jobIdToActorRefAndExpectation(jobId) = (ActorSetup(actor, jobSender), expectation)
      }
    case event: FinishedJobEvent =>
      log.info("Experiment with id {} finished processing", event.jobId)
      jobIdToActorRefAndExpectation.get(event.jobId).foreach(x => x._2.accept(event))
      val actorSetup: Option[ActorSetup] = jobIdToActorRefAndExpectation.get(event.jobId).map(x => x._1)
      if (returnResponseToSender) {
        actorSetup.foreach(x => x.jobSender ! event)
      }
      jobIdToActorRefAndExpectation -= event.jobId
    case KillAllChildren => context.children.foreach(x => context.stop(x))
    case GetJobWorkerStatus(jobId) =>
      val reportTo: ActorRef = sender()
      implicit val timeout: Timeout = Timeout(1 second)
      jobIdToActorRefAndExpectation.get(jobId).foreach(x => {
        x._1.executing.ask(GetStatusForWorkers, self).onComplete(result => reportTo ! result)
      })
    case e =>
      log.warning("Unknown message (will be ignored): {}", e)
  }

  /**
    * We keep a very relaxed supervision here by just allowing the actors
    * responsible for distributing the processing of single jobs to keep their state
    * and continue listening to messages arriving at their inbox.
    */
  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = config.supervisorMaxNumOfRetries, withinTimeRange = config.supervisorMaxNumOfRetriesWithinTime) {
      case e: Exception =>
        log.warning("Child of supervisor actor threw exception: {}; stopping", e)
        Stop
    }
}

