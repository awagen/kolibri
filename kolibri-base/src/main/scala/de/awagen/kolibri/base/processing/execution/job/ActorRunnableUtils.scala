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

import akka.{Done, NotUsed}
import akka.actor.{ActorContext, ActorRef, Props}
import akka.stream.{ActorAttributes, KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.Timeout
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationStateWithData, BadCorn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppProperties.config.{aggregatorResultReceiveParallelism, resultElementGroupingCount, resultElementGroupingInterval, useAggregatorBackpressure, useResultElementGrouping}
import de.awagen.kolibri.base.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.base.processing.decider.Deciders.allResumeDecider
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import de.awagen.kolibri.base.config.AppProperties.config
import de.awagen.kolibri.base.processing.failure.TaskFailType

import scala.util.{Failure, Success}

object ActorRunnableUtils {

  /**
   * Note that using the aggregating flow requires that the elements are
   * actually aggregatable. This would not be the case if the graph this flow
   * is used in runs elements belonging to different aggregations (e.g batches).
   * This can be resolved in the graph definition by applying a grouping
   * before utilizing this flow though.
   */
  def groupingAggregationFlow[V1, Y <: WithCount](jobId: String,
                                                  batchNr: Int,
                                                  aggregatingActor: ActorRef,
                                                  aggregatorConfig: AggregatorConfig[V1, Y],
                                                  expectationGenerator: Int => ExecutionExpectation
                                                 ): Flow[ProcessingMessage[V1], Any, NotUsed] = {
    Flow.fromFunction[ProcessingMessage[V1], ProcessingMessage[V1]](identity)
      .groupedWithin(resultElementGroupingCount, resultElementGroupingInterval)
      .mapAsync[Any](aggregatorResultReceiveParallelism)(messages => {
        val aggregator: Aggregator[ProcessingMessage[V1], Y] = aggregatorConfig.aggregatorSupplier.apply()
        messages.foreach(element => aggregator.add(element))
        val aggState = AggregationStateWithData(aggregator.aggregation, jobId, batchNr, expectationGenerator.apply(messages.size))
        if (useAggregatorBackpressure) {
          implicit val timeout: Timeout = Timeout(10 seconds)
          aggregatingActor ? aggState
        }
        else {
          aggregatingActor ! aggState
          Future.successful[Any](())
        }
      }).withAttributes(ActorAttributes.supervisionStrategy(allResumeDecider))
  }

  def singleElementAggregatorFlow[V1](aggregatingActor: ActorRef): Flow[ProcessingMessage[V1], Any, NotUsed] = {
    if (useAggregatorBackpressure) {
      implicit val timeout: Timeout = Timeout(10 seconds)
      Flow.fromFunction[ProcessingMessage[V1], ProcessingMessage[V1]](identity)
        .mapAsync[Any](aggregatorResultReceiveParallelism)(e => aggregatingActor ? e)
        .withAttributes(ActorAttributes.supervisionStrategy(allResumeDecider))
    }
    else {
      Flow.fromFunction(x => {
        aggregatingActor ! x
        Future.successful[Any](())
      })
    }
  }

  def actorSinkFunction[V1, Y <: WithCount](jobId: String, batchNr: Int, aggregatorConfig: AggregatorConfig[V1, Y], expectationGenerator: Int => ExecutionExpectation): SerializableFunction1[ActorRef, Sink[ProcessingMessage[V1], Future[Done]]] = new SerializableFunction1[ActorRef, Sink[ProcessingMessage[V1], Future[Done]]] {

    override def apply(actorRef: ActorRef): Sink[ProcessingMessage[V1], Future[Done]] = {
      val flow = if (useResultElementGrouping) groupingAggregationFlow(jobId, batchNr, actorRef, aggregatorConfig, expectationGenerator) else singleElementAggregatorFlow(actorRef)
      flow.toMat(Sink.foreach[Any](_ => ()))(Keep.right)
    }
  }

  /**
   * If actor props are passed for processing the elements after application of transformer flow, the elements are
   * sent there, otherwise the element is kept as is and V = V1
   */
  def sendToSeparateActorFlow[V, V1](processingActorProps: Option[Props], waitTimePerElement: FiniteDuration)(implicit actorContext: ActorContext, ec: ExecutionContext): Flow[ProcessingMessage[V], ProcessingMessage[V1], NotUsed] = {
    processingActorProps.map(props => {
      Flow.fromFunction[ProcessingMessage[V], ProcessingMessage[V]](identity)
        .mapAsyncUnordered[ProcessingMessage[V1]](config.requestParallelism)(x => {
          val sendToActor: ActorRef = actorContext.actorOf(props)
          implicit val timeout: Timeout = Timeout(waitTimePerElement)
          val responseFuture: Future[Any] = sendToActor ? x
          val mappedResponseFuture: Future[ProcessingMessage[V1]] = responseFuture.transform({
            case Success(value: ProcessingMessage[V1]) => Success(value)
            case Failure(exception) => Success[ProcessingMessage[V1]](BadCorn(TaskFailType.FailedByException(exception)))
            case e =>
              Success(BadCorn(TaskFailType.UnknownResponseClass(e.getClass.getName)))
          })
          mappedResponseFuture
        }).withAttributes(ActorAttributes.supervisionStrategy(allResumeDecider))
    }).getOrElse(Flow.fromFunction[ProcessingMessage[V], ProcessingMessage[V1]](x => x.asInstanceOf[ProcessingMessage[V1]]))
  }

  def getKillSwitch[V1]: Flow[ProcessingMessage[V1], ProcessingMessage[V1], UniqueKillSwitch] = {
    Flow.fromGraph(KillSwitches.single[ProcessingMessage[V1]])
  }

}
