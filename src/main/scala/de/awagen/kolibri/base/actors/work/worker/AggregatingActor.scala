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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import de.awagen.kolibri.base.actors.work.worker.AggregatingActor._
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationState, Corn}
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.ExecutionContextExecutor

object AggregatingActor {

  def props[U, V](aggregatorSupplier: SerializableSupplier[Aggregator[Tag, U, V]],
                  expectationSupplier: SerializableSupplier[ExecutionExpectation],
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

class AggregatingActor[U, V](val aggregatorSupplier: SerializableSupplier[Aggregator[Tag, U, V]],
                             val expectationSupplier: SerializableSupplier[ExecutionExpectation],
                             val owner: ActorRef,
                             val jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier)
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val expectation: ExecutionExpectation = expectationSupplier.get()
  expectation.init
  val aggregator: Aggregator[Tag, U, V] = aggregatorSupplier.get()

  def handleExpectationStateAndCloseIfFinished(adjustReceive: Boolean): Unit = {
    if (expectation.succeeded || expectation.failed) {
      log.debug(s"expectation succeeded: ${expectation.succeeded}, expectation failed: ${expectation.failed}")
      owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
      if (adjustReceive) self ! Close
    }
  }

  context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.runnableExecutionActorHousekeepingInterval,
    interval = config.runnableExecutionActorHousekeepingInterval,
    receiver = self,
    message = Housekeeping)

  override def receive: Receive = openState

  def openState: Receive = {
    case result@Corn(value: U) =>
      log.debug("received single result event: {}", result)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      aggregator.add(result.getTagsForType(AGGREGATION), value)
      expectation.accept(result)
      handleExpectationStateAndCloseIfFinished(true)
    case result@Corn(aggregator: Aggregator[Tag, U, V]) =>
      log.debug("received aggregated result event: {}", result)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      aggregator.add(aggregator.aggregation)
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
      owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
    case e =>
      log.warning("Received unmatched msg: {}", e)
  }

  def closedState: Receive = {
    case ReportResults =>
      owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
  }
}
