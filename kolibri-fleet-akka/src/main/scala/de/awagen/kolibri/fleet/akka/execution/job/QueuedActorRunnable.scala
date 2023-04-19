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


package de.awagen.kolibri.fleet.akka.execution.job

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete, Unzip, Zip}
import akka.{Done, NotUsed}
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.definitions.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnable.JobActorConfig
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnableSinkType.{IGNORE_SINK, REPORT_TO_ACTOR_SINK}
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnableUtils.sendToSeparateActorFlow
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


/**
 * Allows definition of workflow. The runnable graph will only utilize the actorSink
 * if the JobActorConfig passed to the generator actually contains actor type REPORT_TO,
 * otherwise ignored.
 *
 * This implementation provides a queued execution, that is after calling run elements can be offered
 * to the resulting queued graph and will be processed when accepted. Done to avoid multiple instantiations
 * of flows in case of connection pool flow requests to external systems. This can lead to errors due to how akka http
 * handles this (e.g per flow initialization max concurrent requests are filled initially before any backpressure
 * can regulate it. If all initializations exceed the max connections nr, exceptions will be thrown till
 * the execution is back-pressured enough to avoid this from happening). By having a single execution flow per job
 * and node, we circumvent that.
 *
 * @tparam U - input element type
 * @tparam V - message type to be send to actor passed to actorSink function per element
 * @param jobId                - id of the runnable
 * @param batchNr              - nr of the batch of the overall job
 * @param supplier             - the IndexedGenerator[U] of data to process
 * @param transformer          - the transformation applied to elements of the supplier, U => V
 * @param processingActorProps - if set, send ProcessingMessage[V] to actor of specified type for processing
 * @param expectationGenerator - the execution expectation. Can be utilized by actor running the actorRunnable to determine whether
 *                             processing succeeded, failed due to processing or due to exceeding timeout
 * @param sinkType             - only if set not set to IGNORE_SINK and actorConfig passed to getRunnableGraph contains REPORT_TO actor type
 *                             will the result of each transformation (U => V) of type V be send to the actorRef given by REPORT_TO type in the actorConfig.
 *                             Otherwise Sink.ignore is used. Note that the expectation can still be non empty in case the sending of responses
 *                             does not happen in the sink here but messages are send to other processors which then provide the response
 * @param waitTimePerElement   - max time to wait per processing element (in case processingActorProps are not None; only used to set timeout to the respective ask future)
 */
case class QueuedActorRunnable[U, V, V1, Y <: WithCount](jobId: String,
                                                         batchNr: Int,
                                                         supplier: IndexedGenerator[(U, Option[ActorRef])],
                                                         transformer: Flow[U, ProcessingMessage[V], NotUsed],
                                                         processingActorProps: Option[Props],
                                                         aggregatorConfig: AggregatorConfig[V1, Y],
                                                         expectationGenerator: Int => ExecutionExpectation,
                                                         sinkType: ActorRunnableSinkType.Value,
                                                         waitTimePerElement: FiniteDuration,
                                                         maxExecutionDuration: FiniteDuration,
                                                         sendResultsBack: Boolean) extends KolibriSerializable with RunnableGraphProvider[(U, Option[ActorRef]), (SourceQueueWithComplete[IndexedGenerator[(U, Option[ActorRef])]], (UniqueKillSwitch, Future[Done]))] {

  val log: Logger = LoggerFactory.getLogger(QueuedActorRunnable.getClass)

  private[job] def passThruFlow: Flow[(ProcessingMessage[V1], Option[ActorRef]), (ProcessingMessage[V1], Option[ActorRef]), NotUsed] = Flow.fromFunction[(ProcessingMessage[V1], Option[ActorRef]), (ProcessingMessage[V1], Option[ActorRef])](identity)

  private[job] def getSendToActorFlow: Flow[(ProcessingMessage[V1], Option[ActorRef]), Any, NotUsed] = {
    sinkType match {
      case IGNORE_SINK =>
        passThruFlow
      case REPORT_TO_ACTOR_SINK =>
        ActorRunnableUtils.sendResultToActorFlowFunction[(ProcessingMessage[V1], Option[ActorRef]), V1, Y](jobId, batchNr, aggregatorConfig, expectationGenerator, x => x._1, x => x._2)
      case e =>
        log.warn(s"return type '$e' not covered, just passing processing element through")
        passThruFlow
    }
  }

  private[job] def getKillSwitch[Z]: Flow[Z, Z, UniqueKillSwitch] = {
    Flow.fromGraph(KillSwitches.single[Z])
  }

  def getFlow(implicit actorSystem: ActorSystem, actorContext: ActorContext, mat: Materializer, ec: ExecutionContext): Flow[(U, Option[ActorRef]), Any, NotUsed] = {
    val MaxSubStreams = 100
    Flow.fromGraph(GraphDSL.create() {
      implicit builder =>
          import GraphDSL.Implicits._
          // add stage to unzip the message itself and optional actor into two distinct flows
          val flowUnzipStage: FanOutShape2[(U, Option[ActorRef]), U, Option[ActorRef]] = builder.add(Unzip[U, Option[ActorRef]])
          // flow to execute computation as defined in passed transformer
          val flowStage: FlowShape[U, ProcessingMessage[V]] = builder.add(transformer)

          // stage sending message to separate actor if processingActorProps is set, otherwise
          // not changing the input element
          val sendStage: FlowShape[ProcessingMessage[V], ProcessingMessage[V1]] = builder.add(sendToSeparateActorFlow(processingActorProps, waitTimePerElement))

          // combining the normal processing flow with the separate optional ActorRef
          val combineValueWithConfigStage = builder.add(Zip[ProcessingMessage[V1], Option[ActorRef]])

          // grouping by hash of the passed receiving ActorRef
          val groupedFlow =
            passThruFlow
              .groupBy(
                maxSubstreams = MaxSubStreams,
                f = x => x._2.map(actorRef => actorRef.hashCode()).getOrElse(0),
                allowClosedSubstreamRecreation = true
              )
          val groupAndSendAndMergeFlow: Flow[(ProcessingMessage[V1], Option[ActorRef]), Any, NotUsed] = groupedFlow.via(getSendToActorFlow).mergeSubstreams
          val groupAndSendAndMergeStage = builder.add(groupAndSendAndMergeFlow)

          // connect graph
          // first split the input in two streams, one holding the data, the other the optional sink ActorRef (Option[ActorRef])
          flowUnzipStage.out0 ~> flowStage ~> sendStage ~> combineValueWithConfigStage.in0
          flowUnzipStage.out1 ~> combineValueWithConfigStage.in1
          combineValueWithConfigStage.out ~> groupAndSendAndMergeStage
          FlowShape(flowUnzipStage.in, groupAndSendAndMergeStage.out)
    })
  }

  /**
   * Create processing queue to which we can offer new generators to process elements.
   * Note that the aggregating actor reference is picked per element since we assume that the queue accepts
   * generators for distinct batches, and each batch is aggregated separately.
   * The queue is bounded and the result of the offer-call has to be handled.
   */
  override def getRunnableGraph(config: JobActorConfig)(implicit actorSystem: ActorSystem, actorContext: ActorContext, mat: Materializer, ec: ExecutionContext): RunnableGraph[(SourceQueueWithComplete[IndexedGenerator[(U, Option[ActorRef])]], (UniqueKillSwitch, Future[Done]))] = {
    val QueueSize = 10
    Source.queue[IndexedGenerator[(U, Option[ActorRef])]](QueueSize, OverflowStrategy.backpressure)
      .flatMapConcat(x => Source.fromIterator(() => x.iterator))
      .via(getFlow)
      .toMat(getKillSwitch[Any].toMat(Sink.ignore)(Keep.both))((x, y) => (x, y))
  }

}
