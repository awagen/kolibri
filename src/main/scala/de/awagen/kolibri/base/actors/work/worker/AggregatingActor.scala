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
import de.awagen.kolibri.base.processing.classifier.Mapper.FilteringMapper
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.ExecutionContextExecutor

object AggregatingActor {

  // TODO: we might wannna have a RateExpectation, meaning elements to aggregate must be there at least every
  // given time step
  def props[U, V <: WithCount](filteringSingleElementMapperForAggregator: FilteringMapper[ProcessingMessage[U], ProcessingMessage[U]],
                               filterAggregationMapperForAggregator: FilteringMapper[V, V],
                               filteringMapperForResultSending: FilteringMapper[V, V],
                               aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V],
                               expectationSupplier: () => ExecutionExpectation,
                               owner: ActorRef,
                               jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier,
                               writer: Option[Writer[V, Tag, _]]): Props =
    Props(new AggregatingActor[U, V](filteringSingleElementMapperForAggregator, filterAggregationMapperForAggregator,
      filteringMapperForResultSending, aggregatorSupplier, expectationSupplier, owner, jobPartIdentifier, writer))
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
  * // TODO: add filtering function to single element classifier
  *
  * @param filteringSingleElementMapperForAggregator
  * @param filterAggregationMapperForAggregator
  * @param filteringMapperForResultSending
  * @param aggregatorSupplier
  * @param expectationSupplier
  * @param owner
  * @param jobPartIdentifier
  * @param writerOpt
  * @tparam U
  * @tparam V
  */
class AggregatingActor[U, V <: WithCount](val filteringSingleElementMapperForAggregator: FilteringMapper[ProcessingMessage[U], ProcessingMessage[U]],
                                          val filterAggregationMapperForAggregator: FilteringMapper[V, V],
                                          val filteringMapperForResultSending: FilteringMapper[V, V],
                                          val aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V],
                                          val expectationSupplier: () => ExecutionExpectation,
                                          val owner: ActorRef,
                                          val jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier,
                                          val writerOpt: Option[Writer[V, Tag, _]])
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  val expectation: ExecutionExpectation = expectationSupplier.apply()
  expectation.init
  val aggregator: Aggregator[ProcessingMessage[U], V] = aggregatorSupplier.apply()
  val cancellableSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.runnableExecutionActorHousekeepingInterval,
    interval = config.runnableExecutionActorHousekeepingInterval,
    receiver = self,
    message = Housekeeping)

  def handleExpectationStateAndCloseIfFinished(adjustReceive: Boolean): Unit = {
    if (expectation.succeeded || expectation.failed) {
      cancellableSchedule.cancel()
      log.info(s"expectation succeeded: ${expectation.succeeded}, expectation failed: ${expectation.failed}")
      log.info(s"sending aggregation state for batch: ${jobPartIdentifier.batchNr}")

      // apply the mapper first to decide which parts of the data to be send to the result receiver
      val filteredMappedData = filteringMapperForResultSending.map(aggregator.aggregation)
      owner ! AggregationState(filteredMappedData, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)

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
    aggregator.add(filteringSingleElementMapperForAggregator.map(msg))
    expectation.accept(msg)
    log.debug("expectation state: {}", expectation.statusDesc)
    log.debug(s"expectation: $expectation")
    handleExpectationStateAndCloseIfFinished(true)
    if (useAggregatorBackpressure) sender() ! ACK
  }

  def handleAggregationStateMsg(msg: AggregationState[V]): Unit = {
    log.info("received aggregation result event with count: {}", msg.data.count)
    aggregator.addAggregate(filterAggregationMapperForAggregator.map(msg.data))
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
      owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
      self ! PoisonPill
    case Housekeeping =>
      handleExpectationStateAndCloseIfFinished(adjustReceive = true)
    case ReportResults =>
      sender() ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
    case e =>
      log.warning("Received unmatched msg: {}", e)
  }

  def closedState: Receive = {
    case ReportResults =>
      sender() ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
    case _ =>
  }
}
