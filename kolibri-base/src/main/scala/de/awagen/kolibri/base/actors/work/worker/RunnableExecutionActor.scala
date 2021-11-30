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

package de.awagen.kolibri.base.actors.work.worker

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.stream.scaladsl.RunnableGraph
import akka.stream.{ActorAttributes, UniqueKillSwitch}
import de.awagen.kolibri.base.actors.work.worker.AggregatingActor.{FinalReportState, ProvideStateAndStop, StateUpdateWithoutData}
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.BaseJobPartIdentifier
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, AggregationStateWithoutData}
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor.{BatchProcessState, BatchProcessStateResult, ReportBatchState, RunnableHousekeeping}
import de.awagen.kolibri.base.config.AppProperties.config
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.base.config.EnvVariableKeys.CLUSTER_NODE_HOST
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.decider.Deciders.allResumeDecider
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable.JobActorConfig
import de.awagen.kolibri.base.processing.execution.job.{ActorRunnable, ActorType}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.Types.WithCount

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}


object RunnableExecutionActor {

  def probs[U <: WithCount](maxBatchDurationInSeconds: FiniteDuration,
                            writerOpt: Option[Writer[U, Tag, _]]): Props =
    Props(new RunnableExecutionActor[U](maxBatchDurationInSeconds, writerOpt)).withDispatcher(kolibriDispatcherName)

  sealed trait RunnableExecutionActorCmd extends KolibriSerializable

  sealed trait RunnableExecutionActorEvent extends KolibriSerializable

  case object Terminate extends RunnableExecutionActorCmd

  case object ReportBatchState extends RunnableExecutionActorCmd

  case object RunnableHousekeeping extends RunnableExecutionActorCmd

  case class BatchProcessState(node: String, jobId: String, batchNr: Int, totalElements: Int, processedElementCount: Int) extends RunnableExecutionActorEvent

  sealed case class BatchProcessStateResult(jobId: String, batchNr: Int, result: Either[Throwable, BatchProcessState]) extends RunnableExecutionActorEvent

}

/**
  * Actor to handle execution of an ActorRunnable.
  * ActorRunnable provides the Iterable[U] over the elements to process and
  * a flow which processes elements of type U to the message expected per element.
  * This is send to the ActorRef given in JobActorConfig actor corresponding to ACTOR_SINK type
  * if set and within ActorRunnable the sink type is NOT IGNORE_SINK.
  */
class RunnableExecutionActor[U <: WithCount](maxBatchDuration: FiniteDuration,
                                             val writerOpt: Option[Writer[U, Tag, _]]) extends Actor with ActorLogging with KolibriSerializable {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)
  // actual actor config to be able to pass actors for certain functions along
  // e.g actor for sink, actor to be passed as single execution sender and such
  private[this] var actorConfig: JobActorConfig = _
  // kill switch of the graph execution to stop the execution if needed
  private[this] var killSwitch: UniqueKillSwitch = _
  // Future of the graph execution
  private[this] var executionFuture: Future[Done] = _
  // expectation bound to the job execution.
  // actually the expectation from the actual ActorRunnable is only needed for
  // the aggregatingActor. Here we only need a simplified expectation,
  // that is a single AggregationState msg from the aggregatingActor, thats all
  private[this] var expectation: ExecutionExpectation = _
  // jobId and batch number
  private[this] var runningJobId: String = _
  private[this] var runningJobBatchNr: Int = _
  // sender to send results of the aggregatingActor back to
  private[this] var jobSender: ActorRef = _
  // the actor aggregating results
  private[this] var aggregatingActor: ActorRef = _
  // cancellable of housekeeping schedule
  private[this] var housekeepingCancellable: Cancellable = _
  // nr of elements processed in ActorRunnable
  private[this] var elementsToProcessCount: Int = _
  var sendResultsBack: Boolean = true
  var batchStateUpdate: StateUpdateWithoutData = _

  val readyForJob: Receive = {
    case runnable: ActorRunnable[_, _, _, U] =>
      sendResultsBack = runnable.sendResultsBack
      jobSender = sender()
      // jobId and batchNr might be used as identifiers to filter received messages by
      runningJobId = runnable.jobId
      runningJobBatchNr = runnable.batchNr
      elementsToProcessCount = runnable.supplier.size
      aggregatingActor = context.actorOf(AggregatingActor.props(
        runnable.aggregatorConfig,
        () => runnable.expectationGenerator.apply(runnable.supplier.size),
        owner = this.self,
        jobPartIdentifier = BaseJobPartIdentifier(jobId = runningJobId, batchNr = runningJobBatchNr),
        writerOpt,
        sendResultBack = runnable.sendResultsBack
      ))
      // set the initial state to an empty one
      batchStateUpdate = StateUpdateWithoutData(AggregationStateWithoutData(0, runningJobId, runningJobBatchNr, BaseExecutionExpectation.empty()), isFinal = false)
      // we set the aggregatingActor as receiver of all messages
      // (whether graph sink is used or setting the aggregator as sender when sending
      // messages to executing actors within the graph)
      actorConfig = JobActorConfig(self,
        Map(ActorType.ACTOR_SINK -> aggregatingActor))
      log.debug(s"RunnableExecutionActor received actor runnable to process, jobId: ${runnable.jobId}, batchNr: ${runnable.batchNr}")

      val runnableGraph: RunnableGraph[(UniqueKillSwitch, Future[Done])] = runnable.getRunnableGraph(actorConfig)
        .withAttributes(ActorAttributes.supervisionStrategy(allResumeDecider))

      // the time allowed per execution is actually defined within the expectation
      // passed to the aggregation actor, thus if time ran out there the aggregation
      // state will be reported back, thus we only set expectation on receiving
      // that one AggregationState
      val failExpectations: Seq[Expectation[Any]] = Seq(TimeExpectation(maxBatchDuration))
      expectation = BaseExecutionExpectation(
        fulfillAllForSuccess = Seq(
          ClassifyingCountExpectation(Map("aggregationState" -> {
            case _: AggregationState[_] => true
            case _ => false
          }), expectedClassCounts = Map("aggregationState" -> 1)),
        ),
        fulfillAnyForFail = failExpectations)
      expectation.init
      val outcome: (UniqueKillSwitch, Future[Done]) = runnableGraph.run()
      // when complete, send the aggregation to the aggregatig actor (well, aggregating actor actually not needed in that case)
      outcome._2.onComplete(_ => {
        log.info("graph completed, notifying aggregator to send results and stop aggregating")
        aggregatingActor ! ProvideStateAndStop
        ()
      })
      killSwitch = outcome._1
      executionFuture = outcome._2
      context.become(processing)
      // schedule the housekeeping, checking the runnable status
      housekeepingCancellable = context.system.scheduler.scheduleAtFixedRate(
        initialDelay = config.runnableExecutionActorHousekeepingInterval,
        interval = config.runnableExecutionActorHousekeepingInterval,
        receiver = self,
        message = RunnableHousekeeping)
      context.system.scheduler.scheduleOnce(maxBatchDuration)(self ! PoisonPill)
    case e =>
      log.warning("this actor needs a ActorRunnable Msg to execute, currently ignoring all others. Ignored msg: {}; in " +
        "case the runnableGraph sends back responses to this actor (see JobActorConfig), you might have forgotten to add it to " +
        "the receive", e)
  }

  val processing: Receive = {
    case RunnableHousekeeping =>
      if (expectation.succeeded) {
        log.info("Expectation succeeded, shutting down stream and killing actor")
        killSwitch.shutdown()
        self ! PoisonPill
      }
      else if (expectation.failed) {
        log.info("Expectation failed, shutting down stream and killing actor")
        killSwitch.abort(new RuntimeException(s"Expectation failed:\n${expectation.statusDesc}"))
        aggregatingActor ! ProvideStateAndStop
      }
    // this is the final report, e.g after filtering, coming from AggregatingActor
    case FinalReportState(state) =>
      log.debug("received aggregation (batch finished): {}", state)
      expectation.accept(state)
      housekeepingCancellable.cancel()
      jobSender ! state
      self ! PoisonPill
    // expected regularly from AggregatingActor to keep track of the state
    case stateUpdate@StateUpdateWithoutData(_, _) =>
      log.debug(s"received state update: $stateUpdate")
      batchStateUpdate = stateUpdate
    case ReportBatchState =>
      log.debug("received ReportResults message")
      val reportTo: ActorRef = sender()
      val resultMessage = BatchProcessStateResult(runningJobId, runningJobBatchNr, Right(batchProcessState()))
      reportTo ! resultMessage
    case msg =>
      expectation.accept(msg)
  }

  def batchProcessState(): BatchProcessState = {
    BatchProcessState(
      CLUSTER_NODE_HOST.value,
      batchStateUpdate.state.jobID,
      batchStateUpdate.state.batchNr,
      elementsToProcessCount,
      batchStateUpdate.state.containedElementCount
    )
  }

  override def receive: Receive = readyForJob

}

