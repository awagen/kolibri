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

package de.awagen.kolibri.datatypes.metrics.aggregation.writer

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat._
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.values.RunningValue.RunningValueAdd.{doubleAvgAdd, doubleAvgWeightFunction, errorMapAdd, failMapWeightFon}
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue, RunningValue}


object CSVParameterBasedMetricDocumentFormat {
  val FAIL_COUNT_COLUMN_PREFIX = "fail-count-"
  val WEIGHTED_FAIL_COUNT_COLUMN_PREFIX = "weighted-fail-count-"
  val FAIL_REASONS_COLUMN_PREFIX = "failReasons-"
  val SUCCESS_COUNT_COLUMN_PREFIX = "success-count-"
  val WEIGHTED_SUCCESS_COUNT_COLUMN_PREFIX = "weighted-success-count-"
  val VALUE_COLUMN_PREFIX = "value-"
  val INVALID_ID = "invalidID"
  val MULTI_VALUE_SEPARATOR = "&"
}

case class CSVParameterBasedMetricDocumentFormat(columnSeparator: String) extends MetricDocumentFormat {

  override def metricDocumentToString(ma: MetricDocument[_]): String = {
    val paramNames = ma.getParamNames.toSeq.sorted
    val metricNames = ma.getMetricNames.toSeq.sorted
    val header = formatHeader(paramNames, metricNames)
    val rowSeq = ma.rows.keys.map(x => Seq(formatRow(ma.rows(x), paramNames, metricNames))
      .mkString(columnSeparator)).toSeq
    (Seq(header) ++ rowSeq).mkString("\n")
  }

  def formatHeader(paramNames: Seq[String], metricNames: Seq[String]): String = {
    var header = Seq.empty[String]
    header ++= paramNames
    metricNames
      .foreach(x => {
        header = header ++ Seq(
          s"$FAIL_COUNT_COLUMN_PREFIX$x",
          s"$WEIGHTED_FAIL_COUNT_COLUMN_PREFIX$x",
          s"$FAIL_REASONS_COLUMN_PREFIX$x",
          s"$SUCCESS_COUNT_COLUMN_PREFIX$x",
          s"$WEIGHTED_SUCCESS_COUNT_COLUMN_PREFIX$x",
          s"$VALUE_COLUMN_PREFIX$x"
        )
      })
    header.mkString(columnSeparator)
  }

  // read headers sequentially
  def readHeader(header: String): Seq[String] = {
    header.split(columnSeparator).map(x => x.trim).filter(x => x.nonEmpty).toSeq
  }

  def paramNameToValueColumnMapFromHeaders(headers: Seq[String]): Map[String, Int] = {
    // determine the parameters
    headers.indices.filter(x => {
      val value: String = headers(x)
      !value.startsWith(FAIL_REASONS_COLUMN_PREFIX) &&
        !value.startsWith(FAIL_COUNT_COLUMN_PREFIX) &&
        !value.startsWith(WEIGHTED_FAIL_COUNT_COLUMN_PREFIX) &&
        !value.startsWith(SUCCESS_COUNT_COLUMN_PREFIX) &&
        !value.startsWith(WEIGHTED_SUCCESS_COUNT_COLUMN_PREFIX) &&
        !value.startsWith(VALUE_COLUMN_PREFIX)
    }).map(validIndex => {
      (headers(validIndex), validIndex)
    }).toMap
  }

  def paramMapFromParamToColumnMap(paramNameToColumn: Map[String, Int], values: Seq[String]): Map[String, Seq[String]] = {
    paramNameToColumn.keys.map(paramName => {
      val paramIndex = paramNameToColumn(paramName)
      val paramValues: Seq[String] = values(paramIndex).split(MULTI_VALUE_SEPARATOR).toSeq
      (paramName, paramValues)
    }).toMap
  }

  def metricNameToColumnMapForCategoryFromHeaders(categoryPrefix: String, headers: Seq[String]): Map[String, Int] = {
    headers.indices
      .filter(index => headers(index).startsWith(categoryPrefix))
      .map(index => (headers(index).stripPrefix(s"$categoryPrefix"), index))
      .toMap
  }

  def metricRowFromHeadersAndColumns(headers: Seq[String], paramsMap: Map[String, Seq[String]], columns: Seq[String]): MetricRow = {
    // map holding the match of metric name to the indices of the column
    val successCountColumnIndexForMetricMap: Map[String, Int] = metricNameToColumnMapForCategoryFromHeaders(SUCCESS_COUNT_COLUMN_PREFIX, headers)
    val weightedSuccessCountColumnIndexForMetricMap: Map[String, Int] = metricNameToColumnMapForCategoryFromHeaders(WEIGHTED_SUCCESS_COUNT_COLUMN_PREFIX, headers)
    val failCountColumnIndexForMetricMap: Map[String, Int] = metricNameToColumnMapForCategoryFromHeaders(FAIL_COUNT_COLUMN_PREFIX, headers)
    val weightedFailCountColumnIndexForMetricMap: Map[String, Int] = metricNameToColumnMapForCategoryFromHeaders(WEIGHTED_FAIL_COUNT_COLUMN_PREFIX, headers)
    val failReasonsCountColumnIndexForMetricMap: Map[String, Int] = metricNameToColumnMapForCategoryFromHeaders(FAIL_REASONS_COLUMN_PREFIX, headers)
    val metricNameToColumnIndex: Map[String, Int] = metricNameToColumnMapForCategoryFromHeaders(VALUE_COLUMN_PREFIX, headers)

    // now fill the metric row, initially set the param mapping, then add the single values
    var metricRow: MetricRow = MetricRow.emptyForParams(paramsMap)
    metricNameToColumnIndex.keys.foreach(metricName => {
      // need to add MetricValue per metricName
      // first determine the indices
      val metricIndex: Int = metricNameToColumnIndex(metricName)
      val failCountIndex: Int = failCountColumnIndexForMetricMap(metricName)
      val weightedFailCountIndex: Int = weightedFailCountColumnIndexForMetricMap(metricName)
      val failReasonsIndex: Int = failReasonsCountColumnIndexForMetricMap(metricName)
      val successCountIndex: Int = successCountColumnIndexForMetricMap(metricName)
      val weightedSuccessCountIndex: Int = weightedSuccessCountColumnIndexForMetricMap(metricName)
      // then determine the values
      val metricValue: Double = columns(metricIndex).toDouble
      val failCount: Int = columns(failCountIndex).toInt
      val weightedFailCount: Double = columns(weightedFailCountIndex).toDouble
      val failReasonsCountMap: Map[ComputeFailReason, Int] = columns(failReasonsIndex)
        .split(",")
        .map(x => x.strip())
        .filter(x => x.nonEmpty)
        .map(reasonAndCount => {
          val reason: ComputeFailReason = ComputeFailReason(reasonAndCount.split(":")(0))
          val count = reasonAndCount.split(":")(1).toInt
          // now need to match the ComputeFailReason string values to the actual values
          (reason, count)
        }).toMap
      val successCount: Int = columns(successCountIndex).toInt
      val weightedSuccessCount: Double = columns(weightedSuccessCountIndex).toDouble
      // Map[ComputeFailReason, Int] creason to count map needed beside value, also success and fail counts
      val metricValueObj = MetricValue.createEmptyAveragingMetricValue(metricName)
      val newRunningValue: BiRunningValue[Map[ComputeFailReason, Int], Double] = metricValueObj
        .biValue
        .addFirst(RunningValue(weightedFailCount, failCount, failReasonsCountMap, failMapWeightFon,  errorMapAdd.addFunc))
        .addSecond(RunningValue(weightedSuccessCount, successCount, metricValue, doubleAvgWeightFunction, doubleAvgAdd.addFunc))
      metricRow = metricRow.addMetric(metricValueObj.copy(biValue = newRunningValue))
    })
    metricRow
  }

  def getColumnsFromLine(headerLine: String): Seq[String] = {
    headerLine.split(columnSeparator).map(x => x.trim).toSeq
  }

  def readRow(headers: Seq[String], paramNamesToColumnIndexMap: Map[String, Int], row: String): MetricRow = {
    val colStrValues: Seq[String] = getColumnsFromLine(row)
    assert(headers.size == colStrValues.size, s"header key size '${headers.size}' does not match size of column values '${colStrValues.size}'")
    val paramsMap: Map[String, Seq[String]] = paramMapFromParamToColumnMap(paramNamesToColumnIndexMap, colStrValues)
    // calculate the values and put in MetricRow
    metricRowFromHeadersAndColumns(headers, paramsMap, colStrValues)
  }

  def readRow(headers: Seq[String], row: String): MetricRow = {
    // map holding the parameter values
    val paramNamesToColumnIndexMap: Map[String, Int] = paramNameToValueColumnMapFromHeaders(headers)
    readRow(headers, paramNamesToColumnIndexMap, row)
  }

  def readDocument[T <: AnyRef](headers: Seq[String], rows: Iterable[String], tag: T): MetricDocument[T] = {
    val document = MetricDocument.empty(tag)
    rows.foreach(row => {
      val metricRow: MetricRow = readRow(headers, row)
      document.add(metricRow)
    })
    document
  }

  def paramsToValueString(values: Map[String, Seq[String]], paramNames: Seq[String]): String = {
    paramNames.map(x => values.getOrElse(x, Seq.empty).mkString(MULTI_VALUE_SEPARATOR)).mkString(columnSeparator)
  }

  def formatRow(row: MetricRow, paramNames: Seq[String], metricNames: Seq[String]): String = {
    var data: Seq[String] = Seq.empty[String]
    val paramValues: String = paramsToValueString(row.params, paramNames)
    if (paramValues.nonEmpty) {
      data = data :+ paramValues
    }
    metricNames.foreach(x => {
      val metricValue: MetricValue[Double] = row.metrics.getOrElse(x, MetricValue.createEmptyAveragingMetricValue(x))
      val failMapValue: Map[ComputeFailReason, Int] = metricValue.biValue.value1.value
      val totalErrors = metricValue.biValue.value1.numSamples
      val weightedTotalErrors = metricValue.biValue.value1.weight
      val totalSuccess = metricValue.biValue.value2.numSamples
      val weightedTotalSuccess = metricValue.biValue.value2.weight
      val errorString = failMapValue.keys.toSeq
        .sorted[ComputeFailReason]((x, y) => x.description compare y.description)
        .map(failReason => s"${failReason.description}:${failMapValue(failReason)}")
        .mkString(",")
      data = data ++ Seq(
        s"$totalErrors",
        s"$weightedTotalErrors",
        s"$errorString",
        s"$totalSuccess",
        s"$weightedTotalSuccess",
        s"${String.format("%.4f", metricValue.biValue.value2.value)}"
      )
    })
    data.mkString(columnSeparator)
  }
}
