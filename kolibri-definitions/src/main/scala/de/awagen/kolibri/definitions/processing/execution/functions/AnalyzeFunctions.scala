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


package de.awagen.kolibri.definitions.processing.execution.functions

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.stores.mutable.PriorityStores._
import de.awagen.kolibri.datatypes.tagging.Tags._
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer

import scala.collection.{immutable, mutable}
import scala.util.matching.Regex

object AnalyzeFunctions {

  case class QueryParamValue(query: String, params: Map[String, Seq[String]], value: Double)

  case class KeepNBestAndKWorst(n_best: Int, k_worst: Int, valueFormat: Double => String = x => f"$x%1.4f") {
    val HIGHEST_RESULT_KEY = "highest"
    val LOWEST_RESULT_KEY = "lowest"

    private[this] val highestPrioStore: PriorityStore[Map[String, Seq[String]], QueryParamValue] =
      BasePriorityStore(n_best, (x, y) => if (x.value > y.value) 1 else -1, x => x.params)
    private[this] val lowestPrioStore: PriorityStore[Map[String, Seq[String]], QueryParamValue] =
      BasePriorityStore(k_worst, (x, y) => if (x.value > y.value) -1 else 1, x => x.params)

    def accept(value: QueryParamValue): Unit = {
      highestPrioStore.addEntry(value)
      lowestPrioStore.addEntry(value)
    }

    private[functions] def prioStoreToTuple(prioStore: PriorityStore[Map[String, Seq[String]], QueryParamValue]): Map[Map[String, Seq[String]], Seq[(String, String)]] = {
      prioStore.result.view.mapValues(x => x.map(y => (y.query, valueFormat.apply(y.value)))).toMap
    }

    def result: Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]] =
      Map(HIGHEST_RESULT_KEY -> prioStoreToTuple(highestPrioStore), LOWEST_RESULT_KEY -> prioStoreToTuple(lowestPrioStore))

  }

  case class ExecutionSummary[+T](result: T, failed: Int, success: Int, failTypeCounts: Map[TaskFailType, Int])


  case class GetImprovingAndLoosingFromDirPerRegex(directoryReaderFunc: Regex => DataOverviewReader,
                                                   fileReader: Reader[String, Seq[String]],
                                                   fileWriter: Writer[String, String, _],
                                                   dir: String,
                                                   fileRegex: Regex,
                                                   currentParams: Map[String, Seq[String]],
                                                   compareParams: Seq[Map[String, Seq[String]]],
                                                   metricName: String,
                                                   queryFromFilename: String => String,
                                                   n_best: Int,
                                                   n_worst: Int) extends Execution[ExecutionSummary[Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]]]] {
    lazy val directoryReader: DataOverviewReader = directoryReaderFunc(fileRegex)

    override def execute: Either[TaskFailType.TaskFailType, ExecutionSummary[Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]]]] = {
      val filteredFiles = directoryReader.listResources(dir, _ => true)
      val execution = GetImprovingAndLoosing(
        fileReader,
        fileWriter,
        filteredFiles,
        currentParams,
        compareParams,
        metricName,
        queryFromFilename,
        n_best,
        n_worst)
      execution.execute
    }

  }

  /**
   * Execution to compute the variance for a given metric per single result file (single query results)
   * and return queries and the calculated variance sorted ascending by
   * variance
   *
   * @param dir               - the result (sub)folder
   * @param fileRegex         - the regex to determine which result files to take into account
   * @param metricName        - metric name to calculate variance by
   * @param queryFromFilename - function to determine the query from the filename
   */
  case class GetValueVarianceFromDirPerRegex(directoryReaderFunc: Regex => DataOverviewReader,
                                             fileReader: Reader[String, Seq[String]],
                                             dir: String,
                                             fileRegex: Regex,
                                             metricName: String,
                                             queryFromFilename: String => String) extends Execution[ExecutionSummary[Seq[(String, Double)]]] {
    lazy val directoryReader: DataOverviewReader = directoryReaderFunc(fileRegex)

    override def execute: Either[TaskFailType, ExecutionSummary[Seq[(String, Double)]]] = {
      val filteredFiles: Seq[String] = directoryReader.listResources(dir, _ => true)
      var queryVariancePairs: Seq[(String, Double)] = Seq.empty
      val csvFormat: CSVParameterBasedMetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")

      filteredFiles.foreach(file => {
        // TODO: make the file format selectable or define via file ending
        val document: MetricDocument[Tag] = FileUtils.fileToMetricDocument(file, fileReader, csvFormat)
        // TODO: fix, since not every metric is double by now
        val singleValues: Seq[Double] = document.rows.values.map(x => x.metrics(metricName).biValue.value2.value).map(x => x.asInstanceOf[Double]).toSeq
        val mean = if (singleValues.isEmpty) 0 else singleValues.sum / singleValues.size
        val variance = if (singleValues.isEmpty) 0 else singleValues.map(x => math.pow(x - mean, 2.0)).sum / singleValues.size
        queryVariancePairs = queryVariancePairs :+ (queryFromFilename(file), variance)
      })
      Right(ExecutionSummary(queryVariancePairs.sorted((x: (String, Double), y: (String, Double)) => x._2 compare y._2), 0, filteredFiles.size, immutable.Map.empty))
    }
  }

  /**
   * Generates over the files to compare and for all parameter sets the most winning / loosing queries compared to
   * the values corresponding to current parameters.
   * Result has HIGHEST_RESULT_KEY and LOWEST_RESULT_KEY and for each a mapping parameterMap (of compareParams) ->
   * (query, double (formatted string)) tuples
   *
   * @param compareFiles
   * @param currentParams
   * @param compareParams
   * @param metricName
   * @param queryFromFilename
   * @param n_best
   * @param n_worst
   */
  case class GetImprovingAndLoosing(fileReader: Reader[String, Seq[String]],
                                    fileWriter: Writer[String, String, _],
                                    compareFiles: Seq[String],
                                    currentParams: Map[String, Seq[String]],
                                    compareParams: Seq[Map[String, Seq[String]]],
                                    metricName: String,
                                    queryFromFilename: String => String,
                                    n_best: Int,
                                    n_worst: Int) extends Execution[ExecutionSummary[Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]]]] {

    val csvFormat: CSVParameterBasedMetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")

    override def execute: Either[TaskFailType.TaskFailType, ExecutionSummary[Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]]]] = {
      val result = KeepNBestAndKWorst(n_best, n_worst)
      val seq: Seq[Either[TaskFailType.TaskFailType, Seq[QueryParamValue]]] = compareFiles.map(file => {
        // TODO: make format selectable or detect via file ending
        val document: MetricDocument[Tag] = FileUtils.fileToMetricDocument(file, fileReader, csvFormat)
        var currentValueOpt: Option[MetricValue[Double]] = None
        var compareRows: Seq[MetricRow] = Seq.empty
        document.rows.foreach({
          case e if e._1 == currentParams => currentValueOpt = e._2.getMetricsValue(metricName).map(x => x.asInstanceOf[MetricValue[Double]])
          case e if compareParams.contains(e._1) => compareRows = compareRows :+ e._2
          case _ => // do nothing
        })
        // need to have currentRow set and any nr of rows in compareRows to be able to compare
        if (currentValueOpt.isEmpty) {
          Left(TaskFailType.FailedByException(new RuntimeException("missing value for currentParams")))
        }
        else if (compareRows.isEmpty) {
          Left(TaskFailType.FailedByException(new RuntimeException("empty compareParams")))
        }
        else {
          val currentValue = currentValueOpt.get
          val settingsToDiff: Seq[QueryParamValue] = compareRows
            .filter(row => row.metrics.contains(metricName))
            .map(row => {
              val value: MetricValue[Double] = row.metrics(metricName).asInstanceOf[MetricValue[Double]]
              val diffRelativeToCurrent = value.biValue.value2.value - currentValue.biValue.value2.value
              QueryParamValue(queryFromFilename.apply(file), row.params, diffRelativeToCurrent)
            })
          Right(settingsToDiff)
        }
      })
      var failCount: Int = 0
      var successCount: Int = 0
      val failCountMap: mutable.Map[TaskFailType, Int] = mutable.Map.empty
      seq.foreach({
        case Left(e) => // do nothing
          failCount += 1
          failCountMap(e) = failCountMap.getOrElse(e, 0) + 1
        case Right(e) =>
          successCount += 1
          e.foreach(r => result.accept(r))
      })
      val resultValue = ExecutionSummary(result.result, failCount, successCount, immutable.Map(failCountMap.toSeq: _*))
      Right(resultValue)
    }
  }

}
