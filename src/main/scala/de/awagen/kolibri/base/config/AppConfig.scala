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

package de.awagen.kolibri.base.config

import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.kolibri.base.actors.resources.{CPUBasedResourceChecker, ResourceChecker}
import de.awagen.kolibri.base.cluster.ClusterStatus
import de.awagen.kolibri.base.config.EnvVariableKeys.{IS_SINGLENODE, NODE_ROLES, POD_IP, PROFILE}
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.{CSVParameterBasedMetricDocumentFormat, MetricDocumentFormat}
import org.slf4j.{Logger, LoggerFactory}

import java.util
import scala.concurrent.duration._


/**
  * Object where config values are stored for the app
  */
object AppConfig {

  val logger: Logger = LoggerFactory.getLogger(AppConfig.getClass)

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
    val pod_ip = POD_IP.value
    logger.info(s"pod ip: $pod_ip")
    val roles: Array[String] = NODE_ROLES.value.split(",")
    val roleList = new util.ArrayList[String]()
    roles.foreach(x => roleList.add(x))
    val configValues = new java.util.HashMap[String, Any]()
    if (IS_SINGLENODE.value.toBoolean) {
      configValues.put("akka.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr", 1)
    }
    configValues.put("akka.cluster.singleton.use-lease", "")
    if (pod_ip.nonEmpty) configValues.put("akka.management.http.hostname", pod_ip)
    configValues.put("akka.cluster.roles", roleList)
    ConfigFactory.parseMap(configValues).withFallback(loadedConfig)
  }


  case class BaseConfig private(profile: String) {

    import EnvVariableKeys._

    // path relative to resource folder for singleton manager config
    val SINGLETON_MANAGER_CONFIG_PATH = "cluster_singleton/k-akka-singleton-manager.conf"
    // path relative to resource folder for singleton proxy config
    val SINGLETON_PROXY_CONFIG_PATH = "cluster_singleton/k-akka-singleton-proxy.conf"
    val SINGLETON_MANAGER_NAME = "singleton"
    val SINGLETON_MANAGER_PATH = s"/user/$SINGLETON_MANAGER_NAME"

    val HTTP_SERVER_ROLE = "httpserver"
    val http_server_host: String = HTTP_SERVER_INTERFACE.value
    val http_server_port: Int = HTTP_SERVER_PORT.value.toInt

    val baseConfig: Config = loadConfig(profile)

    val node_roles: Seq[String] = NODE_ROLES.value.split(",").toSeq

    final val applicationName: String = baseConfig.getString("kolibri.actor-system")

    val requestParallelism: Int = baseConfig.getInt("kolibri.request.parallelism")
    val useRequestTracking: Boolean = baseConfig.getBoolean("kolibri.request.useTracking")
    val useConnectionPoolFlow: Boolean = baseConfig.getBoolean("kolibri.request.useConnectionPoolFlow")

    val jobTimeout: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.timeoutInSeconds"), SECONDS)
    val jobProcessingCheckResourcesInterval: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.processingCheckResourcesIntervalInMillis"),
      MILLISECONDS)
    val runningTasksBaselineCount: Int = baseConfig.getInt("kolibri.job.runningTasksBaselineCount")
    val batchDistributionInterval: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.batchDistributionIntervalInMs"), MILLISECONDS)
    val batchMaxTimeToACKInMs: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.batchMaxTimeToACKInMs"), MILLISECONDS)

    val useAggregatorBackpressure: Boolean = baseConfig.getBoolean("kolibri.execution.useAggregatorBackpressure")
    val aggregatorResultReceiveParallelism: Int = baseConfig.getInt("kolibri.execution.aggregatorResultReceiveParallelism")

    // resourceCheckerGenerator
    val isValidResourceCheckerType: Boolean = baseConfig.hasPath("kolibri.job.resources.resourcesCheckerType") && baseConfig.getString("kolibri.job.resources.resourcesCheckerType").toLowerCase() == "cpu"
    val resourceCheckerGenerator: () => ResourceChecker = () => if (isValidResourceCheckerType) {
      new CPUBasedResourceChecker()
    } else {
      new ResourceChecker() {
        override def freeSlots(states: Seq[ClusterStatus]): Int = 0
      }
    }

    val clusterStatusCheckTimeout: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.cluster.statusCheckTimeoutInMillis"), MILLISECONDS)

    val supervisorMaxNumOfRetries: Int = baseConfig.getInt("kolibri.supervisor.maxNumOfRetries")

    val supervisorMaxNumOfRetriesWithinTime: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.supervisor.maxNumOfRetriesWithinTimeInSeconds"), SECONDS)

    val SUPERVISOR_BATCH_SIZE: Int = if (profile == "local") Int.MaxValue else 2000

    val waitForSynchronousTaskComplete: FiniteDuration = FiniteDuration(baseConfig.getInt("kolibri.job.tasks.waitForSynchronousTaskCompleteInMs"), MILLISECONDS)

    val formatType: String = baseConfig.getString("kolibri.format.metricDocumentFormatType")
    val metricDocumentFormat: MetricDocumentFormat = formatType match {
      case "parameter" =>
        CSVParameterBasedMetricDocumentFormat(columnSeparator = "\t")
      case _ =>
        CSVParameterBasedMetricDocumentFormat(columnSeparator = "\t")
    }

    val writerType: String = baseConfig.getString("kolibri.writer.type")

    val writerDirPath: String = baseConfig.getString("kolibri.writer.dirpath")

    val startClusterSingletonRouter: Boolean = baseConfig.getBoolean("kolibri.cluster.startClusterSingletonRouter")

    val supervisorHousekeepingInterval: FiniteDuration = getFiniteDuration(config = baseConfig,
      key = "kolibri.supervisor.houseKeepingIntervalInSeconds", SECONDS)

    val runnableExecutionActorHousekeepingInterval: FiniteDuration = getFiniteDuration(config = baseConfig,
      key = "kolibri.execution.runnableExecutionHouseKeepingIntervalInSeconds", SECONDS)

    val jobManagerCheckForCompletionIntervalInSeconds: FiniteDuration = getFiniteDuration(config = baseConfig,
      key = "kolibri.execution.jobManagerCheckForCompletionIntervalInSeconds", SECONDS)

    val internalJobStatusRequestTimeout: FiniteDuration = getFiniteDuration(config = baseConfig,
      key = "kolibri.internal.jobStatusRequestTimeoutInSeconds", SECONDS)

    val kolibriDispatcherName = "kolibri-dispatcher"
  }

}
