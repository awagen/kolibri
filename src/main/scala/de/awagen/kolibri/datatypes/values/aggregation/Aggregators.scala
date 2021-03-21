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
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableFunction2, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.RunningValue.doubleAvgRunningValue
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.reflect.runtime.universe._

object Aggregators {

  abstract class Aggregator[T: TypeTag, U: TypeTag, V: TypeTag] extends KolibriSerializable {

    val keyType: TypeTag[T] = implicitly[TypeTag[T]]
    val singleElementType: TypeTag[U] = implicitly[TypeTag[U]]
    val aggregationType: TypeTag[V] = implicitly[TypeTag[V]]

    def add(keys: Set[T], sample: U): Unit

    def aggregation: V

    def add(aggregatedValue: V): Unit

  }

  class BaseAggregator[T: TypeTag, U: TypeTag, V: TypeTag](aggFunc: SerializableFunction2[U, V, V], startValueGen: SerializableSupplier[V], mergeFunc: SerializableFunction2[V, V, V]) extends Aggregator[T, U, V] {
    var value: V = startValueGen.get()

    override def add(keys: Set[T], sample: U): Unit = {
      value = aggFunc.apply(sample, value)
    }

    override def aggregation: V = value

    override def add(aggregatedValue: V): Unit = {
      value = mergeFunc.apply(value, aggregatedValue)
    }
  }

  class BasePerClassAggregator[T: TypeTag, TT: TypeTag, V: TypeTag](aggFunc: SerializableFunction2[TT, V, V], startValueForKey: SerializableFunction1[T, V], mergeFunc: SerializableFunction2[V, V, V]) extends Aggregator[T, TT, Map[T, V]] {
    val map: mutable.Map[T, V] = mutable.Map.empty

    override def add(keys: Set[T], sample: TT): Unit = {
      keys.foreach(x => map(x) = aggFunc.apply(sample, map.getOrElse(x, startValueForKey.apply(x))))
    }

    override def aggregation: Map[T, V] = Map(map.toSeq: _*)

    override def add(aggregatedValue: Map[T, V]): Unit = {
      aggregatedValue.foreach(x => map += (x._1 -> mergeFunc.apply(map.getOrElse(x._1, startValueForKey.apply(x._1)), x._2)))
    }
  }

  class TagKeyRunningDoubleAvgPerClassAggregator() extends BasePerClassAggregator[Tag, Double, AggregateValue[Double]](
    aggFunc = (x, y) => y.add(x),
    startValueForKey = _ => doubleAvgRunningValue(count = 0, value = 0.0),
    mergeFunc = (x, y) => x.add(y)) {
  }

  class TagKeyRunningDoubleAvgAggregator() extends BaseAggregator[Tag, Double, AggregateValue[Double]](
    aggFunc = (x, y) => y.add(x),
    startValueGen = () => doubleAvgRunningValue(count = 0, value = 0.0),
    mergeFunc = (x, y) => x.add(y)) {
  }

  class TagKeyMetricDocumentPerClassAggregator() extends BasePerClassAggregator[Tag, MetricRow, MetricDocument[Tag]](
    aggFunc = (x, y) => {
      y.add(x)
      y
    },
    startValueForKey = x => MetricDocument.empty[Tag](x),
    mergeFunc = (x, y) => {
      x.add(y)
      x
    }) {
  }

  class TagKeyMetricAggregationPerClassAggregator() extends Aggregator[Tag, MetricRow, MetricAggregation[Tag]] {
    val aggregationState: MetricAggregation[Tag] = MetricAggregation.empty[Tag]

    override def add(keys: Set[Tag], sample: MetricRow): Unit = {
      aggregationState.addResults(keys, sample)
    }

    override def aggregation: MetricAggregation[Tag] = aggregationState

    override def add(aggregatedValue: MetricAggregation[Tag]): Unit = {
      aggregationState.add(aggregatedValue)
    }
  }

  /**
    * Wrapper for typed aggregators to accept any message and aggregate only those matching the type
    *
    * @param aggregator
    * @tparam T
    * @tparam V
    */
  case class BaseAnyAggregator[T: TypeTag, V: TypeTag](aggregator: Aggregator[Tag, T, V]) extends Aggregator[Tag, Any, V] {
    val logger: Logger = LoggerFactory.getLogger(BaseAnyAggregator.getClass)

    override def add(keys: Set[Tag], sample: Any): Unit = {
      try {
        val data: T = sample.asInstanceOf[T]
        aggregator.add(keys, data)
      }
      catch {
        case _: Throwable =>
          logger.warn(s"Could not add sample $sample as element of type ${aggregator.singleElementType.tpe.typeSymbol.name.toString}")
      }
    }

    override def aggregation: V = aggregator.aggregation

    override def add(aggregatedValue: V): Unit = {
      aggregator.add(aggregatedValue)
    }
  }


}
