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

import de.awagen.kolibri.base.directives.ResourceType
import de.awagen.kolibri.base.usecase.searchopt.io.json.CalculationName.{BINARY_PRECISION_FALSE_AS_YES, BINARY_PRECISION_TRUE_AS_YES, FALSE_COUNT, FIRST_FALSE, FIRST_TRUE, IDENTITY, TRUE_COUNT}
import de.awagen.kolibri.base.usecase.searchopt.io.json.JudgementProviderFactoryJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.io.json.MetricsCalculationJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Functions.{booleanPrecision, countValues, findFirstValue}
import de.awagen.kolibri.base.usecase.searchopt.metrics.{Functions, MetricsCalculation}
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{ConditionalFields, IntMinMaxStructDef, NestedFieldSeqStructDef, RegexStructDef, SeqRegexStructDef, StringChoiceStructDef, StringConstantStructDef}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

import scala.collection.immutable


object CalculationsJsonProtocol extends DefaultJsonProtocol {

  val NAME_KEY = "name"
  val FUNCTION_TYPE_KEY = "functionType"
  val IR_METRICS_VALUE = "IR_METRICS"
  val QUERY_PARAM_NAME_KEY = "queryParamName"
  val REQUEST_TEMPLATE_KEY_KEY = "requestTemplateKey"
  val PRODUCT_IDS_KEY_KEY = "productIdsKey"
  val JUDGEMENT_PROVIDER_KEY = "judgementProvider"
  val METRICS_CALCULATION_KEY = "metricsCalculation"
  val EXCLUDE_PARAMS_KEY = "excludeParams"

  implicit object FromMapFutureCalculationSeqStringToMetricRowFormat extends JsonFormat[FutureCalculation[WeaklyTypedMap[String], Set[String], MetricRow]] with WithStructDef {
    override def read(json: JsValue): FutureCalculation[WeaklyTypedMap[String], Set[String], MetricRow] = json match {
      case spray.json.JsObject(fields) =>
        fields(FUNCTION_TYPE_KEY).convertTo[String] match {
          case IR_METRICS_VALUE =>
            val name = fields(NAME_KEY).convertTo[String]
            val queryParamName = fields(QUERY_PARAM_NAME_KEY).convertTo[String]
            val requestTemplateKey = fields(REQUEST_TEMPLATE_KEY_KEY).convertTo[String]
            val productIdsKey = fields(PRODUCT_IDS_KEY_KEY).convertTo[String]
            val judgementProviderFactory = fields(JUDGEMENT_PROVIDER_KEY).convertTo[JudgementProviderFactory[Double]]
            val metricsCalculation = fields(METRICS_CALCULATION_KEY).convertTo[MetricsCalculation]
            val excludeParamsFromMetricRow = fields(EXCLUDE_PARAMS_KEY).convertTo[Seq[String]]
            val calculation = JudgementBasedMetricsCalculation(
              name,
              queryParamName,
              requestTemplateKey,
              productIdsKey,
              judgementProviderFactory,
              metricsCalculation,
              excludeParamsFromMetricRow
            )
            val result = FromMapFutureCalculation(name, metricsCalculation.metrics.map(x => x.name).toSet, calculation)
            judgementProviderFactory.resources.filter(resource => resource.resourceType == ResourceType.MAP_STRING_TO_DOUBLE_VALUE)
              .foreach(resource => result.addResource(resource))
            result
        }
    }

    // TODO
    override def write(obj: FutureCalculation[WeaklyTypedMap[String], Set[String], MetricRow]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(Seq(
        FieldDef(
          StringConstantStructDef(FUNCTION_TYPE_KEY),
          StringChoiceStructDef(Seq(IR_METRICS_VALUE)),
          required = true
        )
      ),
        Seq(
          ConditionalFields(
            FUNCTION_TYPE_KEY,
            immutable.Map(IR_METRICS_VALUE -> Seq(
              FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
              FieldDef(StringConstantStructDef(QUERY_PARAM_NAME_KEY), RegexStructDef(".*".r), required = true),
              FieldDef(StringConstantStructDef(REQUEST_TEMPLATE_KEY_KEY), RegexStructDef(".*".r), required = true),
              FieldDef(StringConstantStructDef(PRODUCT_IDS_KEY_KEY), RegexStructDef(".*".r), required = true),
              FieldDef(StringConstantStructDef(JUDGEMENT_PROVIDER_KEY), JudgementProviderFactoryJsonProtocol.structDef, required = true),
              FieldDef(StringConstantStructDef(METRICS_CALCULATION_KEY), MetricsCalculationJsonProtocol.structDef, required = true),
              FieldDef(StringConstantStructDef(EXCLUDE_PARAMS_KEY), SeqRegexStructDef(".*".r), required = true),
            ))
          )
        ))
    }
  }

  implicit object FromMapCalculationSeqBooleanToDoubleFormat extends JsonFormat[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]] with WithStructDef {
    val DATA_KEY_KEY = "dataKey"
    val TYPE_KEY = "type"
    val K_KEY = "k"

    override def read(json: JsValue): Calculation[WeaklyTypedMap[String], CalculationResult[Double]] = json match {
      case spray.json.JsObject(fields) =>
        val metricName: String = fields(NAME_KEY).convertTo[String]
        val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
        fields(TYPE_KEY).convertTo[String] match {
          case IDENTITY.name =>
            FromMapCalculation[Double, Double](metricName, dataKey, Functions.identity[Double])
          case FIRST_TRUE.name =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, findFirstValue(true))
          case FIRST_FALSE.name =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, findFirstValue(false))
          case TRUE_COUNT.name =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, countValues(true))
          case FALSE_COUNT.name =>
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, countValues(false))
          case BINARY_PRECISION_TRUE_AS_YES.name =>
            val k: Int = fields(K_KEY).convertTo[Int]
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, booleanPrecision(useTrue = true, k))
          case BINARY_PRECISION_FALSE_AS_YES.name =>
            val k: Int = fields(K_KEY).convertTo[Int]
            FromMapCalculation[Seq[Boolean], Double](metricName, dataKey, booleanPrecision(useTrue = false, k))
        }
    }

    // TODO
    override def write(obj: Calculation[WeaklyTypedMap[String], CalculationResult[Double]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
          FieldDef(StringConstantStructDef(DATA_KEY_KEY), RegexStructDef(".*".r), required = true),
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              IDENTITY.name,
              FIRST_TRUE.name,
              FIRST_FALSE.name,
              TRUE_COUNT.name,
              FALSE_COUNT.name,
              BINARY_PRECISION_TRUE_AS_YES.name,
              BINARY_PRECISION_FALSE_AS_YES.name,
            )),
            required = true
          ),
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              IDENTITY.name -> Seq.empty,
              FIRST_TRUE.name -> Seq.empty,
              FIRST_FALSE.name -> Seq.empty,
              TRUE_COUNT.name -> Seq.empty,
              FALSE_COUNT.name -> Seq.empty,
              BINARY_PRECISION_TRUE_AS_YES.name -> Seq(
                FieldDef(StringConstantStructDef(K_KEY), IntMinMaxStructDef(0, 1000), required = true)
              ),
              BINARY_PRECISION_FALSE_AS_YES.name -> Seq(
                FieldDef(StringConstantStructDef(K_KEY), IntMinMaxStructDef(0, 1000), required = true)
              ),
            )
          )
        )
      )
    }
  }

}
