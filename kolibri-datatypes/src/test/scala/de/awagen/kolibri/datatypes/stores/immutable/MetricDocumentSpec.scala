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

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricsHelper.{metricsSuccess1, metricsSuccess2, metricsSuccess3, metricsSuccess4}
import de.awagen.kolibri.datatypes.stores.immutable.MetricDocument.ParamMap
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.aggregation.AggregationTestHelper.metricValueSupplier

class MetricDocumentSpec extends UnitTestSpec {

  "immutable.MetricDocument" must {

    "correctly add MetricRow state per params" in {
      // given
      val doc = MetricDocument[Tag](id = StringTag("d"), rows = Map.empty[ParamMap, MetricRow])
      // when
      var metricRow1 = MetricRow.emptyForParams(Map("p1" -> Seq("v1", "v2")))
      var metricRow2 = MetricRow.emptyForParams(Map("p1" -> Seq("v2", "v3")))
      metricRow1 = metricRow1.addMetricDontChangeCountStore(metricsSuccess1)
      metricRow1 = metricRow1.addMetricDontChangeCountStore(metricsSuccess2)
      metricRow2 = metricRow2.addMetricDontChangeCountStore(metricsSuccess3)
      metricRow2 = metricRow2.addMetricDontChangeCountStore(metricsSuccess4)
      metricRow1 = metricRow1.incrementSuccessCount()
      metricRow2 = metricRow2.incrementSuccessCount()
      val newDoc = doc.add(metricRow1).add(metricRow2)
      // then
      newDoc.rows(metricRow1.params) mustBe metricRow1
      newDoc.rows(metricRow2.params) mustBe metricRow2
    }

    "correctly add many values to doc" in {
      // given
      val doc = MetricDocument.empty[Tag](StringTag("test"))
      var newDoc = doc
      // when
      Range(0,100).foreach(_ => {
        val row = MetricRow.emptyForParams(Map("p" -> Seq("1.0")))
          .addFullMetricsSampleAndIncreaseSampleCount(metricValueSupplier.apply())
        newDoc = newDoc.add(row)
      })
      // then
      newDoc.rows.values.head.metrics("m1").biValue.value2.value mustBe Map("key1" -> Map("1" -> 100.0))
    }

  }

}
