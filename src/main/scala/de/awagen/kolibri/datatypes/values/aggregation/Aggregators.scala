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

package de.awagen.kolibri.datatypes.values.aggregation

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableFunction2, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.RunningValue.doubleAvgRunningValue
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.reflect.runtime.universe._

object Aggregators {

  abstract class Aggregator[-U: TypeTag, V: TypeTag] extends KolibriSerializable {

    def add(sample: U): Unit

    def aggregation: V

    def addAggregate(aggregatedValue: V): Unit

  }

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

  class BasePerClassAggregator[T <: Tag: TypeTag, TT <: TaggedWithType[T] : TypeTag, V: TypeTag](aggFunc: SerializableFunction2[TT, V, V],
                                                                                                 startValueForKey: SerializableFunction1[T, V],
                                                                                                 mergeFunc: SerializableFunction2[V, V, V],
                                                                                                 keyMapFunction: SerializableFunction1[T, T]) extends Aggregator[TT, Map[T, V]] {
    val map: mutable.Map[T, V] = mutable.Map.empty

    override def add(sample: TT): Unit = {
      val keys: Set[T] = sample.getTags(AGGREGATION).map(tag => keyMapFunction.apply(tag))
      keys.foreach(x => map(x) = aggFunc.apply(sample, map.getOrElse(x, startValueForKey.apply(x))))
    }

    override def aggregation: Map[T, V] = Map(map.toSeq: _*)

    override def addAggregate(aggregatedValue: Map[T, V]): Unit = {
      aggregatedValue.foreach(x => {
        val key = keyMapFunction.apply(x._1)
        map += (key -> mergeFunc.apply(map.getOrElse(key, startValueForKey.apply(key)), x._2))
      })
    }
  }

  class TagKeyRunningDoubleAvgPerClassAggregator(keyMapFunction: SerializableFunction1[Tag, Tag]) extends BasePerClassAggregator[Tag, TaggedWithType[Tag] with DataStore[Double], AggregateValue[Double]](
    aggFunc = (x, y) => y.add(x.data),
    startValueForKey = _ => doubleAvgRunningValue(count = 0, value = 0.0),
    mergeFunc = (x, y) => x.add(y),
    keyMapFunction) {
  }

  class TagKeyRunningDoubleAvgAggregator() extends BaseAggregator[Double, AggregateValue[Double]](
    aggFunc = (x, y) => y.add(x),
    startValueGen = () => doubleAvgRunningValue(count = 0, value = 0.0),
    mergeFunc = (x, y) => x.add(y)) {
  }

  class TagKeyMetricDocumentPerClassAggregator(keyMapFunction: SerializableFunction1[Tag, Tag]) extends BasePerClassAggregator[Tag, TaggedWithType[Tag] with DataStore[MetricRow], MetricDocument[Tag]](
    aggFunc = (x, y) => {
      y.add(x.data)
      y
    },
    startValueForKey = x => MetricDocument.empty[Tag](x),
    mergeFunc = (x, y) => {
      x.add(y)
      x
    },
    keyMapFunction) {
  }

  class TagKeyMetricAggregationPerClassAggregator(keyMapFunction: SerializableFunction1[Tag, Tag]) extends Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]] {
    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    val aggregationState: MetricAggregation[Tag] = MetricAggregation.empty[Tag](keyMapFunction)

    override def add(sample: TaggedWithType[Tag] with DataStore[MetricRow]): Unit = {
      logger.debug(s"adding sample to aggregation (for keys: ${sample.getTagsForType(AGGREGATION)}: $sample")
      val keys = sample.getTagsForType(AGGREGATION)
      aggregationState.addResults(keys, sample.data)
      logger.debug(s"aggregation state is now: $aggregationState")
    }

    override def aggregation: MetricAggregation[Tag] = aggregationState

    override def addAggregate(aggregatedValue: MetricAggregation[Tag]): Unit = {
      logger.debug(s"adding aggregation to aggregation: $aggregatedValue")
      aggregationState.add(aggregatedValue)
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
    val logger: Logger = LoggerFactory.getLogger(BaseAnyAggregator.getClass)

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
