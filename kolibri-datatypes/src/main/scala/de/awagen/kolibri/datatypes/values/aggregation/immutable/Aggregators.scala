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


package de.awagen.kolibri.datatypes.values.aggregation.immutable

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.immutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.immutable.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableFunction2, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.RunningValues.doubleAvgRunningValue
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.runtime.universe._

object Aggregators {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  abstract class Aggregator[U: TypeTag, V: TypeTag] extends KolibriSerializable {

    def add(sample: U): Aggregator[U, V]

    def aggregation: V

    def addAggregate(aggregatedValue: V): Aggregator[U, V]

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
    val value: V = startValueGen.apply()

    override def add(sample: U): Aggregator[U, V] = {
      new BaseAggregator(aggFunc, () => aggFunc.apply(sample, value), mergeFunc)
    }

    override def aggregation: V = value

    override def addAggregate(aggregatedValue: V): Aggregator[U, V] = {
      new BaseAggregator(aggFunc, () => mergeFunc.apply(value, aggregatedValue), mergeFunc)
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
                                                                           keyMapFunction: SerializableFunction1[Tag, Tag],
                                                                           seenTags: Set[Tag] = Set.empty) extends Aggregator[TT, Map[Tag, V]] {
    override def add(sample: TT): Aggregator[TT, Map[Tag, V]] = {
      val keys: Set[Tag] = sample.getTags(AGGREGATION).map(tag => keyMapFunction.apply(tag))
      val startValueFunc: SerializableFunction1[Tag, V] = {
        val mapping: Map[Tag, V] = keys.map(x => x -> aggFunc.apply(sample, startValueForKey.apply(x))).toMap
        x => mapping.getOrElse(x, startValueForKey.apply(x))
      }
      new BasePerClassAggregator(aggFunc, startValueFunc, mergeFunc, keyMapFunction, seenTags ++ keys)
    }

    override def aggregation: Map[Tag, V] = seenTags.map(tag => tag -> startValueForKey(tag)).toMap

    override def addAggregate(aggregatedValue: Map[Tag, V]): Aggregator[TT, Map[Tag, V]] = {
      val startValueFunc: SerializableFunction1[Tag, V] = {
        val mapping: Map[Tag, V] = aggregatedValue.map(x => {
          val key = keyMapFunction.apply(x._1)
          val startValue = startValueForKey(key)
          key -> mergeFunc.apply(startValue, x._2)
        })
        x => mapping.getOrElse(x, startValueForKey.apply(x))
      }
      new BasePerClassAggregator(aggFunc, startValueFunc, mergeFunc, keyMapFunction,
        seenTags ++ aggregatedValue.map(x => keyMapFunction(x._1)))
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
    keyMapFunction,
    Set.empty) {
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

  /**
   * In case MetricAggregation contains a mapping function that alters original tags, ignoreIdDiff would need to be true to avoid conflicts.
   * Setting this attribute to true enables aggregating data for the original tag to data for the mapped tag.
   *
   * @param aggregationState - current aggregation state. MetricAggregation might apply a mapping to tags of input sample data
   * @param ignoreIdDiff   - determines whether merging aggregations for different IDs is allowed
   */
  class TagKeyMetricAggregationPerClassAggregator(aggregationState: MetricAggregation[Tag] = MetricAggregation.empty[Tag](identity), ignoreIdDiff: Boolean = false) extends Aggregator[TaggedWithType with DataPoint[MetricRow], MetricAggregation[Tag]] {

    override def add(sample: TaggedWithType with DataPoint[MetricRow]): Aggregator[TaggedWithType with DataPoint[MetricRow], MetricAggregation[Tag]] = {
      logger.debug(s"adding sample to aggregation (for keys: ${sample.getTagsForType(AGGREGATION)}: $sample")
      val keys = sample.getTagsForType(AGGREGATION)
      val newState = aggregationState.addResults(keys, sample.data)
      logger.debug(s"aggregation state is now: $newState")
      new TagKeyMetricAggregationPerClassAggregator(newState, ignoreIdDiff)
    }

    override def aggregation: MetricAggregation[Tag] = aggregationState

    override def addAggregate(aggregatedValue: MetricAggregation[Tag]): Aggregator[TaggedWithType with DataPoint[MetricRow], MetricAggregation[Tag]] = {
      logger.debug(s"adding aggregation to aggregation: $aggregatedValue")
      val newState = aggregationState.add(aggregatedValue, ignoreIdDiff)
      logger.debug(s"aggregation state is now: $newState")
      new TagKeyMetricAggregationPerClassAggregator(newState, ignoreIdDiff)
    }
  }

  /**
   * Wrapper for typed aggregators to accept any message and aggregate only those matching the type
   *
   * @param aggregator
   * @tparam T
   * @tparam V
   */
  case class BaseAnyAggregator[T: TypeTag, V: TypeTag](aggregator: Aggregator[Any, V]) extends Aggregator[Any, V] {
    override def add(sample: Any): Aggregator[Any, V] = {
      try {
        val data: T = sample.asInstanceOf[T]
        aggregator.add(data)
      }
      catch {
        case _: Throwable =>
          logger.warn(s"Could not add sample $sample as element of type ${aggregator.getClass}")
          aggregator
      }
    }

    override def aggregation: V = aggregator.aggregation

    override def addAggregate(aggregatedValue: V): Aggregator[Any, V] = {
      aggregator.addAggregate(aggregatedValue)
    }
  }


}
