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


package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator, TagKeyMetricDocumentPerClassAggregator, TagKeyRunningDoubleAvgPerClassAggregator}

class AggregatorsJsonProtocolSpec extends UnitTestSpec {

  "AggregatorsJsonProtocol" must {
    import spray.json._
    import AggregatorsJsonProtocol._

    "correctly parse Aggregator[TaggedWithType[Tag] with DataStore[Double], Map[Tag, AggregateValue[Double]]]" in {
      // given
      val json = """{"type": "perClassDouble"}""".parseJson
      // when
      val aggregator: Aggregator[TaggedWithType[Tag] with DataStore[Double], Map[Tag, AggregateValue[Double]]] = json.convertTo[Aggregator[TaggedWithType[Tag] with DataStore[Double], Map[Tag, AggregateValue[Double]]]]
      // then
      aggregator.isInstanceOf[TagKeyRunningDoubleAvgPerClassAggregator]
    }

    "correctly parse Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], Map[Tag, MetricDocument[Tag]]]" in {
      // given
      val json = """{"type": "perClassMetricRow"}""".parseJson
      // when
      val aggregator: Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], Map[Tag, MetricDocument[Tag]]] = json.convertTo[Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], Map[Tag, MetricDocument[Tag]]]]
      // then
      aggregator.isInstanceOf[TagKeyMetricDocumentPerClassAggregator]
    }

    "correctly parse Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]]" in {
      // given
      val json = """{"type": "metricAggregation"}""".parseJson
      // when
      val aggregator: Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]] = json.convertTo[Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]]]
      // then
      aggregator.isInstanceOf[TagKeyMetricAggregationPerClassAggregator]
    }

  }

}
