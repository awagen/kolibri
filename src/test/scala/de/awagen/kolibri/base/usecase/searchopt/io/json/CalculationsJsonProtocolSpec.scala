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

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.io.json.CalculationsJsonProtocol.{FromMapCalculationSeqBooleanToDoubleFormat, FromMapFutureCalculationSeqStringToMetricRowFormat}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, CalculationResult, FromMapCalculation, FutureCalculation}
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import spray.json._

class CalculationsJsonProtocolSpec extends UnitTestSpec {

  val IR_METRICS_FUTURE_CALCULATION: JsValue =
    """
      |{
      |"functionType": "IR_METRICS",
      |"name": "irMetrics",
      |"requestTemplateKey": "requestTemplate",
      |"productIdsKey": "productIds",
      |"judgementProvider": {
      | "type": "FILE_BASED",
      | "filename": "data/testjudgements.txt"
      |},
      |"metricsCalculation": {
      | "metrics": ["DCG_10", "NDCG_10", "PRECISION_4", "ERR"],
      | "judgementHandling": {
      |   "validations": ["EXIST_RESULTS", "EXIST_JUDGEMENTS"],
      |   "handling":  "AS_ZEROS"
      | }
      |},
      |"excludeParams": ["q"]
      |}
      |""".stripMargin.parseJson

  val FROM_MAP_SEQ_BOOLEAN_TO_DOUBLE_CALCULATION: JsValue =
    """{
      |"name": "firstTrue",
      |"dataKey": "seq_bool_key",
      |"functionType": "FIRST_TRUE"
      |}""".stripMargin.parseJson

    "FromMapFutureCalculationSeqStringToMetricRowFormat" must {

      "correctly parse FutureCalculation[WeaklyTypedMap[String], MetricRow]" in {
        val calc: FutureCalculation[WeaklyTypedMap[String], MetricRow] = IR_METRICS_FUTURE_CALCULATION.convertTo[FutureCalculation[WeaklyTypedMap[String], MetricRow]]
        calc.name mustBe "irMetrics"
      }

    }

  "FromMapCalculationSeqBooleanToDoubleFormat" must {

    "correctly parse FromMapCalculation[Seq[Boolean], Double]" in {
      val calc = FROM_MAP_SEQ_BOOLEAN_TO_DOUBLE_CALCULATION.convertTo[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]]
      calc.name mustBe "firstTrue"
    }

  }

}
