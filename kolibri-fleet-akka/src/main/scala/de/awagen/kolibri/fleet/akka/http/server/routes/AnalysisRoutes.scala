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


package de.awagen.kolibri.fleet.akka.http.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher0, PathMatcher1, Route}
import de.awagen.kolibri.fleet.akka.config.AppConfig.persistenceModule
import de.awagen.kolibri.fleet.akka.config.AppProperties
import de.awagen.kolibri.fleet.akka.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.fleet.akka.io.json.ExecutionJsonProtocol._
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.base.processing.execution.functions.AnalyzeFunctions.{ExecutionSummary, GetImprovingAndLoosingFromDirPerRegex, GetValueVarianceFromDirPerRegex}
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.datatypes.stores.PriorityStores.{BasePriorityStore, PriorityStore}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

object AnalysisRoutes extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private[this] val directoryOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule
    .dataOverviewReader(_ => true)
  private[this] val contentReader: Reader[String, Seq[String]] = persistenceModule.persistenceDIModule.reader
  private[this] val outputResultsPath: String = AppProperties.config.outputResultsPath.get
    .stripSuffix("/")
  private[this] val RESULT_PREFIX = "results"
  private[this] val ANALYZE_PREFIX = "analyze"
  private[this] val EXECUTIONS_PATH = "executions"
  private[this] val RESULT_IDENTIFIER_KEY = "id"

  private[this] val DIRECTORY_PATH_DELIMITER = "/"

  private[this] val FAIL_COUNT_PREFIX: String = "fail-count-"
  private[this] val WEIGHTED_FAIL_COUNT_PREFIX: String = "weighted-fail-count-"
  private[this] val FAIL_REASONS_PREFIX: String = "failReasons-"
  private[this] val SUCCESS_COUNT_PREFIX: String = "success-count-"
  private[this] val WEIGHTED_SUCCESS_COUNT_PREFIX: String = "weighted-success-count-"
  private[this] val VALUE_PREFIX: String = "value-"

  private[this] val SORT_BY_METRIC_PARAM = "sortByMetric"
  private[this] val TOP_N_PARAM = "topN"
  private[this] val REVERSED_PARAM = "reversed"
  private[this] val TOPS_FLOPS_PATH = "topsflops"
  private[this] val VARIANCES_PATH = "variances"

  private[this] val HIGHEST_VALUES_KEY = "highest"
  private[this] val LOWEST_VALUES_KEY = "lowest"

  private[this] val evaluationColumnNames: Seq[String] = Seq(
    FAIL_COUNT_PREFIX,
    WEIGHTED_FAIL_COUNT_PREFIX,
    FAIL_REASONS_PREFIX,
    SUCCESS_COUNT_PREFIX,
    WEIGHTED_SUCCESS_COUNT_PREFIX,
    VALUE_PREFIX
  )

  // overview of existing results.
  // level 1: experiment folder
  // level 2: overview of single files in that folder
  // level 3: retrieve content of a file
  // level 3.1: retrieve filtered content of a file
  // level 4: analyze global properties of a file (e.g per metric variance observed within a group,
  // highest increases / decreases per group (e.g query level), ...)

  /**
   * Get all folders corresponding to single experiments
   *
   * @return
   */
  def getResultBaseFolders(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = RESULT_PREFIX / EXECUTIONS_PATH
    corsHandler(
      path(matcher) {
        get {
          val resources = readResourcesFromPath(s"$outputResultsPath", "")
          logger.debug(s"found resources: $resources")
          complete(StatusCodes.OK, resources.toJson.toString())
        }
      })
  }

  /**
   * For a specific experimentId (e.g experiment folder name) retrieve
   * all partial result file names
   *
   * TODO: this gives 405 if the execution path contains any "-", thus make
   * sure to properly normalize the paths (e.g set "-" to "_" or the like)
   *
   * @return
   */
  def getPartialResultsOverview(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher1[String] = RESULT_PREFIX / EXECUTIONS_PATH / """(\w+)""".r
    corsHandler(
      path(matcher) { executionId: String =>
        get {
          val resources = readResourcesFromPath(s"$outputResultsPath/$executionId")
          logger.debug(s"found resources: $resources")
          complete(StatusCodes.OK, resources.toJson.toString())
        }
      })
  }

  private[this] def readResourcesFromPath(path: String, fileSuffix: String = ".json"): Seq[String] = {
    directoryOverviewReader.listResources(
      path, x => x.toLowerCase.endsWith(fileSuffix)
    ).map(filepath => filepath.split(DIRECTORY_PATH_DELIMITER).last.trim)
  }

  case class CsvFileContent(paramNames: Seq[String], metricNames: Seq[String], columnNames: Seq[String], dataLinesAsColumns: Seq[Seq[String]]) {

    /**
     * Allow returning a sorted variant of the current CsvFileContent. If maxRows > 0, specifies how many of the
     * top rows are returned
     *
     * @param metricName - the metric name to sort by
     * @param maxRows    - maximal nr of rows to return
     * @param reversed   - if true, would return sorted by worst results
     * @return
     */
    def sortedByMetric(metricName: String, maxRows: Int = -1, reversed: Boolean = false): CsvFileContent = {
      val metricValueColumnName = s"$VALUE_PREFIX$metricName"
      if (!metricNames.contains(metricName)) {
        throw new IllegalArgumentException(s"metricName '$metricName' not contained in available metric names '$metricNames'")
      }
      // now find column index, and define a limited queue and throw in all single results
      val metricIndex: Int = columnNames.indexOf(metricValueColumnName)
      val comparatorFunc: Ordering[Seq[String]] = (x, y) => {
        if (reversed) if (x(metricIndex).toFloat > y(metricIndex).toFloat) -1 else 1
        else if (x(metricIndex).toFloat > y(metricIndex).toFloat) 1 else -1
      }
      // we are sorting by shooting all rows thru priority store that will just keep topN according to
      // passed sorting criterion
      val nrRows = if (maxRows > 0) maxRows else this.dataLinesAsColumns.size
      val prioStore: PriorityStore[String, Seq[String]] = BasePriorityStore(nrRows, comparatorFunc, _ => "top")
      dataLinesAsColumns.foreach(x => prioStore.addEntry(x))
      this.copy(dataLinesAsColumns = prioStore.result("top"))
    }
  }

  implicit val csvFileContentFormat: RootJsonFormat[CsvFileContent] = jsonFormat4(CsvFileContent)


  private[this] def readJsonFromPath(path: String): JsValue = {
    contentReader.read(path).mkString("\n").parseJson
  }

  private[this] def readCsvFromPath(path: String,
                                    extractParamNamesFunc: Seq[String] => Seq[String] = paramNamesFromFirstLineFunc,
                                    extractMetricNamesFunc: Seq[String] => Seq[String] = containedMetricNamesFunc,
                                    extractColumnNamesFunc: Seq[String] => Seq[String] = columnNamesFromFirstLine,
                                    extractDataFunc: Seq[String] => Seq[Seq[String]] = dataAsLinesWithoutFirstAndSplitColumnsFunc): CsvFileContent = {
    val lines: Seq[String] = contentReader.read(path)
      .filter(line => !line.startsWith("#"))
    val columnNames = extractColumnNamesFunc.apply(lines)
    val metricNames = extractMetricNamesFunc.apply(columnNames)
    val paramNames = extractParamNamesFunc.apply(lines)
    val data = extractDataFunc.apply(lines)
    CsvFileContent(paramNames, metricNames, columnNames, data)
  }

  private[this] val columnNamesFromFirstLine: Seq[String] => Seq[String] = x => x.head.split("\t")

  private[this] val paramNamesFromFirstLineFunc: Seq[String] => Seq[String] = x => x.head.split("\t")
    .filter(colName =>
      !evaluationColumnNames.exists(name => colName.startsWith(name))
    )

  private[this] val dataAsLinesWithoutFirstAndSplitColumnsFunc: Seq[String] => Seq[Seq[String]] = {
    x => x.tail.map(x => x.split("\t"))
  }

  private[this] val containedMetricNamesFunc: Seq[String] => Seq[String] = {
    x =>
      x.filter(name => name.startsWith(VALUE_PREFIX))
        .map(name => name.stripPrefix(VALUE_PREFIX))
  }

  /**
   * Retrieve a single partial or aggregated result file
   *
   * @param system
   * @return
   */
  def getSingleJsonResult(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher1[String] = RESULT_PREFIX / EXECUTIONS_PATH / """(\w+)""".r / "byId"
    corsHandler(
      path(matcher) { executionId =>
        get {
          parameters(RESULT_IDENTIFIER_KEY) {
            resultId => {
              val results = readJsonFromPath(s"$outputResultsPath/$executionId/$resultId")
              logger.debug(s"found resources: $results")
              complete(StatusCodes.OK, results.toString())
            }
          }
        }
      })
  }

  def getSingleCsvResultFiltered(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher1[String] = RESULT_PREFIX / EXECUTIONS_PATH / """(\w+)""".r / "byIdFiltered"
    corsHandler(
      path(matcher) { executionId =>
        get {
          parameters(
            RESULT_IDENTIFIER_KEY,
            SORT_BY_METRIC_PARAM,
            TOP_N_PARAM ? 50,
            REVERSED_PARAM ? false) {
            (resultId, sortByMetric, topN, reversed) => {
              val results: CsvFileContent = readCsvFromPath(s"$outputResultsPath/$executionId/$resultId")
              // now sort the columns such as to retrieve the
              if (!results.metricNames.contains(sortByMetric)) {
                logger.debug(s"found resources: $results")
                complete(StatusCodes.BadRequest, s"passed metric '$sortByMetric' not contained in available" +
                  s"metric names '${results.metricNames}'")
              }
              val sortedResults = results.sortedByMetric(metricName = sortByMetric, maxRows = topN, reversed = reversed)
              complete(StatusCodes.OK, sortedResults.toJson.toString())
            }
          }
        }
      })
  }

  /**
   * Compute for given results the top winning / loosing queries, determined by
   * comparing the values for the current parameter set with the parameters to compare
   * against
   *
   * @param system
   * @return
   */
  def getImproovingAndLoosing(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = ANALYZE_PREFIX / TOPS_FLOPS_PATH
    corsHandler(
      path(matcher) {
        post {
          entity(as[Execution[Any]]) { analysisDef => {
            val castExecution = analysisDef.asInstanceOf[GetImprovingAndLoosingFromDirPerRegex]
            val executionId = castExecution.dir.stripSuffix("/")
            // NOTE: here we overwrite the dir to contain the outputResultsPath, its not the same as
            // executing the execution above thru its own Execution endpoint
            val execution = castExecution.copy(dir = s"$outputResultsPath/$executionId")
            val result: Either[TaskFailType.TaskFailType, ExecutionSummary[Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]]]] = execution.execute
            result match {
              case Left(failType) =>
                complete(StatusCodes.InternalServerError, s"Analysis failed with failType: '$failType'")
              case Right(summary) =>

                val highest: Map[Map[String, Seq[String]], Seq[(String, String)]] = summary.result.find(x => x._1 == HIGHEST_VALUES_KEY).get._2
                val lowest: Map[Map[String, Seq[String]], Seq[(String, String)]] = summary.result.find(x => x._1 == LOWEST_VALUES_KEY).get._2
                val uniqueParamKeys: Set[Map[String, Seq[String]]] = highest.keySet ++ lowest.keySet
                val results: Set[ResultForParameterSet] = uniqueParamKeys.map(paramsMapping => {
                  val highestValued: Seq[IdWithValue] = highest.get(paramsMapping)
                    .map(pairSeq => pairSeq
                      .filter(x => x._2.toFloat > 0.0)
                      .map(x => IdWithValue(x._1, x._2))
                    )
                    .getOrElse(Seq.empty)
                  val lowestValued: Seq[IdWithValue] = lowest.get(paramsMapping)
                    .map(pairSeq =>
                      pairSeq
                        .filter(x => x._2.toFloat < 0.0)
                        .map(x => IdWithValue(x._1, x._2))
                    ).getOrElse(Seq.empty)
                  ResultForParameterSet(parameters = paramsMapping, winning = highestValued, loosing = lowestValued)
                })
                complete(StatusCodes.OK, results.toJson.toString())
            }
          }
          }
        }
      })
  }

  case class IdWithValue(id: String, value: String)

  case class ResultForParameterSet(parameters: Map[String, Seq[String]],
                                   winning: Seq[IdWithValue],
                                   loosing: Seq[IdWithValue])

  implicit val idWithValueFormat: RootJsonFormat[IdWithValue] = jsonFormat2(IdWithValue)
  implicit val resultForParameterSetFormat: RootJsonFormat[ResultForParameterSet] = jsonFormat3(ResultForParameterSet)


  /**
   * Pick result files from directory and calculate variances.
   *
   * @return Group id and variance value on selected metric in descending order.
   */
  def getValueVarianceFromDir(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = ANALYZE_PREFIX / VARIANCES_PATH
    corsHandler(
      path(matcher) {
        post {
          entity(as[Execution[Any]]) { analysisDef => {
            val castExecution = analysisDef.asInstanceOf[GetValueVarianceFromDirPerRegex]
            val executionId = castExecution.dir.stripSuffix("/")
            // NOTE: here we overwrite the dir to contain the outputResultsPath, its not the same as
            // executing the execution above thru its own Execution endpoint
            val execution = castExecution.copy(dir = s"$outputResultsPath/$executionId")
            val results: Either[TaskFailType.TaskFailType, ExecutionSummary[Seq[(String, Double)]]] = execution.execute
            results match {
              case Left(failType) =>
                complete(StatusCodes.InternalServerError, s"Analysis failed with failType: '$failType'")
              case Right(values) =>
                val varianceSortedSeq = values.result
                  .sortBy[Double](x => x._2)
                  .reverseIterator
                  .map(x => IdWithValue(x._1, "%.4f".formatLocal(java.util.Locale.US, x._2)))
                  .toSeq
                complete(StatusCodes.OK, varianceSortedSeq.toJson.toString())
            }
          }
          }
        }
      }
    )
  }

}