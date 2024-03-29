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

package de.awagen.kolibri.datatypes.stores.mutable

import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument.ParamMap

import scala.collection.mutable

object MetricDocument {

  type ParamMap = Map[String, Seq[String]]

  def empty[A <: AnyRef](id: A): MetricDocument[A] = MetricDocument(id, mutable.Map.empty)

}

/**
 * MetricDocument representing a map of parameter set to MetricRow.
 * Implementation uses a mutable map; a single document will only be modified within
 * a single actor, thus single thread at a time.
 *
 * @param id
 * @param rows
 * @tparam A
 */
case class MetricDocument[+A <: AnyRef](id: A, rows: mutable.Map[ParamMap, MetricRow]) {

  private[this] var paramNames: Set[String] = rows.keySet.flatMap(x => x.keys).toSet
  private[this] var metricNames: Set[String] = rows.values.map(x => x.metricNames.toSet).fold(Set.empty[String])((y, z) => y ++ z)

  def weighted(weight: Double): MetricDocument[A] = {
    MetricDocument(id, mutable.Map(rows.map(x => (x._1, x._2.weighted(weight))).toSeq:_*))
  }

  def add(row: MetricRow): Unit = {
    rows(row.params) = rows.getOrElse(
      row.params,
      MetricRow(MetricRow.ResultCountStore(0, 0), row.params, Map.empty, row.contextInfo)
    )
      .addRecordAndIncreaseSampleCount(row)
    metricNames = metricNames ++ row.metricNames
    paramNames = paramNames ++ row.params.keySet
  }

  def add[B >: A <: AnyRef](doc: MetricDocument[B], ignoreIdDiff: Boolean = false): Unit = {
    if (!ignoreIdDiff && doc.id != id) {
      throw new IllegalArgumentException(s"trying to add document with id ${doc.id} to doc with id $id")
    }
    paramNames = paramNames ++ doc.getParamNames
    metricNames = metricNames ++ doc.getMetricNames
    doc.rows.keys.foreach {
      case e if rows.contains(e) =>
        rows(e) = rows(e).addRecordAndIncreaseSampleCount(doc.rows(e))
      case e =>
        rows(e) = doc.rows(e)
    }
  }

  def getParamNames: Set[String] = paramNames

  def getMetricNames: Set[String] = metricNames

}