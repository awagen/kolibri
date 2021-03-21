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

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricsHelper.{metricRecord1, metricRecord2, metricRecord3, metricRecord4}
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.utils.MathUtils
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricDocumentPerClassAggregator, TagKeyRunningDoubleAvgPerClassAggregator}

class AggregatorsSpec extends UnitTestSpec {

  "TagKeyRunningDoubleAvgAggregator" must {

    "correctly add values" in {
      // given
      val aggregator: Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]] = new TagKeyRunningDoubleAvgPerClassAggregator()
      // when
      aggregator.add(Set(StringTag("t1"), StringTag("t2")), 1)
      aggregator.add(Set(StringTag("t1"), StringTag("t2")), 3)
      aggregator.add(Set(StringTag("t2")), 2)
      aggregator.add(Set(StringTag("t1")), 0)
      // then
      val value1: AggregateValue[Double] = aggregator.aggregation(StringTag("t1"))
      val value2: AggregateValue[Double] = aggregator.aggregation(StringTag("t2"))
      aggregator.aggregation.keys.size mustBe 2
      MathUtils.equalWithPrecision(4.0 / 3.0, value1.value, 0.0001f) mustBe true
      value1.count mustBe 3
      MathUtils.equalWithPrecision(6.0 / 3.0, value2.value, 0.0001f) mustBe true
      value2.count mustBe 3
    }

    "correctly add other AggregateValues" in {
      // given
      val aggregator1: Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]] = new TagKeyRunningDoubleAvgPerClassAggregator()
      val aggregator2: Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]] = new TagKeyRunningDoubleAvgPerClassAggregator()
      // when
      aggregator1.add(Set(StringTag("t1"), StringTag("t2")), 1)
      aggregator1.add(Set(StringTag("t1"), StringTag("t2")), 3)
      aggregator1.add(Set(StringTag("t2")), 2)
      aggregator1.add(Set(StringTag("t1")), 0)
      aggregator2.add(Set(StringTag("t1"), StringTag("t2")), 4)
      aggregator2.add(Set(StringTag("t1"), StringTag("t2")), 0)
      aggregator2.add(Set(StringTag("t2")), 1)
      aggregator2.add(Set(StringTag("t1")), 1)
      aggregator1.add(aggregator2.aggregation)
      // then
      val value1: AggregateValue[Double] = aggregator1.aggregation(StringTag("t1"))
      val value2: AggregateValue[Double] = aggregator1.aggregation(StringTag("t2"))
      aggregator1.aggregation.keys.size mustBe 2
      MathUtils.equalWithPrecision(9.0 / 6.0, value1.value, 0.0001f) mustBe true
      value1.count mustBe 6
      MathUtils.equalWithPrecision(11.0 / 6.0, value2.value, 0.0001f) mustBe true
      value2.count mustBe 6
    }

  }

  "TagKeyMetricDocumentAggregator" must {

    "correctly add values" in {
      // given
      val aggregator = new TagKeyMetricDocumentPerClassAggregator()
      // when
      aggregator.add(Set(StringTag("test1")), metricRecord1)
      aggregator.add(Set(StringTag("test2")), metricRecord2)
      aggregator.add(Set(StringTag("test2")), metricRecord3)
      aggregator.add(Set(StringTag("test3")), metricRecord4)
      val r1: MetricRow = aggregator.aggregation(StringTag("test1")).rows(Map.empty)
      val r2: MetricRow = aggregator.aggregation(StringTag("test2")).rows(Map.empty)
      val r3: MetricRow = aggregator.aggregation(StringTag("test3")).rows(Map.empty)
      val expectedR1: MetricRow = metricRecord1
      val expectedR2: MetricRow = metricRecord2.addRecord(metricRecord3)
      val expectedR3: MetricRow = metricRecord4
      // then
      aggregator.aggregation.size mustBe 3
      aggregator.aggregation.keySet.contains(StringTag("test1")) mustBe true
      aggregator.aggregation.keySet.contains(StringTag("test2")) mustBe true
      aggregator.aggregation.keySet.contains(StringTag("test3")) mustBe true
      r1 mustBe expectedR1
      r2 mustBe expectedR2
      r3 mustBe expectedR3
    }

    "correctly add other values" in {
      // given
      val aggregator1 = new TagKeyMetricDocumentPerClassAggregator()
      val aggregator2 = new TagKeyMetricDocumentPerClassAggregator()
      // when
      aggregator1.add(Set(StringTag("test1")), metricRecord1)
      aggregator2.add(Set(StringTag("test2")), metricRecord2)
      aggregator2.add(Set(StringTag("test2")), metricRecord3)
      aggregator1.add(aggregator2.aggregation)

      val r1: MetricDocument[Tag] = aggregator1.aggregation(StringTag("test1"))
      val r2: MetricDocument[Tag] = aggregator1.aggregation(StringTag("test2"))
      val expectedR1: MetricDocument[Tag] = MetricDocument.empty[Tag](StringTag("test1"))
      expectedR1.add(metricRecord1)
      val expectedR2: MetricDocument[Tag] = MetricDocument.empty[Tag](StringTag("test2"))
      expectedR2.add(metricRecord2)
      expectedR2.add(metricRecord3)
      //then
      aggregator1.aggregation.size mustBe 2
      aggregator1.aggregation.keySet.contains(StringTag("test1")) mustBe true
      aggregator1.aggregation.keySet.contains(StringTag("test2")) mustBe true
      aggregator1.aggregation.keySet.contains(StringTag("test3")) mustBe false
      r1 mustBe expectedR1
      r2 mustBe expectedR2
    }

  }

}
