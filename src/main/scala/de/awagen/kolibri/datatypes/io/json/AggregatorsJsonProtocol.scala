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
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator, TagKeyMetricDocumentPerClassAggregator, TagKeyRunningDoubleAvgPerClassAggregator}
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.JsonFormat
import spray.json._

object AggregatorsJsonProtocol {

  val TYPE_PER_CLASS_DOUBLE = "perClassDouble"
  val TYPE_PER_CLASS_METRIC_ROW = "perClassMetricRow"
  val TYPE_METRIC_AGGREGATION = "metricAggregation"
  val TYPE_FIELD = "type"

  implicit object PerClassDoubleAggregatorFormat extends JsonFormat[Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]]] {

    override def read(json: JsValue): Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_FIELD) && fields(TYPE_FIELD).convertTo[String] == TYPE_PER_CLASS_DOUBLE =>
        new TagKeyRunningDoubleAvgPerClassAggregator()
      case e => throw DeserializationException(s"Expected a value from Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]] but got value $e")
    }

    override def write(obj: Aggregator[Tag, Double, Map[Tag, AggregateValue[Double]]]): JsValue = {
      s"""{"$TYPE_FIELD": "$TYPE_PER_CLASS_DOUBLE"}""".parseJson
    }
  }


  implicit object PerClassMetricRowAggregatorFormat extends JsonFormat[Aggregator[Tag, MetricRow, Map[Tag, MetricDocument[Tag]]]] {

    override def read(json: JsValue): Aggregator[Tag, MetricRow, Map[Tag, MetricDocument[Tag]]] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_FIELD) && fields(TYPE_FIELD).convertTo[String] == TYPE_PER_CLASS_METRIC_ROW =>
        new TagKeyMetricDocumentPerClassAggregator()
      case e => throw DeserializationException(s"Expected a value from Aggregator[Tag, MetricRow, Map[Tag, MetricDocument[Tag]]] but got value $e")
    }

    override def write(obj: Aggregator[Tag, MetricRow, Map[Tag, MetricDocument[Tag]]]): JsValue = {
      s"""{"$TYPE_FIELD": "$TYPE_PER_CLASS_METRIC_ROW"}""".parseJson
    }
  }


  implicit object MetricAggregationAggregatorFormat extends JsonFormat[Aggregator[Tag, MetricRow, MetricAggregation[Tag]]] {

    override def read(json: JsValue): Aggregator[Tag, MetricRow, MetricAggregation[Tag]] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_FIELD) && fields(TYPE_FIELD).convertTo[String] == TYPE_METRIC_AGGREGATION =>
        new TagKeyMetricAggregationPerClassAggregator()
      case e => throw DeserializationException(s"Expected a value from Aggregator[Tag, MetricRow, MetricAggregation[Tag]] but got value $e")
    }

    override def write(obj: Aggregator[Tag, MetricRow, MetricAggregation[Tag]]): JsValue = {
      s"""{"$TYPE_FIELD": "$TYPE_METRIC_AGGREGATION"}""".parseJson
    }
  }

}
