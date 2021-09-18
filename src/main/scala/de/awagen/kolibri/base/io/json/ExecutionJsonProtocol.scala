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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.config.AppConfig.persistenceModule.persistenceDIModule
import de.awagen.kolibri.base.config.di.modules.Modules
import de.awagen.kolibri.base.format.RegexUtils
import de.awagen.kolibri.base.io.json.WeightProviderJsonProtocol._
import de.awagen.kolibri.base.processing.execution.functions.AggregationFunctions.{AggregateFilesWeighted, AggregateFromDirectoryByRegexWeighted, DoNothing}
import de.awagen.kolibri.base.processing.execution.functions.AnalyzeFunctions.{GetImprovingAndLoosing, GetImprovingAndLoosingFromDirPerRegex}
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.provider.WeightProviders.WeightProvider
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat, enrichAny}

import scala.util.matching.Regex

object ExecutionJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object ExecutionFormat extends RootJsonFormat[Execution[Any]] {
    override def read(json: JsValue): Execution[Any] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "AGGREGATE_FROM_DIR_BY_REGEX" =>
          val regex: Regex = fields("regex").convertTo[String].r
          val outputFilename: String = fields("outputFilename").convertTo[String]
          val directorySubDir: String = fields("subDir").convertTo[String]
          val weightProvider: WeightProvider[String] = fields("weightProvider").convertTo[WeightProvider[String]]
          AggregateFromDirectoryByRegexWeighted(
            directorySubDir,
            regex,
            weightProvider,
            outputFilename
          )
        case "AGGREGATE_FILES" =>
          val files: Seq[String] = fields("files").convertTo[Seq[String]]
          val outputFilename: String = fields("outputFilename").convertTo[String]
          val directorySubDir: String = fields("subDir").convertTo[String]
          val weightProvider: WeightProvider[String] = fields("weightProvider").convertTo[WeightProvider[String]]
          AggregateFilesWeighted(
            directorySubDir,
            files,
            weightProvider,
            outputFilename
          )
        case "ANALYZE_BEST_WORST_REGEX" =>
          val directory: String = fields("directory").convertTo[String]
          val regex: Regex = fields("regex").convertTo[String].r
          val currentParams: Map[String, Seq[String]] = fields("currentParams").convertTo[Map[String, Seq[String]]]
          val compareParams: Seq[Map[String, Seq[String]]] = fields("compareParams").convertTo[Seq[Map[String, Seq[String]]]]
          val metricName: String = fields("metricName").convertTo[String]
          val queryParamName: String = fields("queryParamName").convertTo[String]
          val n_best: Int = fields("n_best").convertTo[Int]
          val n_worst: Int = fields("n_worst").convertTo[Int]
          GetImprovingAndLoosingFromDirPerRegex(
            directory,
            regex,
            currentParams,
            compareParams,
            metricName,
            queryFromFilename = x => RegexUtils.findParamValueInString(param = queryParamName,
              string = x, defaultValue = "MISSING_VALUE"),
            n_best,
            n_worst
          )
        case "ANALYZE_BEST_WORST_FILES" =>
          val files: Seq[String] = fields("files").convertTo[Seq[String]]
          val currentParams: Map[String, Seq[String]] = fields("currentParams").convertTo[Map[String, Seq[String]]]
          val compareParams: Seq[Map[String, Seq[String]]] = fields("compareParams").convertTo[Seq[Map[String, Seq[String]]]]
          val metricName: String = fields("metricName").convertTo[String]
          val queryParamName: String = fields("queryParamName").convertTo[String]
          val n_best: Int = fields("n_best").convertTo[Int]
          val n_worst: Int = fields("n_worst").convertTo[Int]
          GetImprovingAndLoosing(
            files,
            currentParams,
            compareParams,
            metricName,
            queryFromFilename = x => RegexUtils.findParamValueInString(param = queryParamName,
              string = x, defaultValue = "MISSING_VALUE"),
            n_best,
            n_worst
          )
        case "DO_NOTHING" => DoNothing()
      }
    }

    // TODO
    override def write(obj: Execution[Any]): JsValue = """{}""".toJson
  }

}
