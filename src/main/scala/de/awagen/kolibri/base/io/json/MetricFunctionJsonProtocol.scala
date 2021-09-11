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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.CalculationResult
import de.awagen.kolibri.base.usecase.searchopt.metrics.IRMetricFunctions
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object MetricFunctionJsonProtocol extends DefaultJsonProtocol {

  implicit object MetricFunctionFormat extends JsonFormat[Function[Seq[Double], CalculationResult[Double]]] {
    override def read(json: JsValue): Function[Seq[Double], CalculationResult[Double]] = json match {
      case spray.json.JsObject(fields) =>
        val k: Int = fields("k").convertTo[Int]
        fields("type").convertTo[String] match {
          case "DCG" =>
            IRMetricFunctions.dcgAtK(k)
          case "NDCG" =>
            IRMetricFunctions.ndcgAtK(k)
          case "PRECISION" =>
            val threshold = fields("threshold").convertTo[Double]
            IRMetricFunctions.precisionAtK(k, threshold)
          case "ERR" =>
            val maxGrade = fields.get("maxGrade").map(x => x.convertTo[Double]).getOrElse(3.0)
            IRMetricFunctions.errAtK(k, maxGrade)
        }
    }

    override def write(obj: Function[Seq[Double], CalculationResult[Double]]): JsValue = """{}""".toJson
  }

}
