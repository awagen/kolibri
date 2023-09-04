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


package de.awagen.kolibri.datatypes.values.aggregation.mutable

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableFunction2, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.RunningValues.doubleAvgRunningValue
import de.awagen.kolibri.datatypes.values.aggregation.immutable.AggregateValue
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.reflect.runtime.universe._
import scala.util.Random

object Aggregators {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  abstract class Aggregator[-U, V] extends KolibriSerializable {

    def add(sample: U): Unit

    def aggregation: V

    def addAggregate(aggregatedValue: V): Unit

  }

  /**
   * Aggregator taking start value generator, aggregation and merge functions to define the aggregation behavior
   *
   * @param aggFunc       - Given sample of type U and aggregation of type V, generate new value of type V
   * @param startValueGen - supplier of start value of aggregation, type V
   * @param mergeFunc     - merge function of aggregations, taking two values of type V and providing new value of type V
   * @tparam U - type of single data points
   * @tparam V - type of aggregation
   */
  class BaseAggregator[U: TypeTag, V: TypeTag](aggFunc: SerializableFunction2[U, V, V], startValueGen: SerializableSupplier[V], mergeFunc: SerializableFunction2[V, V, V]) extends Aggregator[U, V] {
    var value: V = startValueGen.apply()

    override def add(sample: U): Unit = {
      value = aggFunc.apply(sample, value)
    }

    override def aggregation: V = value

    override def addAggregate(aggregatedValue: V): Unit = {
      value = mergeFunc.apply(value, aggregatedValue)
    }
  }

  /**
   *
   * @param aggFunc          - aggregation function, taking single data point of type TT, aggregated value of type V, providing new aggregation of type V
   * @param startValueForKey - function giving an initial aggregation value, given a Tag
   * @param mergeFunc        - merge function of aggregation values
   * @param keyMapFunction   - function mapping value of type Tag to value of type Tag (in case the Tag shall not be mapped, just use identity)
   * @tparam TT - type of single data point, needs to extend TaggedWithType
   * @tparam V  - type of aggregation
   */
  class BasePerClassAggregator[TT <: TaggedWithType : TypeTag, V: TypeTag](aggFunc: SerializableFunction2[TT, V, V],
                                                                           startValueForKey: SerializableFunction1[Tag, V],
                                                                           mergeFunc: SerializableFunction2[V, V, V],
                                                                           keyMapFunction: SerializableFunction1[Tag, Tag]) extends Aggregator[TT, Map[Tag, V]] {
    val map: mutable.Map[Tag, V] = mutable.Map.empty

    override def add(sample: TT): Unit = {
      val keys: Set[Tag] = sample.getTags(AGGREGATION).map(tag => keyMapFunction.apply(tag))
      keys.foreach(x => map(x) = aggFunc.apply(sample, map.getOrElse(x, startValueForKey.apply(x))))
    }

    override def aggregation: Map[Tag, V] = Map(map.toSeq: _*)

    override def addAggregate(aggregatedValue: Map[Tag, V]): Unit = {
      aggregatedValue.foreach(x => {
        val key = keyMapFunction.apply(x._1)
        map += (key -> mergeFunc.apply(map.getOrElse(key, startValueForKey.apply(key)), x._2))
      })
    }
  }

  /**
   * Aggregator that aggregates (running) averages per class
   *
   * @param keyMapFunction - function mapping value of type Tag to value of type Tag (in case the Tag shall not be mapped, just use identity)
   */
  class TagKeyRunningDoubleAvgPerClassAggregator(keyMapFunction: SerializableFunction1[Tag, Tag]) extends BasePerClassAggregator[TaggedWithType with DataPoint[Double], AggregateValue[Double]](
    aggFunc = (x, y) => y.add(x),
    startValueForKey = _ => doubleAvgRunningValue(weightedCount = 0.0, count = 0, value = 0.0),
    mergeFunc = (x, y) => x.add(y),
    keyMapFunction) {
  }

  /**
   * Aggregator aggregating to (running) averages overall
   */
  class TagKeyRunningDoubleAvgAggregator() extends BaseAggregator[DataPoint[Double], AggregateValue[Double]](
    aggFunc = (x, y) => y.add(x),
    startValueGen = () => doubleAvgRunningValue(weightedCount = 0.0, count = 0, value = 0.0),
    mergeFunc = (x, y) => x.add(y)) {
  }

  /**
   * In case of a mapping function that alters original tags, ignoreIdDiff would need to be true to avoid conflicts.
   * Setting this attribute to true enables aggregating data for the original tag to data for the mapped tag.
   *
   * @param keyMapFunction - mapping function of Tag of input sample data
   * @param ignoreIdDiff   - determines whether merging aggregations for different IDs is allowed
   */
  class TagKeyMetricDocumentPerClassAggregator(keyMapFunction: SerializableFunction1[Tag, Tag], ignoreIdDiff: Boolean = false) extends BasePerClassAggregator[TaggedWithType with DataPoint[MetricRow], MetricDocument[Tag]](
    aggFunc = (x, y) => {
      y.add(x.data)
      y
    },
    startValueForKey = x => MetricDocument.empty[Tag](x),
    mergeFunc = (x, y) => {
      x.add(y, ignoreIdDiff = ignoreIdDiff)
      x
    },
    keyMapFunction) {}


  class MultiAggregator[-U, V](aggregatorSupplier: () => Aggregator[U, V], numAggregators: Int) extends Aggregator[U, V] {

    private val aggregators: Seq[Aggregator[U, V]] = Range(0, numAggregators).map(_ => aggregatorSupplier())

    private def pickRandomAggregator: Aggregator[U, V] = {
      val random = new Random()
      aggregators(random.between(0, aggregators.length))
    }

    override def add(sample: U): Unit = {
      pickRandomAggregator.add(sample)
    }

    override def aggregation: V = {
      val fullAggregation = aggregators.fold(aggregatorSupplier())((aggOld, newAgg) => {
        aggOld.addAggregate(newAgg.aggregation)
        aggOld
      })
      fullAggregation.aggregation
    }

    override def addAggregate(aggregatedValue: V): Unit = {
      pickRandomAggregator.addAggregate(aggregatedValue)
    }
  }


  /**
   * In case of a mapping function that alters original tags, ignoreIdDiff would need to be true to avoid conflicts.
   * Setting this attribute to true enables aggregating data for the original tag to data for the mapped tag.
   *
   * @param keyMapFunction - mapping function of Tag of input sample data
   * @param ignoreIdDiff   - determines whether merging aggregations for different IDs is allowed
   */
  class TagKeyMetricAggregationPerClassAggregator(keyMapFunction: SerializableFunction1[Tag, Tag], ignoreIdDiff: Boolean = false) extends Aggregator[TaggedWithType with DataPoint[MetricRow], MetricAggregation[Tag]] {
    val aggregationState: MetricAggregation[Tag] = MetricAggregation.empty[Tag](keyMapFunction)

    override def add(sample: TaggedWithType with DataPoint[MetricRow]): Unit = {
      logger.debug(s"adding sample to aggregation (for keys: ${sample.getTagsForType(AGGREGATION)}: $sample")
      val keys = sample.getTagsForType(AGGREGATION)
      aggregationState.addResults(keys, sample.data)
      logger.debug(s"aggregation state is now: $aggregationState")
    }

    override def aggregation: MetricAggregation[Tag] = aggregationState

    override def addAggregate(aggregatedValue: MetricAggregation[Tag]): Unit = {
      logger.debug(s"adding aggregation to aggregation: $aggregatedValue")
      aggregationState.add(aggregatedValue, ignoreIdDiff)
      logger.debug(s"aggregation state is now: $aggregationState")
    }
  }

  /**
   * Wrapper for typed aggregators to accept any message and aggregate only those matching the type
   *
   * @param aggregator
   * @tparam T
   * @tparam V
   */
  case class BaseAnyAggregator[T: TypeTag, V: TypeTag](aggregator: Aggregator[T, V]) extends Aggregator[Any, V] {
    override def add(sample: Any): Unit = {
      try {
        val data: T = sample.asInstanceOf[T]
        aggregator.add(data)
      }
      catch {
        case _: Throwable =>
          logger.warn(s"Could not add sample $sample as element of type ${aggregator.getClass}")
      }
    }

    override def aggregation: V = aggregator.aggregation

    override def addAggregate(aggregatedValue: V): Unit = {
      aggregator.addAggregate(aggregatedValue)
    }
  }


}
