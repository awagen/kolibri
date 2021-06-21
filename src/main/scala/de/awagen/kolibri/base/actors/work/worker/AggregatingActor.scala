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
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, BadCorn, Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.ExecutionContextExecutor

object AggregatingActor {

  def props[U, V](aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V],
                  expectationSupplier: () => ExecutionExpectation,
                  owner: ActorRef,
                  jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier): Props =
    Props(new AggregatingActor[U, V](aggregatorSupplier, expectationSupplier, owner, jobPartIdentifier))

  trait AggregatingActorCmd extends KolibriSerializable

  trait AggregatingActorEvent extends KolibriSerializable

  case object Close extends AggregatingActorCmd

  case class ProvideStateAndStop(reportTo: ActorRef)

  case object ReportResults extends AggregatingActorCmd

  case object Housekeeping

}

class AggregatingActor[U, V](val aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V],
                             val expectationSupplier: () => ExecutionExpectation,
                             val owner: ActorRef,
                             val jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier)
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

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
      log.debug(s"expectation succeeded: ${expectation.succeeded}, expectation failed: ${expectation.failed}")
      log.info(s"sending aggregation state for batch: ${jobPartIdentifier.batchNr}")
      owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
      if (adjustReceive) {
        context.become(closedState)
      }
    }
  }


  override def receive: Receive = openState

  def openState: Receive = {
    case e: BadCorn[U] =>
      aggregator.add(e)
    case result: Corn[U] =>
      log.debug("received single result event: {}", result)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      aggregator.add(result)
      expectation.accept(result)
      handleExpectationStateAndCloseIfFinished(true)
    case result@Corn(aggregator: Aggregator[ProcessingMessage[U], V]) =>
      log.debug("received aggregated result event: {}", result)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      aggregator.addAggregate(aggregator.aggregation)
      expectation.accept(aggregator)
      handleExpectationStateAndCloseIfFinished(true)
    case Close =>
      log.debug("aggregator switched to closed state")
      context.become(closedState)
    case ReportResults =>
      handleExpectationStateAndCloseIfFinished(true)
    case ProvideStateAndStop(reportTo) =>
      log.debug("Providing aggregation state and stopping aggregator")
      reportTo ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
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
  }
}
