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


package de.awagen.kolibri.datatypes.stores.immutable

import de.awagen.kolibri.datatypes.values.MetricValue

trait MetricRecord[A, +B] {

  def getMetricsValue(key: A): Option[MetricValue[B]]

  def addMetricDontChangeCountStore[C >: B](metric: MetricValue[C]): MetricRecord[A, B]

  def addFullMetricsSampleAndIncreaseSampleCount[C >: B](metrics: MetricValue[C]*): MetricRecord[A, B]

  def addRecordAndIncreaseSampleCount[C >: B](record: MetricRecord[A, C]): MetricRecord[A, B]

  def addContextInfo(info: Map[String, Any]): MetricRecord[A, B]

  def metricNames: Seq[A]

  def metricValues: Seq[MetricValue[B]]

  def containsMetric(key: A): Boolean

}
