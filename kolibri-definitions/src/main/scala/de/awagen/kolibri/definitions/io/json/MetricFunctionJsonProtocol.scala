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

import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.MetricType.MetricType
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.IRMetricFunctions
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementInfo
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.Calculations.ComputeResult
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object MetricFunctionJsonProtocol extends DefaultJsonProtocol with WithStructDef {

  case class MetricFunction(metricType: MetricType, k: Int, calc: SerializableFunction1[JudgementInfo, ComputeResult[Double]])

  object MetricType extends Enumeration {
    type MetricType = Value

    val NDCG, DCG, PRECISION, RECALL, ERR = Value
  }

  val TYPE_VALUE_PRECISION = "PRECISION"
  val TYPE_VALUE_RECALL = "RECALL"
  val TYPE_VALUE_DCG = "DCG"
  val TYPE_VALUE_NDCG = "NDCG"
  val TYPE_VALUE_ERR = "ERR"
  val K_KEY = "k"
  val TYPE_KEY = "type"
  val THRESHOLD_KEY = "threshold"
  val MAX_GRADE_KEY = "maxGrade"


  implicit object MetricFunctionFormat extends JsonFormat[MetricFunction] {
    override def read(json: JsValue): MetricFunction = json match {
      case spray.json.JsObject(fields) =>
        val k: Int = fields(K_KEY).convertTo[Int]
        fields(TYPE_KEY).convertTo[String] match {
          case TYPE_VALUE_DCG =>
            MetricFunction(
              MetricType.DCG,
              k,
              IRMetricFunctions.dcgAtK(k)
            )
          case TYPE_VALUE_NDCG =>
            MetricFunction(
              MetricType.NDCG,
              k,
              IRMetricFunctions.ndcgAtK(k)
            )
          case TYPE_VALUE_PRECISION =>
            val threshold = fields(THRESHOLD_KEY).convertTo[Double]
            MetricFunction(
              MetricType.PRECISION,
              k,
              IRMetricFunctions.precisionAtK(k, threshold)
            )
          case TYPE_VALUE_RECALL =>
            val threshold = fields(THRESHOLD_KEY).convertTo[Double]
            MetricFunction(
              MetricType.RECALL,
              k,
              IRMetricFunctions.recallAtK(k, threshold)
            )
          case TYPE_VALUE_ERR =>
            val maxGrade = fields.get(MAX_GRADE_KEY).map(x => x.convertTo[Double]).getOrElse(3.0)
            MetricFunction(
              MetricType.ERR,
              k,
              IRMetricFunctions.errAtK(k, maxGrade)
            )
        }
    }

    override def write(obj: MetricFunction): JsValue = """{}""".toJson
  }

  override def structDef: JsonStructDefs.StructDef[_] = {
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(StringConstantStructDef(K_KEY), IntMinMaxStructDef(1, 1000), required = true),
        FieldDef(StringConstantStructDef(TYPE_KEY), StringChoiceStructDef(
          Seq(
            TYPE_VALUE_DCG,
            TYPE_VALUE_NDCG,
            TYPE_VALUE_PRECISION,
            TYPE_VALUE_RECALL,
            TYPE_VALUE_ERR
          )
        ), required = true)
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          TYPE_VALUE_DCG -> Seq.empty,
          TYPE_VALUE_NDCG -> Seq.empty,
          TYPE_VALUE_PRECISION -> Seq(
            FieldDef(StringConstantStructDef(THRESHOLD_KEY), DoubleMinMaxStructDef(0.0, 1.0), required = true)
          ),
          TYPE_VALUE_RECALL -> Seq(
            FieldDef(StringConstantStructDef(THRESHOLD_KEY), DoubleMinMaxStructDef(0.0, 1.0), required = true)
          ),
          TYPE_VALUE_ERR -> Seq(
            FieldDef(StringConstantStructDef(MAX_GRADE_KEY), DoubleMinMaxStructDef(0.0, 100.0), required = true)
          )
        ))
      )
    )
  }
}
