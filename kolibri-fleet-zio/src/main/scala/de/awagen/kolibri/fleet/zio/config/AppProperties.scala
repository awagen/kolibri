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

package de.awagen.kolibri.fleet.zio.config

import com.amazonaws.regions.Regions
import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.{CSVParameterBasedMetricDocumentFormat, JsonMetricDocumentFormat, MetricDocumentFormat}
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.datatypes.types.JsonTypeCast.JsonTypeCast
import de.awagen.kolibri.fleet.zio.config.EnvVariableKeys.PROFILE
import org.slf4j.{Logger, LoggerFactory}

import java.util.Objects
import scala.concurrent.duration._
import scala.util.Random


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
    val configValues = new java.util.HashMap[String, Any]()
    ConfigFactory.parseMap(configValues).withFallback(loadedConfig)
  }


  case class BaseConfig private(profile: String) {

    import EnvVariableKeys._

    val baseConfig: Config = loadConfig(profile)

    final val applicationName: String = baseConfig.getString("kolibri.appName")

    val http_server_port: Int = HTTP_SERVER_PORT.value.toInt

    val requestParallelism: Int = baseConfig.getInt("kolibri.request.parallelism")

    val batchStateUpdateInitialDelayInSeconds = baseConfig.getInt("kolibri.job.batchStateUpdateInitialDelayInSeconds")
    val batchStateUpdateIntervalInSeconds = baseConfig.getInt("kolibri.job.batchStateUpdateIntervalInSeconds")
    val jobProcessingCheckResourcesInterval: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.processingCheckResourcesIntervalInMillis"),
      MILLISECONDS)
    val runningTasksPerJobMaxCount: Int = baseConfig.getInt("kolibri.job.runningTasksPerJobMaxCount")
    val runningTasksPerJobDefaultCount: Int = baseConfig.getInt("kolibri.job.runningTasksPerJobDefaultCount")

    val allowedTimePerElementInMillis: Int = baseConfig.getInt("kolibri.job.allowedTimePerElementInMillis")
    val allowedTimePerBatchInSeconds: Int = baseConfig.getInt("kolibri.job.allowedTimePerBatchInSeconds")
    val allowedTimePerJobInSeconds: Int = baseConfig.getInt("kolibri.job.allowedTimePerJobInSeconds")

    // max time allowed for resource directive to be processed
    val maxResourceDirectiveLoadTimeInMinutes: Int = baseConfig.getInt("kolibri.job.resources.directives.maxLoadTimeInMinutes")
    // some properties determining amount of additional judgement information is stored
    // in judgement provider
    val topKJudgementsPerQueryStorageSize: Int = baseConfig.getInt("kolibri.job.resources.judgements.topKJudgementsPerQueryStorageSize")

    val waitForSynchronousTaskComplete: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.tasks.waitForSynchronousTaskCompleteInMs"), MILLISECONDS)

    val formatTypes: Seq[String] = baseConfig.getString("kolibri.format.metricDocumentFormatTypes").split(",")
      .map(x => x.trim.toLowerCase).filter(x => x.nonEmpty).toSeq
    val metricDocumentFormats: Seq[MetricDocumentFormat] = formatTypes.map {
      case "csv" => CSVParameterBasedMetricDocumentFormat(columnSeparator = "\t")
      case "json" => new JsonMetricDocumentFormat()
      case _ => null
    }.filter(Objects.nonNull)

    val metricDocumentFormatsMap: Map[String, MetricDocumentFormat] = metricDocumentFormats.map(x => (x.identifier.toLowerCase, x)).toMap

    val internalJobStatusRequestTimeout: FiniteDuration = getFiniteDuration(config = baseConfig,
      key = "kolibri.internal.jobStatusRequestTimeoutInSeconds", SECONDS)

    val analyzeTimeout: FiniteDuration = getFiniteDuration(config = baseConfig,
      key = "kolibri.internal.analyzeTimeoutInSeconds", SECONDS)

    val maxNrBatchRetries: Int = baseConfig.getInt("kolibri.execution.maxNrBatchRetries")

    def safeGetString(path: String): Option[String] = {
      if (baseConfig.hasPath(path)) Some(baseConfig.getString(path))
      else None
    }

    val awsS3Bucket: Option[String] = safeGetString("kolibri.persistence.s3.bucket")

    val awsS3BucketPath: Option[String] = safeGetString("kolibri.persistence.s3.bucketPath")

    val awsS3Region: Option[Regions] = safeGetString("kolibri.persistence.s3.region").map(x => Regions.valueOf(x))

    val gcpGSBucket: Option[String] = safeGetString("kolibri.persistence.gs.bucket")

    val gcpGSBucketPath: Option[String] = safeGetString("kolibri.persistence.gs.bucketPath")

    val gcpGSProjectID: Option[String] = safeGetString("kolibri.persistence.gs.projectID")

    val localPersistenceWriteBasePath: Option[String] = safeGetString("kolibri.persistence.local.writeBasePath")

    val localPersistenceWriteResultsSubPath: Option[String] = safeGetString("kolibri.persistence.local.writeResultsSubPath")

    val localPersistenceReadBasePath: Option[String] = safeGetString("kolibri.persistence.local.readBasePath")

    val localResourceReadBasePath: Option[String] = safeGetString("kolibri.persistence.local.resources.readBasePath")

    val jobTemplatesPath: Option[String] = safeGetString("kolibri.persistence.templates.jobTemplatesPath")
    val inputDataPath: Option[String] = safeGetString("kolibri.persistence.inputs.dataPath")
    val outputResultsPath: Option[String] = safeGetString("kolibri.persistence.outputs.resultsPath")

    val persistenceMode: String = baseConfig.getString("kolibri.persistence.mode")
    val persistenceModuleClass: Option[String] = safeGetString("kolibri.persistence.moduleClass")

    val directoryPathSeparator: String = baseConfig.getString("kolibri.persistence.directoryPathSeparator")
    val csvColumnSeparator: String = baseConfig.getString("kolibri.persistence.csvColumnSeparator")

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

    // hosts set as allowed to fire requests on.
    // wildcard "*" specifies all hosts are allowed. Config value shall contain
    // comma-separated host names
    // TODO: incorporate the below for configs / config checks
    // and refuse in case the limitations are not met
    val allowedRequestTargetHosts: Seq[String] = baseConfig.getString("kolibri.request.target.allowedHosts").split(",")
      .map(x => x.trim).filter(x => x.nonEmpty)
    val allowedRequestTargetPorts: Seq[String] = baseConfig.getString("kolibri.request.target.allowedPorts").split(",")
      .map(x => x.trim).filter(x => x.nonEmpty)
    val allowedRequestTargetContextPaths: Seq[String] = baseConfig.getString("kolibri.request.target.allowedContextPaths").split(",")


    // --- jobs folder relative to readers / writers base folder
    // base folder where jobs are stored
    val jobBaseFolder: String = baseConfig.getString("kolibri.job.basefolder").stripPrefix("/").stripSuffix("/")
    // the base folder where the open (not yet fully processed) jobs are stored (every job has own folder
    // named by [jobName]_[timePlacedInMillis]
    val openJobBaseFolder: String = s"$jobBaseFolder/${baseConfig.getString("kolibri.job.openJobsSubfolder").stripPrefix("/").stripSuffix("/")}"
    // the base folder where the processed jobs are stored
    val doneJobBaseFolder: String = s"$jobBaseFolder/${baseConfig.getString("kolibri.job.doneJobsSubfolder").stripPrefix("/").stripSuffix("/")}"

    // base folder within a given job folder where tasks are stored (thus this folder is meant to be relative
    // to a specific job folder that resides either within the openJobBaseFolder or doneJobBaseFolder)
    val perJobTaskBaseFolder: String = baseConfig.getString("kolibri.job.tasks.basefolder").stripPrefix("/").stripSuffix("/")
    val perJobClaimBaseFolder: String = s"$perJobTaskBaseFolder/${baseConfig.getString("kolibri.job.tasks.claimSubFolder").stripPrefix("/").stripSuffix("/")}"
    val perJobOpenTaskBaseFolder: String = s"$perJobTaskBaseFolder/${baseConfig.getString("kolibri.job.tasks.openTasksSubFolder").stripPrefix("/").stripSuffix("/")}"
    val perJobDoneTaskBaseFolder: String = s"$perJobTaskBaseFolder/${baseConfig.getString("kolibri.job.tasks.doneTasksSubFolder").stripPrefix("/").stripSuffix("/")}"
    val perJobTaskProgressStateBaseFolder: String = s"$perJobTaskBaseFolder/${baseConfig.getString("kolibri.job.tasks.inProgressStateTasksSubFolder").stripPrefix("/").stripSuffix("/")}"


    // node-hash to identify and distinguish claims and processing states from
    // distinct nodes
    val node_hash: String = safeGetString("kolibri.nodeHash").getOrElse(
      Random.alphanumeric.take(6).mkString
    )

    val maxNrJobsClaimed: Int = baseConfig.getInt("kolibri.job.tasks.maxNrJobsClaimed")
    val maxNrJobsProcessing: Int = baseConfig.getInt("kolibri.job.tasks.maxNrJobsProcessing")
    val pulledTaskHistorySize: Int = baseConfig.getInt("kolibri.job.tasks.maxNrJobsHistory")

    val maxParallelItemsPerBatch: Int = baseConfig.getInt("kolibri.execution.maxParallelItemsPerBatch")

    val maxRetriesPerBatchTask: Int = baseConfig.getInt("kolibri.execution.maxBatchTaskRetries")

  }

}
