/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.execution.aggregation

import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.Aggregator
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.ValueWithCount
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.runtime.universe._

object Aggregators {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def countingAggregator(value: Int, count: Int): Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = new Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] {
    override def add(sample: TaggedWithType with DataPoint[Unit]): Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = {
      logger.info(s"adding sample to aggregator: $sample")
      countingAggregator(value + 1, count + 1)
    }

    override def aggregation: ValueWithCount[Int] = {
      logger.info("requesting current aggregation state")
      ValueWithCount(value, count)
    }

    override def addAggregate(aggregatedValue: ValueWithCount[Int]): Aggregator[TaggedWithType with DataPoint[Unit], ValueWithCount[Int]] = {
      countingAggregator(value + aggregatedValue.value, count + aggregatedValue.count)
    }
  }

  def inactiveAggregator[V: TypeTag, W <: WithCount](fixedValue: W)(implicit tag: TypeTag[W]): Aggregator[TaggedWithType with DataPoint[V], W] = new Aggregator[TaggedWithType with DataPoint[V], W] {
    override def add(sample: TaggedWithType with DataPoint[V]): Aggregator[TaggedWithType with DataPoint[V], W] = inactiveAggregator(fixedValue)

    override def aggregation: W = fixedValue

    override def addAggregate(aggregatedValue: W): Aggregator[TaggedWithType with DataPoint[V], W] = inactiveAggregator(fixedValue)
  }

}
