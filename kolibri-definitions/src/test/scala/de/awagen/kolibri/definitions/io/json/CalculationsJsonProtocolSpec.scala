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

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, TwoInCalculation}
import de.awagen.kolibri.definitions.directives.RetrievalDirective
import de.awagen.kolibri.definitions.resources.{ResourceProvider, RetrievalError}
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import spray.json._

class CalculationsJsonProtocolSpec extends UnitTestSpec {

  // NOTE: currently dummy provider, adjust to proper one when testing calculation that requests resources
  val calculationProtocol: CalculationsJsonProtocol = CalculationsJsonProtocol(new ResourceProvider {
    override def getResource[T](directive: RetrievalDirective.RetrievalDirective[T]): Either[RetrievalError[T], T] = {
      null
    }
  })

  import calculationProtocol._

  val IR_METRICS_CALCULATION: JsValue =
    """
      |{
      |"type": "IR_METRICS",
      |"queryParamName": "q",
      |"productIdsKey": "productIds",
      |"judgementsResource": {
      | "resourceType": "JUDGEMENT_PROVIDER",
      | "identifier": "ident1"
      |},
      |"metricsCalculation": {
      | "metrics": [
      | {"name": "DCG_10", "function": {"type": "DCG", "k": 10}},
      | {"name": "NDCG_10", "function": {"type": "NDCG", "k": 10}},
      | {"name": "PRECISION_4", "function": {"type": "PRECISION", "k": 4, "threshold": 0.1}},
      | {"name": "ERR", "function": {"type": "ERR", "k": 4}}
      | ],
      | "judgementHandling": {
      |   "validations": ["EXIST_RESULTS", "EXIST_JUDGEMENTS"],
      |   "handling":  "AS_ZEROS"
      | }
      |}
      |}
      |""".stripMargin.parseJson

  val FROM_MAP_SEQ_BOOLEAN_TO_DOUBLE_CALCULATION: JsValue =
    """{
      |"name": "firstTrue",
      |"dataKey": "seq_bool_key",
      |"type": "FIRST_TRUE"
      |}""".stripMargin.parseJson


  val FROM_TWO_MAPS_JACCARD_CALCULATION: JsValue =
    """{
      |"type": "JACCARD_SIMILARITY",
      |"name": "jaccard",
      |"data1Key": "data1",
      |"data2Key": "data2"
      |}""".stripMargin.parseJson



  "FromMapCalculationsDoubleFormat" must {
    "correctly parse Calculation[WeaklyTypedMap[String], Any]" in {
      val calc: Calculation[WeaklyTypedMap[String], Any] = IR_METRICS_CALCULATION.convertTo[Calculation[WeaklyTypedMap[String], Any]]
      (Set("DCG_10", "NDCG_10", "PRECISION_4", "ERR") diff calc.names).isEmpty mustBe true
    }
  }

  "FromMapCalculationSeqBooleanToDoubleFormat" must {
    "correctly parse FromMapCalculation[Seq[Boolean], Any]" in {
      val calc = FROM_MAP_SEQ_BOOLEAN_TO_DOUBLE_CALCULATION.convertTo[Calculation[WeaklyTypedMap[String], Any]]
      calc.names mustBe Set("firstTrue")
    }
  }

  "FromTwoMapsCalculationFormat" must {
    "correctly parse jaccard calculation" in {
      val calc = FROM_TWO_MAPS_JACCARD_CALCULATION.convertTo[TwoInCalculation[WeaklyTypedMap[String], WeaklyTypedMap[String], Any]]
      calc.names mustBe Set("jaccard")
    }
  }

}
