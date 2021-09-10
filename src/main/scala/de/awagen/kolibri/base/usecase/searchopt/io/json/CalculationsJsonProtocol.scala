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


package de.awagen.kolibri.base.usecase.searchopt.io.json

import de.awagen.kolibri.base.usecase.searchopt.io.json.JudgementProviderFactoryJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.io.json.MetricsCalculationJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, CalculationResult, FromMapCalculation, FromMapFutureCalculation, FutureCalculation, JudgementBasedMetricsCalculation}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Functions.{booleanPrecision, countValues, findFirstValue}
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}


object CalculationsJsonProtocol extends DefaultJsonProtocol {

  implicit object FromMapFutureCalculationSeqStringToMetricRowFormat extends JsonFormat[FutureCalculation[WeaklyTypedMap[String], MetricRow]] {
    override def read(json: JsValue): FutureCalculation[WeaklyTypedMap[String], MetricRow] = json match {
      case spray.json.JsObject(fields) =>
        fields("functionType").convertTo[String] match {
          case "IR_METRICS" =>
            val name = fields("name").convertTo[String]
            val queryParamName = fields("queryParamName").convertTo[String]
            val requestTemplateKey = fields("requestTemplateKey").convertTo[String]
            val productIdsKey = fields("productIdsKey").convertTo[String]
            val judgementProviderFactory = fields("judgementProvider").convertTo[JudgementProviderFactory[Double]]
            val metricsCalculation = fields("metricsCalculation").convertTo[MetricsCalculation]
            val excludeParamsFromMetricRow = fields("excludeParams").convertTo[Seq[String]]
            val calculation = JudgementBasedMetricsCalculation(
              name,
              queryParamName,
              requestTemplateKey,
              productIdsKey,
              judgementProviderFactory,
              metricsCalculation,
              excludeParamsFromMetricRow
            )
            FromMapFutureCalculation(name, metricsCalculation.metrics.map(x => x.name).toSet, calculation)
        }
    }

    // TODO
    override def write(obj: FutureCalculation[WeaklyTypedMap[String], MetricRow]): JsValue = """{}""".toJson

  }

  implicit object FromMapCalculationSeqBooleanToDoubleFormat extends JsonFormat[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]] {
    override def read(json: JsValue): FromMapCalculation[Seq[Boolean], Double] = json match {
      case spray.json.JsObject(fields) =>
        val metricName: String = fields("name").convertTo[String]
        val dataKey: String = fields("dataKey").convertTo[String]
        fields("functionType").convertTo[String] match {
          case "FIRST_TRUE" =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, findFirstValue(true))
          case "FIRST_FALSE" =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, findFirstValue(false))
          case "TRUE_COUNT" =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, countValues(true))
          case "FALSE_COUNT" =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, countValues(false))
          case "BINARY_PRECISION_TRUE_AS_YES" =>
            val k: Int = fields("k").convertTo[Int]
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, booleanPrecision(useTrue = true, k))
          case "BINARY_PRECISION_FALSE_AS_YES" =>
            val k: Int = fields("k").convertTo[Int]
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, booleanPrecision(useTrue = false, k))
        }
    }

    // TODO
    override def write(obj: Calculation[WeaklyTypedMap[String], CalculationResult[Double]]): JsValue = """{}""".toJson
  }

}
