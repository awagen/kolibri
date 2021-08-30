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


package de.awagen.kolibri.base.processing.execution.functions

import de.awagen.kolibri.base.config.di.modules.Modules.PersistenceDIModule
import de.awagen.kolibri.base.io.reader.{DirectoryReader, FileReader}
import de.awagen.kolibri.base.io.writer.Writers.FileWriter
import de.awagen.kolibri.base.processing.execution.functions.FileUtils.regexDirectoryReader
import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.stores.PriorityStores._
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags._
import de.awagen.kolibri.datatypes.values.MetricValue

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


  case class GetImprovingAndLoosingFromDirPerRegex(persistenceDIModule: PersistenceDIModule,
                                                   dir: String,
                                                   fileRegex: Regex,
                                                   currentParams: Map[String, Seq[String]],
                                                   compareParams: Seq[Map[String, Seq[String]]],
                                                   metricName: String,
                                                   queryParamName: String,
                                                   n_best: Int,
                                                   n_worst: Int) extends Execution[ExecutionSummary[KeepNBestAndKWorst]] {
    val directoryReader: DirectoryReader = regexDirectoryReader(fileRegex)

    override def execute: Either[TaskFailType.TaskFailType, ExecutionSummary[KeepNBestAndKWorst]] = {
      val filteredFiles = directoryReader.listFiles(dir, _ => true)
      val execution = GetImprovingAndLoosing(
        persistenceDIModule,
        filteredFiles,
        currentParams,
        compareParams,
        metricName,
        queryParamName,
        n_best,
        n_worst)
      execution.execute
    }

  }

  case class GetImprovingAndLoosing(persistenceDIModule: PersistenceDIModule,
                                    compareFiles: Seq[String],
                                    currentParams: Map[String, Seq[String]],
                                    compareParams: Seq[Map[String, Seq[String]]],
                                    metricName: String,
                                    queryParamName: String,
                                    n_best: Int,
                                    n_worst: Int) extends Execution[ExecutionSummary[KeepNBestAndKWorst]] {
    val fileReader: FileReader = persistenceDIModule.fileReader
    val fileWriter: FileWriter[String, _] = persistenceDIModule.fileWriter

    override def execute: Either[TaskFailType.TaskFailType, ExecutionSummary[KeepNBestAndKWorst]] = {
      val result = KeepNBestAndKWorst(n_best, n_worst)
      val seq: Seq[Either[TaskFailType.TaskFailType, Seq[QueryParamValue]]] = compareFiles.map(file => {
        val document: MetricDocument[Tag] = FileUtils.fileToMetricDocument(file, fileReader, StringTag(""))
        var currentValueOpt: Option[MetricValue[Double]] = None
        var compareRows: Seq[MetricRow] = Seq.empty
        document.rows.foreach({
          case e if e._1 == currentParams => currentValueOpt = e._2.getMetricsValue(metricName)
          case e if compareParams.contains(e._1) => compareRows = compareRows :+ e._2
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
              val value: MetricValue[Double] = row.metrics(metricName)
              val diffRelativeToCurrent = value.biValue.value2.value - currentValue.biValue.value2.value
              QueryParamValue(row.params(queryParamName).head, row.params, diffRelativeToCurrent)
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
      Right(ExecutionSummary(result, failCount, successCount, immutable.Map(failCountMap.toSeq: _*)))
    }
  }

}
