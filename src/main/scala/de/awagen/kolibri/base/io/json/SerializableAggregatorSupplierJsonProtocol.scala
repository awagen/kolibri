package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, BaseAnyAggregator, TagKeyMetricAggregationPerClassAggregator, TagKeyMetricDocumentPerClassAggregator}
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat}

import scala.reflect.runtime.universe.typeOf

object SerializableAggregatorSupplierJsonProtocol extends DefaultJsonProtocol {

  val METRIC_ROW_TYPE: String = "METRIC_ROW_AGGREGATOR"

  implicit object metricAggregationFormat extends JsonFormat[SerializableSupplier[Aggregator[Tag, Any, MetricAggregation[Tag]]]] {
    override def read(json: JsValue): SerializableSupplier[Aggregator[Tag, Any, MetricAggregation[Tag]]] = json match {
      case spray.json.JsObject(fields) if fields.contains("type") && fields("type").convertTo[String] == METRIC_ROW_TYPE =>
        new SerializableSupplier[Aggregator[Tag, Any, MetricAggregation[Tag]]] {
          override def get(): Aggregator[Tag, Any, MetricAggregation[Tag]] = BaseAnyAggregator(new TagKeyMetricAggregationPerClassAggregator())
        }
    }

    override def write(obj: SerializableSupplier[Aggregator[Tag, Any, MetricAggregation[Tag]]]): JsValue = obj match {
      case e: SerializableSupplier[Aggregator[Tag, Any, MetricAggregation[Tag]]] if e.get().singleElementType.tpe == typeOf[MetricRow] =>
        var fieldMap: Map[String, JsValue] = JsString(obj.toString).asJsObject.fields
        fieldMap = fieldMap + ("type" -> JsString(METRIC_ROW_TYPE))
        JsString(JsObject(fieldMap).toString())
      case e =>
        JsString(e.toString)
    }
  }

}
