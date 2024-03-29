/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.io.json

import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.aggregateTypeFormat
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.types.FieldDefinitions._
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, TwoInCalculation}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.HttpMethod
import de.awagen.kolibri.definitions.http.HttpMethod.HttpMethod
import de.awagen.kolibri.definitions.io.json.ConnectionJsonProtocol.connectionFormat
import de.awagen.kolibri.definitions.io.json.EnumerationJsonProtocol.httpMethodFormat
import de.awagen.kolibri.definitions.io.json.TaggingConfigurationsJsonProtocol
import de.awagen.kolibri.definitions.io.json.TaggingConfigurationsJsonProtocol.requestAndParsingResultTaggerConfigFormat
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.ParsingConfigJsonProtocol
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.ParsingConfigJsonProtocol.parsingConfigJsonFormat
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.config.AppConfig.JsonFormats.calculationsJsonProtocol.{FromMapCalculationsFormat, FromTwoMapsCalculationFormat}
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.{MergeTwoMetricRows, RequestJsonAndParseValuesTask}
import de.awagen.kolibri.fleet.zio.execution.{TaskFactory, ZIOTask}
import de.awagen.kolibri.fleet.zio.io.json.EnumerationJsonProtocol.requestModeFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.requests.RequestMode
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.requests.RequestMode.RequestMode
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}
import zio.ZIO
import zio.http.Client

object TaskJsonProtocol extends DefaultJsonProtocol {

  private[json] val TYPE_KEY = "type"
  private[json] val REQUEST_AND_PARSE_VALUES_TASK_TYPE = "REQUEST_PARSE"
  private[json] val METRIC_CALCULATION_TASK_TYPE = "METRIC_CALCULATION"
  private[json] val MAP_COMPARISON_TASK_TYPE = "MAP_COMPARISON"
  private[json] val MERGE_METRIC_ROWS_TASK_TYPE = "MERGE_METRIC_ROWS"
  private[json] val PARSING_CONFIG_KEY = "parsingConfig"
  private[json] val REQUEST_AND_PARSING_RESULT_TAGGER_KEY = "taggingConfig"
  private[json] val CONNECTIONS_KEY = "connections"
  private[json] val CONTEXT_PATH_KEY = "contextPath"
  private[json] val FIXED_PARAMS_KEY = "fixedParams"
  private[json] val HTTP_METHOD_KEY = "httpMethod"
  private[json] val SUCCESS_KEY_NAME_KEY = "successKeyName"
  private[json] val FAIL_KEY_NAME_KEY = "failKeyName"
  private[json] val REQUEST_MODE_KEY = "requestMode"
  private[json] val PARSED_VALUES_TO_CONTEXT_KEYS_KEY = "parsedValueToContextKeys"


  private[json] val INPUT_KEY_1 = "input1"
  private[json] val INPUT_KEY_2 = "input2"

  private[json] val PARSED_DATA_KEY_NAME_KEY = "parsedDataKey"
  private[json] val CALCULATIONS_KEY = "calculations"
  private[json] val METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_KEY = "metricNameToAggregationTypeMapping"
  private[json] val EXCLUDE_PARAMS_FROM_METRIC_ROW_KEY = "excludeParamsFromMetricRow"


  /**
   * Json format for sequence of task whose success value is WeaklyTypedMap with key-type String.
   * Sequence is used here to be able to provide multiple tasks if the related execution contains
   * multiple steps.
   */
  implicit object SeqTypedMapZIOTaskFormat extends JsonFormat[ZIO[Client, Throwable, Seq[ZIOTask[WeaklyTypedMap[String]]]]] with WithStructDef {
    override def read(json: JsValue): ZIO[Client, Throwable, Seq[ZIOTask[WeaklyTypedMap[String]]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case REQUEST_AND_PARSE_VALUES_TASK_TYPE =>
          val parsingConfig: ParsingConfig = fields(PARSING_CONFIG_KEY).convertTo[ParsingConfig]
          val taggingConfig: RequestAndParsingResultTaggerConfig = fields(REQUEST_AND_PARSING_RESULT_TAGGER_KEY).convertTo[RequestAndParsingResultTaggerConfig]
          val connections: Seq[Connection] = fields(CONNECTIONS_KEY).convertTo[Seq[Connection]]
          val requestMode: RequestMode = fields(REQUEST_MODE_KEY).convertTo[RequestMode]
          val contextPath = fields(CONTEXT_PATH_KEY).convertTo[String]
          val fixedParams: Map[String, Seq[String]] = fields(FIXED_PARAMS_KEY).convertTo[Map[String, Seq[String]]]
          val httpMethod: HttpMethod = fields.get(HTTP_METHOD_KEY).map(x => x.convertTo[HttpMethod])
            .getOrElse(HttpMethod.GET)
          val successKeyName: String = fields.get(SUCCESS_KEY_NAME_KEY).map(x => x.convertTo[String])
            .getOrElse("parsedValueMap")
          val failKeyName: String = fields.get(FAIL_KEY_NAME_KEY).map(x => x.convertTo[String])
            .getOrElse("parseFail")
          requestMode match {
            // in case of request all connections mode,
            // we request all connections separately and store results for each
            case RequestMode.REQUEST_ALL_CONNECTIONS =>
              for {
                httpClient <- ZIO.service[Client]
                tasks <- ZIO.attempt({
                  connections.indices.map(x => {
                    RequestJsonAndParseValuesTask(
                      parsingConfig = parsingConfig,
                      taggingConfig = taggingConfig,
                      connectionSupplier = () => connections(x),
                      contextPath = contextPath,
                      fixedParams = fixedParams,
                      httpMethod = httpMethod.toString,
                      successKeyName = s"$successKeyName-${x + 1}",
                      failKeyName = s"$failKeyName-${x + 1}",
                      httpClient = httpClient
                    )
                  })
                })
              } yield tasks
            // in case of distribute load mode, we distribute load over all connections,
            // thus only retrieve a single result for each request from any of the nodes
            case RequestMode.DISTRIBUTE_LOAD =>
              val random = new util.Random()
              val connectionSupplier = () => {
                val connectionIndex = random.between(0, connections.length)
                connections(connectionIndex)
              }
              for {
                httpClient <- ZIO.service[Client]
                tasks <- ZIO.attempt({
                  Seq(
                    RequestJsonAndParseValuesTask(
                      parsingConfig = parsingConfig,
                      taggingConfig = taggingConfig,
                      connectionSupplier = connectionSupplier,
                      contextPath = contextPath,
                      fixedParams = fixedParams,
                      httpMethod = httpMethod.toString,
                      successKeyName = successKeyName,
                      failKeyName = failKeyName,
                      httpClient = httpClient
                    )
                  )
                })
              } yield tasks
          }
      }
    }

    // TODO: implement
    override def write(obj: ZIO[Client, Throwable, Seq[ZIOTask[WeaklyTypedMap[String]]]]): JsValue = ???

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(
              Seq(REQUEST_AND_PARSE_VALUES_TASK_TYPE)
            ),
            required = true,
            description = "Type of the job."
          ),
          FieldDef(
            StringConstantStructDef(SUCCESS_KEY_NAME_KEY),
            RegexStructDef(".+".r),
            required = false,
            description = "The success key name to store computed result under if compute is successful. Note that in case" +
              " of RequestMode REQUEST_ALL_CONNECTIONS this will be used as prefix and '-[index]' is appended, where [index] stands for 1-based increasing index." +
              " If not set, default will be used."
          ),
          FieldDef(
            StringConstantStructDef(FAIL_KEY_NAME_KEY),
            RegexStructDef(".+".r),
            required = false,
            description = "The fail key name to store computed TaskFailType under if compute is NOT successful. Note that in case" +
              " of RequestMode REQUEST_ALL_CONNECTIONS this will be used as prefix and '-[index]' is appended, where [index] stands for 1-based increasing index." +
              "  If not set, default will be used."
          )
        ),
        Seq(
          ConditionalFields(TYPE_KEY, Map(
            REQUEST_AND_PARSE_VALUES_TASK_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(PARSING_CONFIG_KEY),
                ParsingConfigJsonProtocol.structDef,
                required = true,
                description = "Definition of data to parse out of the (json) response"
              ),
              FieldDef(
                StringConstantStructDef(REQUEST_AND_PARSING_RESULT_TAGGER_KEY),
                TaggingConfigurationsJsonProtocol.RequestAndParsingResultTaggerConfigFormat.structDef,
                required = true,
                description = "Definition of tagger based on request settings and result of requesting and parsing."
              ),
              FieldDef(
                StringConstantStructDef(CONNECTIONS_KEY),
                GenericSeqStructDef(
                  AppConfig.JsonFormats.connectionFormatStruct.structDef
                ),
                required = true,
                description = "Connections to send requests to. Whether all of them are queried or load is distributed among them or another mode depends on the RequestMode set."
              ),
              FieldDef(
                StringConstantStructDef(REQUEST_MODE_KEY),
                StringChoiceStructDef(
                  Seq(RequestMode.REQUEST_ALL_CONNECTIONS.toString, RequestMode.DISTRIBUTE_LOAD.toString)
                ),
                required = true,
                description = "Request mode allows selection of how requests are sent to the given connections. In REQUEST_ALL_CONNECTIONS mode, " +
                  " every connection is requested separately and results are stored separately. In DISTRIBUTE_LOAD " +
                  " load is distributed among the given connections and a single response is stored."
              ),
              FieldDef(
                StringConstantStructDef(CONTEXT_PATH_KEY),
                StringStructDef,
                required = true,
                description = "The context path when composing the request."
              ),
              FieldDef(
                StringConstantStructDef(FIXED_PARAMS_KEY),
                MapStructDef(StringStructDef, StringSeqStructDef),
                required = true,
                description = "List of url parameters, where each parameter can have multiple values."
              ),
              FieldDef(
                StringConstantStructDef(HTTP_METHOD_KEY),
                StringChoiceStructDef(Seq(HttpMethod.GET.toString, HttpMethod.PUT.toString, HttpMethod.POST.toString)),
                required = false,
                description = "The http method to be used. If not set, GET will be used."
              )
            )
          ))
        )
      )
    }
  }

  implicit object MetricRowZIOTaskFormat extends JsonFormat[ZIO[Any, Throwable, ZIOTask[MetricRow]]] with WithStructDef {
    override def read(json: JsValue): ZIO[Any, Throwable, ZIOTask[MetricRow]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case METRIC_CALCULATION_TASK_TYPE =>
          val parsedDataKey = fields(PARSED_DATA_KEY_NAME_KEY).convertTo[String]
          val requestTemplateKey = REQUEST_TEMPLATE_STORAGE_KEY.name
          val calculations = fields(CALCULATIONS_KEY).convertTo[Seq[Calculation[WeaklyTypedMap[String], Any]]]
          val metricTypeMapping = fields(METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_KEY).convertTo[Map[String, AggregationType]]
          val excludeParamsFromMetricRow = fields(EXCLUDE_PARAMS_FROM_METRIC_ROW_KEY).convertTo[Seq[String]]
          // TODO: make this actually configurable so that we can tag based on result properties
          val tagger: ProcessingMessage[MetricRow] => ProcessingMessage[MetricRow] = identity
          val successKeyName = fields.get(SUCCESS_KEY_NAME_KEY).map(x => x.convertTo[String]).getOrElse("metricsRow")
          val failKeyName = fields.get(FAIL_KEY_NAME_KEY).map(x => x.convertTo[String]).getOrElse("metricsCalculationFail")
          val parsedValuesToContextKeys = fields.get(PARSED_VALUES_TO_CONTEXT_KEYS_KEY).map(x => x.convertTo[Seq[String]]).getOrElse(Seq.empty)
          ZIO.attempt({
            TaskFactory.CalculateMetricsTask(
              requestAndParseSuccessKey = parsedDataKey,
              requestTemplateKey = requestTemplateKey,
              calculations = calculations,
              metricNameToAggregationTypeMapping = metricTypeMapping,
              excludeParamsFromMetricRow = excludeParamsFromMetricRow,
              tagger = tagger,
              successKeyName = successKeyName,
              failKeyName = failKeyName,
              parsedValueToContextKeys = parsedValuesToContextKeys
            )
          })
        case MAP_COMPARISON_TASK_TYPE =>
          val inputKey1 = fields(INPUT_KEY_1).convertTo[String]
          val inputKey2 = fields(INPUT_KEY_2).convertTo[String]
          val twoInputCalculations = fields(CALCULATIONS_KEY).convertTo[Seq[TwoInCalculation[WeaklyTypedMap[String], WeaklyTypedMap[String], Any]]]
          val metricTypeMapping = fields(METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_KEY).convertTo[Map[String, AggregationType]]
          val excludeParamsFromMetricRow = fields(EXCLUDE_PARAMS_FROM_METRIC_ROW_KEY).convertTo[Seq[String]]
          val successKeyName = fields.get(SUCCESS_KEY_NAME_KEY).map(x => x.convertTo[String]).getOrElse("twoMapInputMetricsRow")
          val failKeyName = fields.get(FAIL_KEY_NAME_KEY).map(x => x.convertTo[String]).getOrElse("twoMapInputMetricsCalculationFail")
          ZIO.attempt({
            TaskFactory.TwoMapInputCalculation(
              key1 = inputKey1,
              key2 = inputKey2,
              calculations = twoInputCalculations,
              metricNameToAggregationTypeMapping = metricTypeMapping,
              excludeParamsFromMetricRow = excludeParamsFromMetricRow,
              successKeyName = successKeyName,
              failKeyName = failKeyName
            )
          })
        case MERGE_METRIC_ROWS_TASK_TYPE =>
          val inputKey1 = fields(INPUT_KEY_1).convertTo[String]
          val inputKey2 = fields(INPUT_KEY_2).convertTo[String]
          val successKeyName = fields.get(SUCCESS_KEY_NAME_KEY).map(x => x.convertTo[String]).getOrElse("mergeTwoRowsResult")
          val failKeyName = fields.get(FAIL_KEY_NAME_KEY).map(x => x.convertTo[String]).getOrElse("failedToMergeRows")
          ZIO.attempt({
            MergeTwoMetricRows(
              key1 = inputKey1,
              key2 = inputKey2,
              successKeyName = successKeyName,
              failKeyName = failKeyName
            )
          })
      }
    }

    // TODO: implement
    override def write(obj: ZIO[Any, Throwable, ZIOTask[MetricRow]]): JsValue = ???

    override def structDef: StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(
              Seq(
                METRIC_CALCULATION_TASK_TYPE,
                MAP_COMPARISON_TASK_TYPE,
                MERGE_METRIC_ROWS_TASK_TYPE
              )
            ),
            required = true,
            description = "Type of the job"
          ),
          FieldDef(
            StringConstantStructDef(SUCCESS_KEY_NAME_KEY),
            RegexStructDef(".+".r),
            required = false,
            description = "The success key name to store computed result under if compute is successful." +
              " If not set, default will be used."
          ),
          FieldDef(
            StringConstantStructDef(FAIL_KEY_NAME_KEY),
            RegexStructDef(".+".r),
            required = false,
            description = "The fail key name to store computed TaskFailType under if compute is NOT successful." +
              "  If not set, default will be used."
          )
        ),
        Seq(
          ConditionalFields(TYPE_KEY, Map(
            METRIC_CALCULATION_TASK_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(PARSED_DATA_KEY_NAME_KEY),
                RegexStructDef(".+".r),
                required = false,
                description = "The key under which the ProcessingMessage[WeaklyTypedMap[String]] object" +
                  " is stored. The contained WeaklyTypedMap[String] object contains the fields used" +
                  " within the computations, e.g parsed fields or similar."
              ),
              FieldDef(
                StringConstantStructDef(PARSED_VALUES_TO_CONTEXT_KEYS_KEY),
                StringSeqStructDef,
                required = false,
                description = "Keys of the parsed values in the WeaklyTypedMap[String] object that serves as input for this task for which the key-value pairs shall be taken over into the contextInfo object." +
                  "This allows storing additional information that is not a normal metric, yet can contain information such as images for the product sequence allowing" +
                  "to visualize the result sequence or similar."
              ),
              FieldDef(
                StringConstantStructDef(CALCULATIONS_KEY),
                GenericSeqStructDef(
                  AppConfig.JsonFormats.calculationsJsonProtocol.FromMapCalculationsFormat.structDef
                ),
                required = true,
                description = "Calculations to be executed. Here the fields extracted in the passed data (e.g as defined in parsingConfig) are referenced. " +
                  "Allows computation of common information retrieval (IR) metrics such as NDCG, ERR, Precision, Recall and " +
                  "further metrics (such as value distributions)."
              ),
              FieldDef(
                StringConstantStructDef(METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_KEY),
                MapStructDef(StringStructDef, aggregateTypeFormat.structDef),
                required = true,
                description = "Mapping of metric names to aggregation type."
              ),
              FieldDef(
                StringConstantStructDef(EXCLUDE_PARAMS_FROM_METRIC_ROW_KEY),
                StringSeqStructDef,
                required = true,
                description = "The results are aggregated based on non-metric fields in the result files. All fixed parameters " +
                  "as specified in the fixedParams setting are excluded by default, yet parameters that are added " +
                  "in the requestParameters setting will need to be excluded by entering the names of the parameters here, " +
                  "otherwise the aggregation might be too granular."
              )
            ),
            MAP_COMPARISON_TASK_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(INPUT_KEY_1),
                RegexStructDef(".+".r),
                required = true,
                description = "The key under which the first input argument (of type ProcessingMessage[WeaklyTypedMap[String]])" +
                  " is stored."
              ),
              FieldDef(
                StringConstantStructDef(INPUT_KEY_2),
                RegexStructDef(".+".r),
                required = true,
                description = "The key under which the second input argument (of type ProcessingMessage[WeaklyTypedMap[String]])" +
                  " is stored."
              ),
              FieldDef(
                StringConstantStructDef(CALCULATIONS_KEY),
                GenericSeqStructDef(
                  AppConfig.JsonFormats.calculationsJsonProtocol.FromTwoMapsCalculationFormat.structDef
                ),
                required = true,
                description = "Calculations to be computed given the two inputs. This can be jaccard similarity" +
                  " metric based on parsed result sequence or other pre-defined methods."
              ),
              FieldDef(
                StringConstantStructDef(METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_KEY),
                MapStructDef(StringStructDef, aggregateTypeFormat.structDef),
                required = true,
                description = "Mapping of metric names to aggregation type."
              ),
              FieldDef(
                StringConstantStructDef(EXCLUDE_PARAMS_FROM_METRIC_ROW_KEY),
                StringSeqStructDef,
                required = true,
                description = "The results are aggregated based on non-metric fields in the result files. All fixed parameters " +
                  "as specified in the fixedParams setting are excluded by default, yet parameters that are added " +
                  "in the requestParameters setting will need to be excluded by entering the names of the parameters here, " +
                  "otherwise the aggregation might be too granular."
              )
            ),
            MERGE_METRIC_ROWS_TASK_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(INPUT_KEY_1),
                RegexStructDef(".+".r),
                required = true,
                description = "The key under which the first input argument (of type ProcessingMessage[MetricRow])" +
                  " is stored."
              ),
              FieldDef(
                StringConstantStructDef(INPUT_KEY_2),
                RegexStructDef(".+".r),
                required = true,
                description = "The key under which the second input argument (of type ProcessingMessage[MetricRow])" +
                  " is stored."
              )
            )
          ))
        )
      )
    }
  }

  implicit object ZIOTaskFormat extends JsonFormat[ZIO[Client, Throwable, Seq[ZIOTask[_]]]] with WithStructDef {
    override def read(json: JsValue): ZIO[Client, Throwable, Seq[ZIOTask[_]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case REQUEST_AND_PARSE_VALUES_TASK_TYPE =>
          SeqTypedMapZIOTaskFormat.read(json)
        case e if Set(METRIC_CALCULATION_TASK_TYPE, MAP_COMPARISON_TASK_TYPE, MERGE_METRIC_ROWS_TASK_TYPE).contains(e) =>
          MetricRowZIOTaskFormat.read(json).map(x => Seq(x))
      }
    }

    // TODO
    override def write(obj: ZIO[Client, Throwable, Seq[ZIOTask[_]]]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(
            Seq(
              REQUEST_AND_PARSE_VALUES_TASK_TYPE,
              METRIC_CALCULATION_TASK_TYPE,
              MAP_COMPARISON_TASK_TYPE,
              MERGE_METRIC_ROWS_TASK_TYPE
            )
          ),
          required = true,
          description = "Type of the job"
        ),
        FieldDef(
          StringConstantStructDef(SUCCESS_KEY_NAME_KEY),
          RegexStructDef(".+".r),
          required = false,
          description = "The success key name to store computed result under if compute is successful." +
            " If not set, default will be used."
        ),
        FieldDef(
          StringConstantStructDef(FAIL_KEY_NAME_KEY),
          RegexStructDef(".+".r),
          required = false,
          description = "The fail key name to store computed TaskFailType under if compute is NOT successful." +
            "  If not set, default will be used."
        )
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          REQUEST_AND_PARSE_VALUES_TASK_TYPE -> SeqTypedMapZIOTaskFormat.structDef.asInstanceOf[NestedFieldSeqStructDef].conditionalFieldsSeq
            .find(x => x.conditionFieldId == TYPE_KEY).map(x => x.fieldsForConditionValue(REQUEST_AND_PARSE_VALUES_TASK_TYPE)).getOrElse(Seq.empty),
          METRIC_CALCULATION_TASK_TYPE -> MetricRowZIOTaskFormat.structDef.asInstanceOf[NestedFieldSeqStructDef].conditionalFieldsSeq
            .find(x => x.conditionFieldId == TYPE_KEY).map(x => x.fieldsForConditionValue(METRIC_CALCULATION_TASK_TYPE)).getOrElse(Seq.empty),
          MAP_COMPARISON_TASK_TYPE -> MetricRowZIOTaskFormat.structDef.asInstanceOf[NestedFieldSeqStructDef].conditionalFieldsSeq
            .find(x => x.conditionFieldId == TYPE_KEY).map(x => x.fieldsForConditionValue(MAP_COMPARISON_TASK_TYPE)).getOrElse(Seq.empty),
          MERGE_METRIC_ROWS_TASK_TYPE -> MetricRowZIOTaskFormat.structDef.asInstanceOf[NestedFieldSeqStructDef].conditionalFieldsSeq
            .find(x => x.conditionFieldId == TYPE_KEY).map(x => x.fieldsForConditionValue(MERGE_METRIC_ROWS_TASK_TYPE)).getOrElse(Seq.empty)
        ))
      )
    )
  }


}
