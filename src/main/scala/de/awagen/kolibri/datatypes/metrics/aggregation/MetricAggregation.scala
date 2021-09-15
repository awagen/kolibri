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

package de.awagen.kolibri.datatypes.metrics.aggregation

import de.awagen.kolibri.datatypes.functions.GeneralSerializableFunctions._
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.types.Types.WithCount

import scala.collection.mutable


object MetricAggregation {

  def empty[A <: AnyRef](keyMapFunction: SerializableFunction1[A, A]): MetricAggregation[A] = MetricAggregation[A](mutable.Map.empty, keyMapFunction)

  def combineAggregates[A](agg1: MetricRow, agg2: MetricRow): MetricRow = {
    MetricRow.empty.addRecordAndIncreaseSampleCount(agg1).addRecordAndIncreaseSampleCount(agg2)
  }

}


/**
  * MetricAggregation that keeps track of full MetricDocuments for keys of defined type.
  * Each key stands for a separate aggregation, which can be used for selectively aggregating subsets of results
  *
  * @param aggregationStateMap - map with key = key of defined type A, value = MetricDocument, which maps a ParamMap to
  *                            a MetricRow, which carries all relevant parameters and corresponding metrics
  * @param keyMapFunction      - optional function to map result keys to before adding to aggregation. E.g can be used in case
  *                            all incoming results shall only be aggregated under a single "ALL" aggregation instead of
  *                            keeping track of distinct results per key
  * @tparam A - type of the keys that describe the aggregation groups
  */
case class MetricAggregation[A <: AnyRef](aggregationStateMap: mutable.Map[A, MetricDocument[A]] = mutable.Map.empty[A, MetricDocument[A]],
                                          keyMapFunction: SerializableFunction1[A, A] = identity) extends WithCount {

  private[this] var aggregatedElementsCount: Int = _

  override def count: Int = aggregatedElementsCount

  def addResults(tags: Set[A], record: MetricRow): Unit = {
    val mappedTags = tags.map(tag => keyMapFunction.apply(tag))
    mappedTags.foreach(x =>
      if (aggregationStateMap.contains(x)) {
        aggregationStateMap(x).add(record)
      }
      else {
        aggregationStateMap(x) = MetricDocument.empty[A](x)
        aggregationStateMap(x).add(record)
      }
    )
    aggregatedElementsCount += 1
  }

  def add(aggregation: MetricAggregation[A], ignoreIdDiff: Boolean = false): Unit = {
    val originalKeys = aggregation.aggregationStateMap.keySet.toSeq
    val mappedKeys = originalKeys.map(key => keyMapFunction.apply(key))
    originalKeys.indices.foreach {
      case e if aggregationStateMap.keySet.contains(mappedKeys(e)) =>
        aggregationStateMap(mappedKeys(e)).add(aggregation.aggregationStateMap(originalKeys(e)), ignoreIdDiff = ignoreIdDiff)
      case e =>
        val newDoc = MetricDocument.empty[A](mappedKeys(e))
        newDoc.add(aggregation.aggregationStateMap(originalKeys(e)), ignoreIdDiff = ignoreIdDiff)
        aggregationStateMap(mappedKeys(e)) = newDoc
    }
    aggregatedElementsCount += aggregation.count
  }

}
