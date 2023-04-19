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

import akka.NotUsed
import akka.actor.{ActorContext, ActorRef, Props}
import akka.pattern.ask
import akka.stream.ActorAttributes
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{AggregationStateWithData, BadCorn, ProcessingMessage}
import de.awagen.kolibri.definitions.processing.consume.AggregatorConfigurations.AggregatorConfig
import de.awagen.kolibri.definitions.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import de.awagen.kolibri.fleet.akka.actors.decider.Deciders.allResumeDecider
import de.awagen.kolibri.fleet.akka.config.AppProperties.config
import de.awagen.kolibri.fleet.akka.config.AppProperties.config._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ActorRunnableUtils {

  val logger: Logger = LoggerFactory.getLogger(ActorRunnableUtils.getClass)

  /**
   * Note that using the aggregating flow requires that the elements are
   * actually aggregatable. This would not be the case if the graph this flow
   * is used in runs elements belonging to different aggregations (e.g batches).
   * This can be resolved in the graph definition by applying a grouping
   * before utilizing this flow though.
   */
  def groupingAggregationFlow[U, V1, Y <: WithCount](jobId: String,
                                                     batchNr: Int,
                                                     aggregatorConfig: AggregatorConfig[V1, Y],
                                                     expectationGenerator: Int => ExecutionExpectation,
                                                     elementExtractFunc: U => ProcessingMessage[V1],
                                                     aggregatingActorExtractFunc: U => Option[ActorRef]): Flow[U, Any, NotUsed] = {
    Flow.fromFunction[U, U](identity)
      .groupedWithin(resultElementGroupingCount, resultElementGroupingInterval)
      .mapAsync[Any](aggregatorResultReceiveParallelism)(messages => {
        val aggregatingActorOpt = aggregatingActorExtractFunc(messages.head)
        val aggregator: Aggregator[ProcessingMessage[V1], Y] = aggregatorConfig.aggregatorSupplier.apply()
        messages.foreach(element => aggregator.add(elementExtractFunc(element)))
        val aggState = AggregationStateWithData(aggregator.aggregation, jobId, batchNr, expectationGenerator.apply(messages.size))
        aggregatingActorOpt.map(aggregatingActor => {
          if (useAggregatorBackpressure) {
            implicit val timeout: Timeout = Timeout(10 seconds)
            aggregatingActor ? aggState
          }
          else {
            aggregatingActor ! aggState
            Future.successful[Any](())
          }
        }).getOrElse({
          logger.warn(s"No actor sink available in context, which will usually" +
            s"cause results to be computed but not handled, so you might wanna stop execution and fix this")
          Future.successful[Any](())
        })
      }).withAttributes(ActorAttributes.supervisionStrategy(allResumeDecider))
  }

  def singleElementAggregatorFlow[U, V1](elementExtractFunc: U => ProcessingMessage[V1], aggregatingActorExtractFunc: U => Option[ActorRef]): Flow[U, Any, NotUsed] = {
    if (useAggregatorBackpressure) {
      implicit val timeout: Timeout = Timeout(10 seconds)
      Flow.fromFunction[U, U](identity)
        .mapAsync[Any](aggregatorResultReceiveParallelism)(e => {
          aggregatingActorExtractFunc.apply(e).map(aggregatingActor => {
            aggregatingActor ? elementExtractFunc.apply(e)
          }).getOrElse({
            logger.warn(s"No actor sink available in context, which will usually" +
              s"cause results to be computed but not handled, so you might wanna stop execution and fix this")
            Future.successful[Any](())
          })
        })
        .withAttributes(ActorAttributes.supervisionStrategy(allResumeDecider))
    }
    else {
      Flow.fromFunction(x => {
        aggregatingActorExtractFunc.apply(x).map(aggregatingActor => {
          aggregatingActor ! elementExtractFunc(x)
          Future.successful[Any](())
        }).getOrElse({
          logger.warn(s"No actor sink available in context, which will usually" +
            s"cause results to be computed but not handled, so you might wanna stop execution and fix this")
          Future.successful[Any](())
        })
      })
    }
  }

  def sendResultToActorFlowFunction[U, V1, Y <: WithCount](jobId: String, batchNr: Int, aggregatorConfig: AggregatorConfig[V1, Y], expectationGenerator: Int => ExecutionExpectation, elementExtractFunc: U => ProcessingMessage[V1], aggregatingActorExtractFunc: U => Option[ActorRef]): Flow[U, Any, NotUsed] =  {
    if (useResultElementGrouping) groupingAggregationFlow[U, V1, Y](jobId, batchNr, aggregatorConfig, expectationGenerator, elementExtractFunc, aggregatingActorExtractFunc) else singleElementAggregatorFlow[U, V1](elementExtractFunc, aggregatingActorExtractFunc)
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

}
