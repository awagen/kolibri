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

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.values.MetricValue


case class CSVParameterBasedMetricDocumentFormat(columnSeparator: String) extends MetricDocumentFormat {

  val FAIL_COUNT_COLUMN_PREFIX = "fail-count-"
  val FAIL_REASONS_COLUMN_PREFIX = "failReasons-"
  val SUCCESS_COUNT_COLUMN_PREFIX = "success-count-"
  val VALUE_COLUMN_PREFIX = "value-"
  val INVALID_ID = "invalidID"
  val MULTI_VALUE_SEPARATOR = "//"


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
          s"$FAIL_REASONS_COLUMN_PREFIX$x",
          s"$SUCCESS_COUNT_COLUMN_PREFIX$x",
          s"$VALUE_COLUMN_PREFIX$x"
        )
      })
    header.mkString(columnSeparator)
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
      val value: Map[ComputeFailReason, Int] = metricValue.biValue.value1.value
      val totalErrors = metricValue.biValue.value1.count
      val totalSuccess = metricValue.biValue.value2.count
      val errorString = value.keys.toSeq
        .sorted[ComputeFailReason]((x, y) => x.description compare y.description)
        .map(failReason => s"${failReason.description}:${value(failReason)}")
        .mkString(",")
      data = data ++ Seq(
        s"$totalErrors",
        s"$errorString",
        s"$totalSuccess",
        s"${String.format("%.4f", metricValue.biValue.value2.value)}"
      )
    })
    data.mkString(columnSeparator)
  }
}
