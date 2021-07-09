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
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricsHelper.{metricRecord1, metricRecord2, metricRecord3, metricRecord4}
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec


class MetricAggregationSpec extends UnitTestSpec {

  "OverallAggregationSpec" must {

    "correctly add all metric results" in {
      //given
      val aggregation = MetricAggregation.empty[Tag](identity)
      //when
      aggregation.addResults(Set(StringTag("test1")), metricRecord1)
      aggregation.addResults(Set(StringTag("test2")), metricRecord2)
      aggregation.addResults(Set(StringTag("test2")), metricRecord3)
      aggregation.addResults(Set(StringTag("test3")), metricRecord4)
      val r1: MetricRow = aggregation.aggregationStateMap(StringTag("test1")).rows(Map.empty)
      val r2: MetricRow = aggregation.aggregationStateMap(StringTag("test2")).rows(Map.empty)
      val r3: MetricRow = aggregation.aggregationStateMap(StringTag("test3")).rows(Map.empty)
      val expectedR1: MetricRow = metricRecord1
      val expectedR2: MetricRow = metricRecord2.addRecord(metricRecord3)
      val expectedR3: MetricRow = metricRecord4
      //then
      aggregation.aggregationStateMap.size mustBe 3
      aggregation.aggregationStateMap.keySet.contains(StringTag("test1")) mustBe true
      aggregation.aggregationStateMap.keySet.contains(StringTag("test2")) mustBe true
      aggregation.aggregationStateMap.keySet.contains(StringTag("test3")) mustBe true
      r1 mustBe expectedR1
      r2 mustBe expectedR2
      r3 mustBe expectedR3
    }

    "correctly add other MetricAggregation" in {
      //given
      val aggregation1 = MetricAggregation.empty[Tag](identity)
      val aggregation2 = MetricAggregation.empty[Tag](identity)
      //when
      aggregation1.addResults(Set(StringTag("test1")), metricRecord1)
      aggregation2.addResults(Set(StringTag("test2")), metricRecord2)
      aggregation2.addResults(Set(StringTag("test2")), metricRecord3)
      val combined = MetricAggregation.empty[Tag](identity)
      combined.add(aggregation1)
      combined.add(aggregation2)
      val r1: MetricDocument[Tag] = combined.aggregationStateMap(StringTag("test1"))
      val r2: MetricDocument[Tag] = combined.aggregationStateMap(StringTag("test2"))
      val expectedR1: MetricDocument[Tag] = MetricDocument.empty[Tag](StringTag("test1"))
      expectedR1.add(metricRecord1)
      val expectedR2: MetricDocument[Tag] = MetricDocument.empty[Tag](StringTag("test2"))
      expectedR2.add(metricRecord2)
      expectedR2.add(metricRecord3)
      //then
      combined.aggregationStateMap.size mustBe 2
      combined.aggregationStateMap.keySet.contains(StringTag("test1")) mustBe true
      combined.aggregationStateMap.keySet.contains(StringTag("test2")) mustBe true
      combined.aggregationStateMap.keySet.contains(StringTag("test3")) mustBe false
      r1 mustBe expectedR1
      r2 mustBe expectedR2
    }

  }

}
