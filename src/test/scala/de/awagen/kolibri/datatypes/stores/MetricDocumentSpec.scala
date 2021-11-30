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


package de.awagen.kolibri.datatypes.stores

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricsHelper._
import de.awagen.kolibri.datatypes.stores.MetricDocument.ParamMap
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

import scala.collection.mutable

class MetricDocumentSpec extends UnitTestSpec {

  "MetricDocument" must {

    "correctly add MetricRow state per params" in {
      // given
      val doc = MetricDocument[Tag](id = StringTag("d"), rows = mutable.Map.empty[ParamMap, MetricRow])
      // when
      var metricRow1 = MetricRow.emptyForParams(Map("p1" -> Seq("v1", "v2")))
      var metricRow2 = MetricRow.emptyForParams(Map("p1" -> Seq("v2", "v3")))
      metricRow1 = metricRow1.addMetricDontChangeCountStore(metricsSuccess1)
      metricRow1 = metricRow1.addMetricDontChangeCountStore(metricsSuccess2)
      metricRow2 = metricRow2.addMetricDontChangeCountStore(metricsSuccess3)
      metricRow2 = metricRow2.addMetricDontChangeCountStore(metricsSuccess4)
      metricRow1.countStore.incrementSuccessCount()
      metricRow2.countStore.incrementSuccessCount()
      doc.add(metricRow1)
      doc.add(metricRow2)
      // then
      doc.rows(metricRow1.params) mustBe metricRow1
      doc.rows(metricRow2.params) mustBe metricRow2
    }

  }

}
