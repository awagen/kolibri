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


package de.awagen.kolibri.datatypes.stores.immutable

import de.awagen.kolibri.datatypes.stores.immutable.MetricDocument.ParamMap

object MetricDocument {

  type ParamMap = Map[String, Seq[String]]

  def empty[A <: AnyRef](id: A): MetricDocument[A] = MetricDocument(id, Map.empty)

}

/**
 * MetricDocument representing a map of parameter set to MetricRow.
 * Immutable Implementation.
 *
 * @param id
 * @param rows
 * @tparam A
 */
case class MetricDocument[+A <: AnyRef](id: A, rows: Map[ParamMap, MetricRow]) {

  private[this] val paramNames: Set[String] = rows.keySet.flatMap(x => x.keys)
  private[this] val metricNames: Set[String] = rows.values.map(x => x.metricNames.toSet).fold(Set.empty[String])((y, z) => y ++ z)

  def weighted(weight: Double): MetricDocument[A] = {
    MetricDocument(id, Map(rows.map(x => (x._1, x._2.weighted(weight))).toSeq: _*))
  }

  def add(row: MetricRow): MetricDocument[A] = {
    val newMap = rows + (row.params -> rows.getOrElse(
      row.params,
      MetricRow(MetricRow.ResultCountStore(0, 0), row.params, Map.empty, row.contextInfo)
    ).addRecordAndIncreaseSampleCount(row))
    MetricDocument(id, newMap)
  }

  def add[B >: A <: AnyRef](doc: MetricDocument[B], ignoreIdDiff: Boolean = false): MetricDocument[B] = {
    if (!ignoreIdDiff && doc.id != id) {
      throw new IllegalArgumentException(s"trying to add document with id ${doc.id} to doc with id $id")
    }
    doc.rows.keys.foldLeft(this)((oldState, paramMap) => {
      if (oldState.rows.contains(paramMap)) {
        val newMap = this.rows + (paramMap -> oldState.rows(paramMap).addRecordAndIncreaseSampleCount(doc.rows(paramMap)))
        MetricDocument(id, newMap)
      }
      else {
        val newMap = this.rows + (paramMap -> doc.rows(paramMap))
        MetricDocument(id, newMap)
      }
    })
  }

  def getParamNames: Set[String] = paramNames

  def getMetricNames: Set[String] = metricNames

}



