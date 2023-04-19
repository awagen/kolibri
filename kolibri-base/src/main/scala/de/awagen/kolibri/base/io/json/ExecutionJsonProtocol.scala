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

import de.awagen.kolibri.base.format.RegexUtils
import de.awagen.kolibri.base.processing.execution.functions.AggregationFunctions.{AggregateFilesWeighted, AggregateFromDirectoryByRegexWeighted, DoNothing, MultiExecution}
import de.awagen.kolibri.base.processing.execution.functions.AnalyzeFunctions.{GetImprovingAndLoosing, GetImprovingAndLoosingFromDirPerRegex, GetValueVarianceFromDirPerRegex}
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.provider.WeightProviders.WeightProvider
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat, enrichAny}

import scala.util.matching.Regex

object ExecutionJsonProtocol extends DefaultJsonProtocol {

  val TYPE_KEY = "type"
  val REGEX_KEY = "regex"
  val FILES_KEY = "files"
  val OUTPUT_FILENAME_KEY = "outputFilename"
  val READ_SUBDIR_KEY = "readSubDir"
  val WRITE_SUBDIR_KEY = "writeSubDir"
  val WEIGHT_PROVIDER_KEY = "weightProvider"
  val GROUP_SUPPLIER_KEY = "groupSupplier"
  val DIRECTORY_KEY = "directory"
  val CURRENT_PARAMS_KEY = "currentParams"
  val COMPARE_PARAMS_KEY = "compareParams"
  val METRIC_NAME_KEY = "metricName"
  val QUERY_PARAM_NAME_KEY = "queryParamName"
  val N_BEST_KEY = "n_best"
  val N_WORST_KEY = "n_worst"
  val AGGREGATE_FROM_DIR_BY_REGEX_TYPE = "AGGREGATE_FROM_DIR_BY_REGEX"
  val AGGREGATE_FILES_TYPE = "AGGREGATE_FILES"
  val AGGREGATE_GROUPS_TYPE = "AGGREGATE_GROUPS"
  val ANALYZE_BEST_WORST_REGEX_TYPE = "ANALYZE_BEST_WORST_REGEX"
  val ANALYZE_BEST_WORST_FILES_TYPE = "ANALYZE_BEST_WORST_FILES"
  val ANALYZE_QUERY_METRIC_VARIANCE_TYPE = "ANALYZE_QUERY_METRIC_VARIANCE"
  val DO_NOTHING_TYPE = "DO_NOTHING"

  val MISSING_VALUE_VALUE = "MISSING_VALUE"

  object ExecutionFormat extends WithStructDef {

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(
              Seq(
                AGGREGATE_FROM_DIR_BY_REGEX_TYPE,
                AGGREGATE_FILES_TYPE,
                AGGREGATE_GROUPS_TYPE,
                ANALYZE_BEST_WORST_REGEX_TYPE,
                ANALYZE_BEST_WORST_FILES_TYPE,
                ANALYZE_QUERY_METRIC_VARIANCE_TYPE
              )
            ),
            required = true
          )
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              AGGREGATE_FROM_DIR_BY_REGEX_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(REGEX_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(OUTPUT_FILENAME_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(READ_SUBDIR_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(WRITE_SUBDIR_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(WEIGHT_PROVIDER_KEY),
                  WeightProviderJsonProtocol.StringWeightProviderFormat.structDef,
                  required = true
                )
              ),
              AGGREGATE_FILES_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(FILES_KEY),
                  StringSeqStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(OUTPUT_FILENAME_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(WRITE_SUBDIR_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(WEIGHT_PROVIDER_KEY),
                  WeightProviderJsonProtocol.StringWeightProviderFormat.structDef,
                  required = true
                )
              ),
              AGGREGATE_GROUPS_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(GROUP_SUPPLIER_KEY),
                  SupplierJsonProtocol.StringSeqMappingFormatStruct.structDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(READ_SUBDIR_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(WRITE_SUBDIR_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(WEIGHT_PROVIDER_KEY),
                  WeightProviderJsonProtocol.StringWeightProviderFormat.structDef,
                  required = true
                )
              ),
              ANALYZE_BEST_WORST_REGEX_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(DIRECTORY_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(REGEX_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(CURRENT_PARAMS_KEY),
                  MapStructDef(StringStructDef, StringSeqStructDef),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(COMPARE_PARAMS_KEY),
                  GenericSeqStructDef(MapStructDef(StringStructDef, StringSeqStructDef)),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(METRIC_NAME_KEY),
                  // TODO: might wanna restrict to actual available metrics
                  // (take into account that metrics can also be custom though)
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(QUERY_PARAM_NAME_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(N_BEST_KEY),
                  IntMinMaxStructDef(0, 1000),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(N_WORST_KEY),
                  IntMinMaxStructDef(0, 1000),
                  required = true
                )
              ),
              ANALYZE_BEST_WORST_FILES_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(FILES_KEY),
                  StringSeqStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(CURRENT_PARAMS_KEY),
                  MapStructDef(StringStructDef, StringSeqStructDef),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(COMPARE_PARAMS_KEY),
                  GenericSeqStructDef(MapStructDef(StringStructDef, StringSeqStructDef)),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(METRIC_NAME_KEY),
                  // TODO: might wanna restrict to actual available metrics
                  // (take into account that metrics can also be custom though)
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(QUERY_PARAM_NAME_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(N_BEST_KEY),
                  IntMinMaxStructDef(0, 1000),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(N_WORST_KEY),
                  IntMinMaxStructDef(0, 1000),
                  required = true
                )
              ),
              ANALYZE_QUERY_METRIC_VARIANCE_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(DIRECTORY_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(REGEX_KEY),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(METRIC_NAME_KEY),
                  // TODO: might wanna restrict to actual available metrics
                  // (take into account that metrics can also be custom though)
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(QUERY_PARAM_NAME_KEY),
                  RegexStructDef(".+".r),
                  required = true
                )
              ),
              DO_NOTHING_TYPE -> Seq.empty
            )
          )
        )
      )
    }

  }

  case class ExecutionFormat(reader: Reader[String, Seq[String]],
                             writer: Writer[String, String, _],
                             metricDocumentFormatsMap: Map[String, MetricDocumentFormat],
                             regexToDataOverviewReader: SerializableFunction1[Regex, DataOverviewReader],
                             weightFormat: JsonFormat[WeightProvider[String]],
                             supplierJsonProtocol: SupplierJsonProtocol) extends RootJsonFormat[Execution[Any]] {
    implicit val wf: JsonFormat[WeightProvider[String]] = weightFormat
    implicit val mappingSupplierFormat: JsonFormat[SerializableSupplier[Map[String, Double]]] = supplierJsonProtocol.MapStringDoubleFormat
    implicit val stringSeqMappingFormat: JsonFormat[() => Map[String, Seq[String]]] = supplierJsonProtocol.StringSeqMappingFormat

    override def read(json: JsValue): Execution[Any] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case AGGREGATE_FROM_DIR_BY_REGEX_TYPE =>
          val regex: Regex = fields(REGEX_KEY).convertTo[String].r
          val outputFilename: String = fields(OUTPUT_FILENAME_KEY).convertTo[String]
          val readSubDir: String = fields(READ_SUBDIR_KEY).convertTo[String]
          val writeSubDir: String = fields(WRITE_SUBDIR_KEY).convertTo[String]
          val weightProvider: WeightProvider[String] = fields(WEIGHT_PROVIDER_KEY).convertTo[WeightProvider[String]]
          AggregateFromDirectoryByRegexWeighted(
            regexToDataOverviewReader,
            reader,
            writer,
            metricDocumentFormatsMap,
            readSubDir,
            writeSubDir,
            regex,
            weightProvider,
            outputFilename
          )
        case AGGREGATE_FILES_TYPE =>
          val files: Seq[String] = fields(FILES_KEY).convertTo[Seq[String]]
          val outputFilename: String = fields(OUTPUT_FILENAME_KEY).convertTo[String]
          val writeSubDir: String = fields(WRITE_SUBDIR_KEY).convertTo[String]
          val weightProvider: WeightProvider[String] = fields(WEIGHT_PROVIDER_KEY).convertTo[WeightProvider[String]]
          AggregateFilesWeighted(
            reader,
            writer,
            metricDocumentFormatsMap,
            writeSubDir,
            files,
            weightProvider,
            outputFilename
          )
        case AGGREGATE_GROUPS_TYPE =>
          val groupNameToIdentifierMap: Map[String, Seq[String]] = fields(GROUP_SUPPLIER_KEY).convertTo[() => Map[String, Seq[String]]].apply()
          val readSubDir: String = fields(READ_SUBDIR_KEY).convertTo[String]
          val writeSubDir: String = fields(WRITE_SUBDIR_KEY).convertTo[String]
          val weightProvider: WeightProvider[String] = fields(WEIGHT_PROVIDER_KEY).convertTo[WeightProvider[String]]
          val executions: Seq[Execution[Any]] = groupNameToIdentifierMap.map(x => {
            AggregateFilesWeighted(
              reader,
              writer,
              metricDocumentFormatsMap,
              writeSubDir,
              x._2.map(x => s"${readSubDir.stripSuffix("/")}/$x"),
              weightProvider,
              x._1
            )
          }).toSeq
          MultiExecution(executions)
        case ANALYZE_BEST_WORST_REGEX_TYPE =>
          val directory: String = fields(DIRECTORY_KEY).convertTo[String]
          val regex: Regex = fields(REGEX_KEY).convertTo[String].r
          val currentParams: Map[String, Seq[String]] = fields(CURRENT_PARAMS_KEY).convertTo[Map[String, Seq[String]]]
          val compareParams: Seq[Map[String, Seq[String]]] = fields(COMPARE_PARAMS_KEY).convertTo[Seq[Map[String, Seq[String]]]]
          val metricName: String = fields(METRIC_NAME_KEY).convertTo[String]
          val queryParamName: String = fields(QUERY_PARAM_NAME_KEY).convertTo[String]
          val n_best: Int = fields(N_BEST_KEY).convertTo[Int]
          val n_worst: Int = fields(N_WORST_KEY).convertTo[Int]
          GetImprovingAndLoosingFromDirPerRegex(
            regexToDataOverviewReader,
            reader,
            writer,
            directory,
            regex,
            currentParams,
            compareParams,
            metricName,
            queryFromFilename = x => RegexUtils.findParamValueInString(param = queryParamName,
              string = x, defaultValue = MISSING_VALUE_VALUE),
            n_best,
            n_worst
          )
        case ANALYZE_BEST_WORST_FILES_TYPE =>
          val files: Seq[String] = fields(FILES_KEY).convertTo[Seq[String]]
          val currentParams: Map[String, Seq[String]] = fields(CURRENT_PARAMS_KEY).convertTo[Map[String, Seq[String]]]
          val compareParams: Seq[Map[String, Seq[String]]] = fields(COMPARE_PARAMS_KEY).convertTo[Seq[Map[String, Seq[String]]]]
          val metricName: String = fields(METRIC_NAME_KEY).convertTo[String]
          val queryParamName: String = fields(QUERY_PARAM_NAME_KEY).convertTo[String]
          val n_best: Int = fields(N_BEST_KEY).convertTo[Int]
          val n_worst: Int = fields(N_WORST_KEY).convertTo[Int]
          GetImprovingAndLoosing(
            reader,
            writer,
            files,
            currentParams,
            compareParams,
            metricName,
            queryFromFilename = x => RegexUtils.findParamValueInString(param = queryParamName,
              string = x, defaultValue = MISSING_VALUE_VALUE),
            n_best,
            n_worst
          )
        case ANALYZE_QUERY_METRIC_VARIANCE_TYPE =>
          val directory: String = fields(DIRECTORY_KEY).convertTo[String]
          val regex: Regex = fields(REGEX_KEY).convertTo[String].r
          val metricName: String = fields(METRIC_NAME_KEY).convertTo[String]
          val queryParamName: String = fields(QUERY_PARAM_NAME_KEY).convertTo[String]
          val queryFromFileName: String => String = x => RegexUtils.findParamValueInString(param = queryParamName,
            string = x, defaultValue = MISSING_VALUE_VALUE)
          GetValueVarianceFromDirPerRegex(
            regexToDataOverviewReader,
            reader,
            directory,
            regex,
            metricName,
            queryFromFileName)
        case DO_NOTHING_TYPE => DoNothing()
      }
    }

    // TODO
    override def write(obj: Execution[Any]): JsValue = """{}""".toJson
  }

}
