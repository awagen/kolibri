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


package de.awagen.kolibri.base.processing.execution.job

import akka.actor.{ActorContext, ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.{Done, NotUsed}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.base.processing.execution.job
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableUtils.{actorSinkFunction, getKillSwitch, sendToSeparateActorFlow}
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.Types.{With, WithCount}
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
case class QueuedActorRunnable[U, V, V1 <: With[JobActorConfig], Y <: WithCount](jobId: String,
                                                                                 batchNr: Int,
                                                                                 supplier: IndexedGenerator[U],
                                                                                 transformer: Flow[U, ProcessingMessage[V], NotUsed],
                                                                                 processingActorProps: Option[Props],
                                                                                 aggregatorConfig: AggregatorConfig[V1, Y],
                                                                                 expectationGenerator: Int => ExecutionExpectation,
                                                                                 sinkType: job.ActorRunnableSinkType.Value,
                                                                                 waitTimePerElement: FiniteDuration,
                                                                                 maxExecutionDuration: FiniteDuration,
                                                                                 sendResultsBack: Boolean) extends KolibriSerializable with RunnableGraphProvider[U, (SourceQueueWithComplete[IndexedGenerator[U]], (UniqueKillSwitch, Future[Done]))] {

  val log: Logger = LoggerFactory.getLogger(QueuedActorRunnable.getClass)

  private[job] def getSendToActorFlow: Flow[ProcessingMessage[V1], ProcessingMessage[V1], NotUsed] = {
    sinkType match {
      case IGNORE_SINK =>
        Flow.fromFunction[ProcessingMessage[V1], ProcessingMessage[V1]](identity)
      case REPORT_TO_ACTOR_SINK =>
        val sinkFunc: V1 => Unit = x => x.withInstance.others.get(ActorType.ACTOR_SINK).map(actorSink => {
          actorSinkFunction(jobId, batchNr, aggregatorConfig, expectationGenerator).apply(actorSink)
        }).getOrElse(())
        Flow.fromFunction[ProcessingMessage[V1], ProcessingMessage[V1]](x => {
          sinkFunc(x.data)
          x
        })
      case e =>
        log.warn(s"return type '$e' not covered, just passing processing element through")
        Flow.fromFunction[ProcessingMessage[V1], ProcessingMessage[V1]](identity)
    }
  }

  /**
   * Create processing queue to which we can offer new generators to process elements.
   * Note that the aggregating actor reference is picked per element since we assume that the queue accepts
   * generators for distinct batches, and each batch is aggregated separately.
   * The queue is bounded and the result of the offer-call has to be handled.
   *
   * @param config - JobActorConfig to
   * @param actorContext
   * @param mat
   * @param ec
   * @tparam T
   */
  override def getRunnableGraph(config: JobActorConfig)(implicit actorSystem: ActorSystem, actorContext: ActorContext, mat: Materializer, ec: ExecutionContext): RunnableGraph[(SourceQueueWithComplete[IndexedGenerator[U]], (UniqueKillSwitch, Future[Done]))] = {
    val QueueSize = 10
    Source.queue[IndexedGenerator[U]](QueueSize, OverflowStrategy.backpressure)
      .flatMapConcat(x => Source.fromIterator(() => x.iterator))
      .via(transformer)
      .via[ProcessingMessage[V1], NotUsed](sendToSeparateActorFlow(processingActorProps, waitTimePerElement))
      // we need the grouping to be able to apply grouping of results before sending
      // to aggregation actor. GroupBy creates substream per group
      .groupBy(maxSubstreams = 10, f = x => x.data.withInstance.executing.hashCode(), allowClosedSubstreamRecreation = true)
      // send partial results to aggregating actor
      .via(getSendToActorFlow)
      // after aggregation, we merge the streams again to get rid of per-group sub-streams
      .mergeSubstreams
      .toMat(getKillSwitch[V1].toMat(Sink.ignore)(Keep.both))((x, y) => (x, y))
  }
}
