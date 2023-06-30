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
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, TwoInCalculation}
import de.awagen.kolibri.definitions.directives.Resource
import de.awagen.kolibri.definitions.io.json.ResourceJsonProtocol.StructDefs.RESOURCE_JUDGEMENT_PROVIDER_STRUCT_DEF
import de.awagen.kolibri.definitions.io.json.ResourceJsonProtocol.resourceJudgementProviderFormat
import de.awagen.kolibri.definitions.resources.ResourceProvider
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.CalculationName.{BINARY_PRECISION_FALSE_AS_YES, BINARY_PRECISION_TRUE_AS_YES, FALSE_COUNT, FIRST_FALSE, FIRST_TRUE, IDENTITY, IR_METRICS, JACCARD_SIMILARITY, STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM, TRUE_COUNT}
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.MetricsCalculationJsonProtocol
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.MetricsCalculationJsonProtocol.metricsCalculationFormat
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations.{FromMapCalculation, JudgementsFromResourceIRMetricsCalculations}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.ComputeResultFunctions.{booleanPrecision, countValues, findFirstValue, stringSeqHistogram}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.TwoInComputeResultFunctions.jaccardSimilarity
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{Calculations, ComputeResultFunctions, MetricsCalculation}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object CalculationsJsonProtocol extends DefaultJsonProtocol {

  val NAME_KEY = "name"
  val QUERY_PARAM_NAME_KEY = "queryParamName"
  val PRODUCT_IDS_KEY_KEY = "productIdsKey"
  val JUDGEMENT_RESOURCE_KEY = "judgementsResource"
  val METRICS_CALCULATION_KEY = "metricsCalculation"

}

case class CalculationsJsonProtocol(resourceProvider: ResourceProvider) {

  import CalculationsJsonProtocol._

  implicit object FromMapCalculationsFormat extends JsonFormat[Calculation[WeaklyTypedMap[String], Any]] with WithStructDef {
    val DATA_KEY_KEY = "dataKey"
    val TYPE_KEY = "type"
    val K_KEY = "k"

    override def read(json: JsValue): Calculation[WeaklyTypedMap[String], Any] = json match {
      case spray.json.JsObject(fields) =>
        fields(TYPE_KEY).convertTo[String] match {
          case IR_METRICS.name =>
            val queryParamName = fields(QUERY_PARAM_NAME_KEY).convertTo[String]
            val productIdsKey = fields(PRODUCT_IDS_KEY_KEY).convertTo[String]
            val judgementsResource: Resource[JudgementProvider[Double]] = fields(JUDGEMENT_RESOURCE_KEY).convertTo[Resource[JudgementProvider[Double]]]
            val metricsCalculation = fields(METRICS_CALCULATION_KEY).convertTo[MetricsCalculation]
            JudgementsFromResourceIRMetricsCalculations(
              productIdsKey,
              REQUEST_TEMPLATE_STORAGE_KEY.name,
              queryParamName,
              judgementsResource,
              metricsCalculation,
              resourceProvider)
          case IDENTITY.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            FromMapCalculation[Double, Double](Set(metricName), dataKey, ComputeResultFunctions.identity[Double])
          case FIRST_TRUE.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            FromMapCalculation[Seq[Boolean], Double](Set(metricName), dataKey, findFirstValue(true))
          case FIRST_FALSE.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            FromMapCalculation[Seq[Boolean], Double](Set(metricName), dataKey, findFirstValue(false))
          case TRUE_COUNT.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            FromMapCalculation[Seq[Boolean], Double](Set(metricName), dataKey, countValues(true))
          case FALSE_COUNT.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            FromMapCalculation[Seq[Boolean], Double](Set(metricName), dataKey, countValues(false))
          case BINARY_PRECISION_TRUE_AS_YES.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            val k: Int = fields(K_KEY).convertTo[Int]
            FromMapCalculation[Seq[Boolean], Double](Set(metricName), dataKey, booleanPrecision(useTrue = true, k))
          case BINARY_PRECISION_FALSE_AS_YES.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            val k: Int = fields(K_KEY).convertTo[Int]
            FromMapCalculation[Seq[Boolean], Double](Set(metricName), dataKey, booleanPrecision(useTrue = false, k))
          case STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM.name =>
            val metricName: String = fields(NAME_KEY).convertTo[String]
            val dataKey: String = fields(DATA_KEY_KEY).convertTo[String]
            val k: Int = fields(K_KEY).convertTo[Int]
            FromMapCalculation[Seq[String], Map[String, Map[String, Double]]](Set(metricName), dataKey, stringSeqHistogram(k))
        }
    }

    // TODO
    override def write(obj: Calculation[WeaklyTypedMap[String], Any]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      val singleValueCalculationMandatoryFields = Seq(
        FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
        FieldDef(StringConstantStructDef(DATA_KEY_KEY), RegexStructDef(".*".r), required = true),
      )

      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              IR_METRICS.name,
              IDENTITY.name,
              FIRST_TRUE.name,
              FIRST_FALSE.name,
              TRUE_COUNT.name,
              FALSE_COUNT.name,
              BINARY_PRECISION_TRUE_AS_YES.name,
              BINARY_PRECISION_FALSE_AS_YES.name,
              STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM.name
            )),
            required = true
          ),
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              IR_METRICS.name -> Seq(
                FieldDef(StringConstantStructDef(QUERY_PARAM_NAME_KEY), RegexStructDef("\\w+".r), required = true),
                FieldDef(StringConstantStructDef(PRODUCT_IDS_KEY_KEY), RegexStructDef("\\w+".r), required = true),
                FieldDef(StringConstantStructDef(JUDGEMENT_RESOURCE_KEY), RESOURCE_JUDGEMENT_PROVIDER_STRUCT_DEF, required = true),
                FieldDef(StringConstantStructDef(METRICS_CALCULATION_KEY), MetricsCalculationJsonProtocol.structDef, required = true)
              ),
              IDENTITY.name -> singleValueCalculationMandatoryFields,
              FIRST_TRUE.name -> singleValueCalculationMandatoryFields,
              FIRST_FALSE.name -> singleValueCalculationMandatoryFields,
              TRUE_COUNT.name -> singleValueCalculationMandatoryFields,
              FALSE_COUNT.name -> singleValueCalculationMandatoryFields,
              BINARY_PRECISION_TRUE_AS_YES.name -> (Seq(
                FieldDef(StringConstantStructDef(K_KEY), IntMinMaxStructDef(0, 1000), required = true)
              ) ++ singleValueCalculationMandatoryFields),
              BINARY_PRECISION_FALSE_AS_YES.name -> (Seq(
                FieldDef(StringConstantStructDef(K_KEY), IntMinMaxStructDef(0, 1000), required = true)
              ) ++ singleValueCalculationMandatoryFields),
              STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM.name -> (
                Seq(
                  FieldDef(StringConstantStructDef(K_KEY), IntMinMaxStructDef(0, 1000), required = true)
                ) ++ singleValueCalculationMandatoryFields
                )
            )
          )
        )
      )
    }
  }

  implicit object FromTwoMapsCalculationFormat extends JsonFormat[TwoInCalculation[WeaklyTypedMap[String], WeaklyTypedMap[String], Any]] with WithStructDef {

    val TYPE_KEY = "type"
    val NAME_KEY = "name"
    val DATA_1_KEY = "data1Key"
    val DATA_2_KEY = "data2Key"

    override def read(json: JsValue): TwoInCalculation[WeaklyTypedMap[String], WeaklyTypedMap[String], Any] = json match {
        case spray.json.JsObject(fields) =>
          fields(TYPE_KEY).convertTo[String] match {
            case JACCARD_SIMILARITY.name =>
              val metricName = fields(NAME_KEY).convertTo[String]
              val data1Key = fields(DATA_1_KEY).convertTo[String]
              val data2Key = fields(DATA_2_KEY).convertTo[String]
              Calculations.FromTwoMapsCalculation(metricName, data1Key, data2Key, jaccardSimilarity)
          }
    }

    // TODO: implement
    override def write(obj: TwoInCalculation[WeaklyTypedMap[String], WeaklyTypedMap[String], Any]): JsValue = ???

    override def structDef: StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              JACCARD_SIMILARITY.name,
            )),
            required = true
          )
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              JACCARD_SIMILARITY.name -> Seq(
                FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
                FieldDef(StringConstantStructDef(DATA_1_KEY), RegexStructDef(".*".r), required = true),
                FieldDef(StringConstantStructDef(DATA_2_KEY), RegexStructDef(".*".r), required = true)
              )
            )
          )
        )
      )

    }
  }

}
