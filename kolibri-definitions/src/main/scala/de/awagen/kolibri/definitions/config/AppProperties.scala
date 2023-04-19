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

package de.awagen.kolibri.definitions.config

import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.kolibri.definitions.config.EnvVariableKeys.PROFILE
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.datatypes.types.JsonTypeCast.JsonTypeCast
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._


/**
  * Object where config values are stored for the app
  */
object AppProperties {

  private[this] val logger: Logger = LoggerFactory.getLogger(AppProperties.getClass)

  val profile: String = PROFILE.value
  val config: BaseConfig = BaseConfig(profile)

  def getFiniteDuration(config: Config, key: String, timeUnit: TimeUnit): FiniteDuration = {
    FiniteDuration(config.getInt(key), timeUnit)
  }

  def loadConfig(profile: String): Config = {
    val configName: String = s"application-$profile.conf"
    logger.info(s"loading config: $configName")
    val loadedConfig: Config = ConfigFactory.load(configName)
    logger.debug(s"loaded config: ${loadedConfig.toString}")
    loadedConfig
  }


  case class BaseConfig private(profile: String) {

    def safeGetString(path: String): Option[String] = {
      if (baseConfig.hasPath(path)) Some(baseConfig.getString(path))
      else None
    }

    val baseConfig: Config = loadConfig(profile)

    val judgementSourceType: String = baseConfig.getString("kolibri.format.judgements.sourceType")
    val judgementQueryAndProductDelimiter: String = baseConfig.getString("kolibri.format.judgements.judgementKeyQueryAndProductDelimiter")
    val judgementCSVFileColumnDelimiter: String = baseConfig.getString("kolibri.format.judgements.csv.judgementFileColumnDelimiter")
    val judgementCSVFileIgnoreLessThanColumns: Int = baseConfig.getInt("kolibri.format.judgements.csv.judgementFileIgnoreLessThanColumns")
    val judgementCSVFileJudgementColumn: Int = baseConfig.getInt("kolibri.format.judgements.csv.judgementFileJudgementColumn")
    val judgementCSVFileSearchTermColumn: Int = baseConfig.getInt("kolibri.format.judgements.csv.judgementFileSearchTermColumn")
    val judgementCSVFileProductIdColumn: Int = baseConfig.getInt("kolibri.format.judgements.csv.judgementFileProductIdColumn")

    val judgementJsonLinesPlainQueryPath: Seq[String] = baseConfig.getString("kolibri.format.judgements.jsonLines.queryPath")
      .split(",").map(x => x.trim).filter(x => x.nonEmpty)
    val judgementJsonLinesPlainProductsPath: Seq[String] = baseConfig.getString("kolibri.format.judgements.jsonLines.productsPath")
      .split(",").map(x => x.trim).filter(x => x.nonEmpty)
    val judgementJsonLinesPlainProductIdSelector: String = baseConfig.getString("kolibri.format.judgements.jsonLines.productIdSelector")
    val judgementJsonLinesPlainJudgementSelector: String = baseConfig.getString("kolibri.format.judgements.jsonLines.judgementSelector")
    val judgementJsonLinesJudgementValueTypeCast: JsonTypeCast.Val = JsonTypeCast.withName(baseConfig.getString("kolibri.format.judgements.jsonLines.judgementValueTypeCast")).asInstanceOf[JsonTypeCast]

    val allowedTimePerElementInMillis: Int = baseConfig.getInt("kolibri.job.allowedTimePerElementInMillis")
    val allowedTimePerBatchInSeconds: Int = baseConfig.getInt("kolibri.job.allowedTimePerBatchInSeconds")
    val allowedTimePerJobInSeconds: Int = baseConfig.getInt("kolibri.job.allowedTimePerJobInSeconds")

    val outputResultsPath: Option[String] = safeGetString("kolibri.persistence.outputs.resultsPath")

    val allowedRequestTargetHosts: Seq[String] = baseConfig.getString("kolibri.request.target.allowedHosts").split(",")
      .map(x => x.trim).filter(x => x.nonEmpty)
    val allowedRequestTargetPorts: Seq[String] = baseConfig.getString("kolibri.request.target.allowedPorts").split(",")
      .map(x => x.trim).filter(x => x.nonEmpty)
    val allowedRequestTargetContextPaths: Seq[String] = baseConfig.getString("kolibri.request.target.allowedContextPaths").split(",")


  }

}
