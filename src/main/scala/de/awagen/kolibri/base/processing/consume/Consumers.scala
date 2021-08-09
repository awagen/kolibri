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


package de.awagen.kolibri.base.processing.consume

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationStateWithData, AggregationStateWithoutData, ProcessingMessage}
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import org.slf4j.{Logger, LoggerFactory}

object Consumers {

  trait ExecutionConsumer[T] extends KolibriSerializable {

    val applyFunc: PartialFunction[Any, Unit]
    val expectation: ExecutionExpectation

    def aggregation: T

    def hasFinished: Boolean

    def wasSuccessful: Boolean

    def hasFailed: Boolean

    def wrapUp: Unit

  }


  /**
    *
    * @param jobId       : the job id
    * @param expectation : expectation state
    * @param aggregator  : aggregation state
    * @param writer      : writer to be used to persist the aggregation result
    * @tparam T : type of data contained in the ProcessingMessages that reflect single results
    * @tparam U : type of the actual aggregation
    */
  case class BaseExecutionConsumer[T, U](jobId: String,
                                         expectation: ExecutionExpectation,
                                         aggregator: Aggregator[ProcessingMessage[T], U],
                                         writer: Writer[U, Tag, _]) extends ExecutionConsumer[U] {
    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    var wrappedUp: Boolean = false

    override val applyFunc: PartialFunction[Any, Unit] = {
      case _ if wrappedUp =>
        logger.warn("Consumer already in wrappedUp state, ignoring new element")
      case e: AggregationStateWithData[U] =>
        expectation.accept(e)
        aggregator.addAggregate(e.data)
        if (hasFinished) wrapUp
      case e: AggregationStateWithoutData[U] =>
        expectation.accept(e)
        if (hasFinished) wrapUp
      case e: ProcessingMessage[T] if e.data.isInstanceOf[T] =>
        expectation.accept(e)
        aggregator.add(e)
        if (hasFinished) wrapUp
    }

    override def hasFinished: Boolean = expectation.failed || expectation.succeeded

    override def wasSuccessful: Boolean = expectation.succeeded

    override def hasFailed: Boolean = expectation.failed

    override def wrapUp: Unit = {
      if (!wrappedUp) {
        logger.info("wrapping up execution consumer")
        writer.write(aggregator.aggregation, StringTag(jobId))
        wrappedUp = true
      }
    }

    override def aggregation: U = aggregator.aggregation
  }

}
