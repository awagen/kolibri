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

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricsHelper._
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.aggregation.AggregationTestHelper.metricRowSupplier


class MetricRowSpec extends UnitTestSpec {

  val metricsValue1: MetricValue[Double] = createMetricsCalculationSuccess("mName1", 1.5)
  val metricsValue2: MetricValue[Double] = createMetricsCalculationSuccess("mName2", 1.3)
  val metricsValue3: MetricValue[Double] = createMetricsCalculationSuccess("mName3", 2.3)


  "MetricStore" must {

    "correctly add new value" in {
      // given
      var store: MetricRow = MetricRow.empty
      // when
      store = store.addMetricDontChangeCountStore(metricsValue1)
      // then
      store.getMetricsValue("mName1").get mustBe metricsValue1
      store.metrics.keys mustBe Set("mName1")
      store.metricValues mustBe Seq(metricsValue1)
    }

    "correctly add multiple new values" in {
      // given
      var store: MetricRow = MetricRow.empty
      // when
      store = store.addFullMetricsSampleAndIncreaseSampleCount(metricsValue1, metricsValue2, metricsValue3)
      // then
      store.getMetricsValue("mName1").get mustBe metricsValue1
      store.getMetricsValue("mName2").get mustBe metricsValue2
      store.getMetricsValue("mName3").get mustBe metricsValue3
      store.metrics.keys mustBe Set("mName1", "mName2", "mName3")
      store.metricValues mustBe Seq(metricsValue1, metricsValue2, metricsValue3)
    }

    "correctly add other store" in {
      // given
      var store1: MetricRow = MetricRow.empty
      var store2: MetricRow = MetricRow.empty
      store1 = store1.addMetricDontChangeCountStore(metricsValue1)
      store2 = store2.addMetricDontChangeCountStore(metricsValue3)
      // when
      val fullStore: MetricRow = store1.addRecordAndIncreaseSampleCount(store2)
      // then
      fullStore.getMetricsValue("mName1").get mustBe metricsValue1
      fullStore.getMetricsValue("mName3").get mustBe metricsValue3
      fullStore.metrics.keys mustBe Set("mName1", "mName3")
      fullStore.metricValues mustBe Seq(metricsValue1, metricsValue3)
    }

    "correctly add many values to MetricRow" in {
      // given
      var resultRow = MetricRow.emptyForParams(Map("p" -> Seq("1.0")))
      // when
      Range(0,100).foreach(_ => {
        resultRow = resultRow.addRecordAndIncreaseSampleCount(metricRowSupplier.apply())
      })
      // then
      resultRow.metricValues.head.biValue.value2.value mustBe Map("key1" -> Map("1" -> 100.0))
    }



  }

}
