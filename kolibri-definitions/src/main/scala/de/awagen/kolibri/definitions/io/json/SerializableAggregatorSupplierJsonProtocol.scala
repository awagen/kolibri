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

package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.datatypes.functions.GeneralSerializableFunctions._
import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator}
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat}

object SerializableAggregatorSupplierJsonProtocol extends DefaultJsonProtocol {

  val METRIC_ROW_TYPE: String = "METRIC_ROW_AGGREGATOR"

  implicit object metricAggregationFormat extends JsonFormat[SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]]] {
    override def read(json: JsValue): SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]] = json match {
      case spray.json.JsObject(fields) if fields.contains("type") && fields("type").convertTo[String] == METRIC_ROW_TYPE =>
        new SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]] {
          override def apply(): Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]] = new TagKeyMetricAggregationPerClassAggregator(identity)
        }
    }

    override def write(obj: SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]]): JsValue = obj match {
      case e: SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]] =>
        var fieldMap: Map[String, JsValue] = JsString(obj.toString).asJsObject.fields
        fieldMap = fieldMap + ("type" -> JsString(METRIC_ROW_TYPE))
        JsString(JsObject(fieldMap).toString())
      case e =>
        JsString(e.toString)
    }
  }

}
