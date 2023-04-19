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


package de.awagen.kolibri.fleet.akka.actors.work.worker

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import de.awagen.kolibri.definitions.processing.JobPartIdentifiers
import de.awagen.kolibri.fleet.akka.actors.work.worker.AggregatingActor._
import de.awagen.kolibri.definitions.processing.ProcessingMessages._
import de.awagen.kolibri.fleet.akka.config.AppProperties.config
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.{kolibriDispatcherName, useAggregatorBackpressure}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import de.awagen.kolibri.definitions.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.definitions.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.ExecutionContextExecutor

object AggregatingActor {

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

  // the wrapper for the final result to report back (e.g this should be used for the final result
  // after applying filters (if applicable) on the data to be sent back and taking the
  // sendResultDataBackToOwner flag into account
  case class FinalReportState(state: AggregationState[_]) extends AggregatingActorEvent

  // temporary/final report state without data for owner to enable owner to answer state requests (e.g AggregatingActor itself
  // will receive many messages in mailbox, so avoiding additional requests)
  case class StateUpdateWithoutData(state: AggregationStateWithoutData[_], isFinal: Boolean) extends AggregatingActorEvent

  case object Close extends AggregatingActorCmd

  case object ProvideStateAndStop extends AggregatingActorCmd

  case object Housekeeping extends AggregatingActorCmd

  case object ACK extends AggregatingActorEvent

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
  * @param aggregatorConfig - config providing aggregator and optional filters for distinct granularities
  * @param expectationSupplier - supplier of an expectation on the results to be received
  * @param owner - the owner of this actor, e.g used for scheduled result reporting
  * @param jobPartIdentifier - the part identifier for a job
  * @param writerOpt - optional writer for the aggregation result
  * @param sendResultDataBackToOwner - flag to indicate whether the full aggregation result shall be reported as result, otherwise only the summary without data will be sent
  * @tparam U - single result type
  * @tparam V - aggregation type
  * @return
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
    initialDelay = config.aggregatingActorHousekeepingInterval,
    interval = config.aggregatingActorHousekeepingInterval,
    receiver = self,
    message = Housekeeping)

  val cancellableResultReport: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.aggregatingActorStateSendingInterval,
    interval = config.aggregatingActorStateSendingInterval)(() => sendAggregationStateWithoutData(owner))

  override def postStop(): Unit = {
    cancellableSchedule.cancel()
    cancellableResultReport.cancel()
    super.postStop()
  }

  def sendAggregationStateWithoutData(sendTo: ActorRef): Unit = {
    log.debug("sending state update to runnable actor")
    val isFinal: Boolean = expectation.failed || expectation.succeeded
    sendTo ! StateUpdateWithoutData(aggregationStateWithoutData(aggregator.aggregation), isFinal)
  }

  def sendFinalReportState(sendTo: ActorRef): Unit = {
    sendTo ! FinalReportState(aggregationStateFiltered(sendResultDataBackToOwner))
  }

  /**
    * Based on the passed aggregation data, compose AggregationState[V] without data
    * @param aggregationData - the current data state of the aggregation
    * @return
    */
  def aggregationStateWithoutData(aggregationData: V): AggregationStateWithoutData[V] = {
    AggregationStateWithoutData(aggregationData.count, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
  }

  /**
    * Based on the passed aggregation data, compose AggregationState[V] with data
    * @param aggregationData - the current data state of the aggregation
    * @return
    */
  def aggregationStateWithData(aggregationData: V): AggregationStateWithData[V] = {
    AggregationStateWithData(aggregationData, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
  }

  /**
    * In case some result filtering before sending is applied (see aggregatorConfig),
    * apply this and based on this filtered data provide resulting AggregationState
    * @param withData - flag to indicate whether data shall be included or not. Use true-setting with caution, as this
    *                 might cause trouble in case of big amounts of data (e.g in case serialization is needed, that is if the requesting
    *                 actor does not live on the same node. This is the case e.g for the final result
    *                 returning here, as this will be reported back to JobManager. In those cases storing result
    *                 and just returning an aggregation state without the actual data to the JobManager is the way to go)
    * @return
    */
  def aggregationStateFiltered(withData: Boolean): AggregationState[V] = {
    val filteredMappedData: V = aggregatorConfig.filteringMapperForResultSending.map(aggregator.aggregation)
    if (withData) {
      // NOTE: in cases of very big responses this doesnt seem to successfully send if serialization needed (probably serialize-deserialize issue)
      // of expectation, which then is null, as well as jobId for some reason, while the log message above
      // is logged correctly before, referencing expectation
      aggregationStateWithData(filteredMappedData)
    }
    else {
      aggregationStateWithoutData(filteredMappedData)
    }
  }

  def handleExpectationStateAndCloseIfFinished(adjustReceive: Boolean): Unit = {
    if (expectation.succeeded || expectation.failed) {
      cancellableSchedule.cancel()
      cancellableResultReport.cancel()
      log.info(s"expectation succeeded: ${expectation.succeeded}, expectation failed: ${expectation.failed}")
      log.info(s"sending aggregation state for batch: ${jobPartIdentifier.batchNr}")

      // send state without data based on full, unfiltered data
      sendAggregationStateWithoutData(owner)
      // applying mapper first to decide which parts of the data to be send to the result receiver
      // and then sending result marking it as final result
      sendFinalReportState(owner)

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
        log.debug("received aggregation result event with count: {}", e.data.count)
        aggregator.addAggregate(aggregatorConfig.filterAggregationMapperForAggregator.map(e.data))
      case e: AggregationStateWithoutData[V] =>
        log.debug("received aggregation result event with count: {}", e.containedElementCount)
    }
    expectation.accept(msg)
    log.debug("overall partial result count: {}", aggregator.aggregation.count)
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
      cancellableResultReport.cancel()
      context.become(closedState)
    case ProvideStateAndStop =>
      log.debug("Providing aggregation state and stopping aggregator")
      cancellableSchedule.cancel()
      cancellableResultReport.cancel()
      sendAggregationStateWithoutData(sender())
      // filter results if needed and send result
      sendFinalReportState(sender())
      self ! PoisonPill
    case Housekeeping =>
      handleExpectationStateAndCloseIfFinished(adjustReceive = true)
    case e =>
      log.warning("Received unmatched msg: {}", e)
  }

  def closedState: Receive = {
    case _ =>
  }
}
