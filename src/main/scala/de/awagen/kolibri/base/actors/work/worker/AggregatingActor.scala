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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import de.awagen.kolibri.base.actors.work.worker.AggregatingActor._
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages._
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.config.AppConfig.config.{kolibriDispatcherName, useAggregatorBackpressure}
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.ExecutionContextExecutor

object AggregatingActor {

  /**
    * @param aggregatorConfig
    * @param owner
    * @param jobPartIdentifier
    * @param writer
    * @tparam U
    * @tparam V
    * @return
    */
  def props[U, V <: WithCount](aggregatorConfig: AggregatorConfig[U, V],
                               expectationSupplier: () => ExecutionExpectation,
                               owner: ActorRef,
                               jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier,
                               writer: Option[Writer[V, Tag, _]],
                               sendResultBack: Boolean): Props =
    Props(new AggregatingActor[U, V](aggregatorConfig, expectationSupplier, owner, jobPartIdentifier, writer,
      sendResultBack))
      .withDispatcher(kolibriDispatcherName)

  trait AggregatingActorCmd extends KolibriSerializable

  trait AggregatingActorEvent extends KolibriSerializable

  case object Close extends AggregatingActorCmd

  case object ProvideStateAndStop

  case object ReportResults extends AggregatingActorCmd

  case object Housekeeping

  case object ACK

}


/**
  * The actor taking care of keeping state of the aggregations.
  * To be able to flexibly react to incoming tagged results (ProcessingMessage objects) and already aggregated incoming
  * partial data, the actor takes as arguments mapper for those, e.g which allows e.g changing of tags based on the incoming
  * tags, e.g in case aggregations shall be grouped based on tags instead of grouping per single tags.
  * Also, before sending results back to the receiver of the aggregated result, a mapper is provided that is applied
  * to the final aggregation before it is send back to receiver (e.g in case certain results need to be filtered out
  * or the tags have to be changed (e.g from many to simply ALL-tag, causing only aggregation of the overall data on receiver).
  * Note that result sending overhead can be limited this way, e.g in case an aggregator state reflects a complete partial result that
  * can already be stored to persistence, while on the receiving actor we might only be interested in incorporatimg all results and
  * not the single result tags).
  *
  * @param aggregatorConfig
  * @param owner
  * @param jobPartIdentifier
  * @param writerOpt
  * @tparam U
  * @tparam V
  */
class AggregatingActor[U, V <: WithCount](val aggregatorConfig: AggregatorConfig[U, V],
                                          val expectationSupplier: () => ExecutionExpectation,
                                          val owner: ActorRef,
                                          val jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier,
                                          val writerOpt: Option[Writer[V, Tag, _]],
                                          val sendResultDataBackToOwner: Boolean)
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  val expectation: ExecutionExpectation = expectationSupplier.apply()
  expectation.init
  val aggregator: Aggregator[ProcessingMessage[U], V] = aggregatorConfig.aggregatorSupplier.apply()
  val cancellableSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.runnableExecutionActorHousekeepingInterval,
    interval = config.runnableExecutionActorHousekeepingInterval,
    receiver = self,
    message = Housekeeping)

  def sendAggregationState(receiver: ActorRef): Unit = {
    val filteredMappedData: V = aggregatorConfig.filteringMapperForResultSending.map(aggregator.aggregation)
    if (sendResultDataBackToOwner) {
      // NOTE: in cases of very big responses this doesnt seem to successfully send (probably serialize-deserialize issue)
      // of expectation, which then is null, as well as jobId for some reason, while the log message above
      // is logged correctly before, referencing expectation
      receiver ! AggregationStateWithData(filteredMappedData, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
    }
    else {
      receiver ! AggregationStateWithoutData(filteredMappedData.count, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
    }
  }

  def handleExpectationStateAndCloseIfFinished(adjustReceive: Boolean): Unit = {
    if (expectation.succeeded || expectation.failed) {
      cancellableSchedule.cancel()
      log.info(s"expectation succeeded: ${expectation.succeeded}, expectation failed: ${expectation.failed}")
      log.info(s"sending aggregation state for batch: ${jobPartIdentifier.batchNr}")

      // apply the mapper first to decide which parts of the data to be send to the result receiver
      // TODO: place fail and success counts within aggregator and then pass them separate from the data (data then doesnt need to be of type WithCount)
      sendAggregationState(owner)

      writerOpt.foreach(writer => {
        writer.write(aggregator.aggregation, StringTag(jobPartIdentifier.jobId))
      })
      if (adjustReceive) {
        context.become(closedState)
      }
    }
  }

  def handleSingleMsg(msg: ProcessingMessage[U]): Unit = {
    log.debug("received single result event: {}", msg)
    aggregator.add(aggregatorConfig.filteringSingleElementMapperForAggregator.map(msg))
    expectation.accept(msg)
    log.debug("expectation state: {}", expectation.statusDesc)
    log.debug(s"expectation: $expectation")
    handleExpectationStateAndCloseIfFinished(true)
    if (useAggregatorBackpressure) sender() ! ACK
  }

  // NOTE: we use counts on the aggregated state to be able to accept partial aggregations and still
  // keep the expectations, e.g if we receive an aggregation state incorporating 10 elements,
  // then this should fulfill receive expectations on 10 elements. We might add this to aggregation state instead
  // of requiring this on the value of type V
  def handleAggregationStateMsg(msg: AggregationState[V]): Unit = {
    msg match {
      case e: AggregationStateWithData[V] =>
        log.info("received aggregation result event with count: {}", e.data.count)
        aggregator.addAggregate(aggregatorConfig.filterAggregationMapperForAggregator.map(e.data))
      case e: AggregationStateWithoutData[V] =>
        log.info("received aggregation result event with count: {}", e.containedElementCount)
    }
    expectation.accept(msg)
    log.info("overall partial result count: {}", aggregator.aggregation.count)
    log.debug("expectation state: {}", expectation.statusDesc)
    log.debug(s"expectation: $expectation")
    handleExpectationStateAndCloseIfFinished(true)
    if (useAggregatorBackpressure) sender() ! ACK
  }

  override def receive: Receive = openState

  def openState: Receive = {
    case e: BadCorn[U] =>
      handleSingleMsg(e)
    case aggregationResult: AggregationState[V] =>
      handleAggregationStateMsg(aggregationResult)
    case result: Corn[U] =>
      handleSingleMsg(result)
    case Close =>
      log.debug("aggregator switched to closed state")
      cancellableSchedule.cancel()
      context.become(closedState)
    case ReportResults =>
      handleExpectationStateAndCloseIfFinished(true)
    case ProvideStateAndStop =>
      log.debug("Providing aggregation state and stopping aggregator")
      cancellableSchedule.cancel()
      sendAggregationState(owner)
      self ! PoisonPill
    case Housekeeping =>
      handleExpectationStateAndCloseIfFinished(adjustReceive = true)
    case ReportResults =>
      sendAggregationState(sender())
    case e =>
      log.warning("Received unmatched msg: {}", e)
  }

  def closedState: Receive = {
    case ReportResults =>
      sendAggregationState(sender())
    case _ =>
  }
}
