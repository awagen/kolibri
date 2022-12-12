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

import de.awagen.kolibri.datatypes.io.json.AnyJsonProtocol.AnyJsonFormat
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat._
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.values.Calculations.ResultRecord
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.RunningValue
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{DoubleJsonFormat, StringJsonFormat, mapFormat}
import spray.json._

import java.util.Objects


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

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def metricDocumentToString(ma: MetricDocument[_]): String = {
    val metricNameToTypeMapping = MetricDocumentFormatHelper.getMetricNameToAggregationTypeMap(ma)

    val metricNameToTypeMappingCommentLines: Seq[String] = metricNameToTypeMapping.map(mapping => {
      s"# K_METRIC_AGGREGATOR_MAPPING ${mapping._1} ${mapping._2.toString()}"
    }).toSeq
    val paramNames = ma.getParamNames.toSeq.sorted
    val metricNames = ma.getMetricNames.toSeq.sorted
    val header = formatHeader(paramNames, metricNames)
    val rowSeq = ma.rows.keys.map(x => Seq(formatRow(ma.rows(x), paramNames, metricNames))
      .mkString(columnSeparator)).toSeq
    (metricNameToTypeMappingCommentLines ++ Seq(header) ++ rowSeq).mkString("\n")
  }

  private[writer] def formatHeader(paramNames: Seq[String], metricNames: Seq[String]): String = {
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

  private[writer] def paramNameToValueColumnMapFromHeaders(headers: Seq[String]): Map[String, Int] = {
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

  private[writer] def paramMapFromParamToColumnMap(paramNameToColumn: Map[String, Int], values: Seq[String]): Map[String, Seq[String]] = {
    paramNameToColumn.keys.map(paramName => {
      val paramIndex = paramNameToColumn(paramName)
      val paramValues: Seq[String] = values(paramIndex).split(MULTI_VALUE_SEPARATOR).toSeq
      (paramName, paramValues)
    }).toMap
  }

  private[writer] def metricNameToColumnMapForCategoryFromHeaders(categoryPrefix: String, headers: Seq[String]): Map[String, Int] = {
    headers.indices
      .filter(index => headers(index).startsWith(categoryPrefix))
      .map(index => (headers(index).stripPrefix(s"$categoryPrefix"), index))
      .toMap
  }

  private[writer] def convertValue(value: String, aggregationType: AggregationType): Any = {
    aggregationType match {
      case AggregationType.DOUBLE_AVG =>
        value.toDouble
      case AggregationType.MAP_UNWEIGHTED_SUM_VALUE =>
        value.stripMargin.parseJson.convertTo[Map[String, Double]]
      case AggregationType.MAP_WEIGHTED_SUM_VALUE =>
        value.stripMargin.parseJson.convertTo[Map[String, Double]]
      case AggregationType.NESTED_MAP_UNWEIGHTED_SUM_VALUE =>
        value.stripMargin.parseJson.convertTo[Map[String, Map[String, Double]]]
      case AggregationType.NESTED_MAP_WEIGHTED_SUM_VALUE =>
        value.stripMargin.parseJson.convertTo[Map[String, Map[String, Double]]]
    }
  }

  private[writer] def metricRowFromHeadersAndColumns(headers: Seq[String], paramsMap: Map[String, Seq[String]], columns: Seq[String], metricNameAggregationTypeMap: Map[String, AggregationType]): MetricRow = {
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
      val metricAggregationType = metricNameAggregationTypeMap.getOrElse(metricName, AggregationType.DOUBLE_AVG)
      val metricValue: Any = convertValue(columns(metricIndex), metricAggregationType)
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

      val valueRunningValue: RunningValue[_] = metricAggregationType.singleSampleToRunningValue(ResultRecord[Any](metricName, Right(metricValue)))
        .copy(
          weight = weightedSuccessCount,
          numSamples = successCount,
          metricValue
        )
      val errorRunningValue: RunningValue[Map[ComputeFailReason, Int]] = AggregationType.ERROR_MAP_VALUE.emptyRunningValueSupplier.apply()
        .copy(
          weight = weightedFailCount,
          numSamples = failCount,
          value = failReasonsCountMap
        )
      val newValue = MetricValue(name = metricName, BiRunningValue(errorRunningValue, valueRunningValue))

      metricRow = metricRow.addMetricDontChangeCountStore(newValue)
    })
    metricRow
  }

  private[writer] def getColumnsFromLine(headerLine: String): Seq[String] = {
    headerLine.split(columnSeparator).map(x => x.trim).toSeq
  }

  private[writer] def readRow(headers: Seq[String], paramNamesToColumnIndexMap: Map[String, Int], row: String, metricNameAggregationTypeMap: Map[String, AggregationType]): MetricRow = {
    val colStrValues: Seq[String] = getColumnsFromLine(row)
    assert(headers.size == colStrValues.size, s"header key size '${headers.size}' does not match size of column values '${colStrValues.size}'")
    val paramsMap: Map[String, Seq[String]] = paramMapFromParamToColumnMap(paramNamesToColumnIndexMap, colStrValues)
    // calculate the values and put in MetricRow
    metricRowFromHeadersAndColumns(headers, paramsMap, colStrValues, metricNameAggregationTypeMap)
  }

  private[writer] def readRow(headers: Seq[String], row: String, metricNameAggregationTypeMap: Map[String, AggregationType]): MetricRow = {
    // map holding the parameter values
    val paramNamesToColumnIndexMap: Map[String, Int] = paramNameToValueColumnMapFromHeaders(headers)
    readRow(headers, paramNamesToColumnIndexMap, row, metricNameAggregationTypeMap)
  }

  def readDocument[T <: AnyRef](headers: Seq[String], rows: Iterable[String], tag: T, metricNameAggregationTypeMap: Map[String, AggregationType]): MetricDocument[T] = {
    val document = MetricDocument.empty(tag)
    rows.foreach(row => {
      val metricRow: MetricRow = readRow(headers, row, metricNameAggregationTypeMap)
      document.add(metricRow)
    })
    document
  }

  private[writer] def paramsToValueString(values: Map[String, Seq[String]], paramNames: Seq[String]): String = {
    paramNames.map(x => values.getOrElse(x, Seq.empty).mkString(MULTI_VALUE_SEPARATOR)).mkString(columnSeparator)
  }

  private[writer] def formatRow(row: MetricRow, paramNames: Seq[String], metricNames: Seq[String]): String = {
    var data: Seq[String] = Seq.empty[String]
    val paramValues: String = paramsToValueString(row.params, paramNames)
    if (paramValues.nonEmpty) {
      data = data :+ paramValues
    }
    metricNames.foreach(x => {
      val metricValue: MetricValue[Any] = row.metrics.getOrElse(x, MetricValue.createDoubleEmptyAveragingMetricValue(x))
      val failMapValue: Map[ComputeFailReason, Int] = metricValue.biValue.value1.value
      val totalErrors = metricValue.biValue.value1.numSamples
      val weightedTotalErrors = metricValue.biValue.value1.weight
      val totalSuccess = metricValue.biValue.value2.numSamples
      val weightedTotalSuccess = metricValue.biValue.value2.weight
      val errorString = failMapValue.keys.toSeq
        .sorted[ComputeFailReason]((x, y) => x.description compare y.description)
        .map(failReason => s"${failReason.description}:${failMapValue(failReason)}")
        .mkString(",")
      val value = metricValue.biValue.value2.value
      val valueStringFormat: String = value match {
        case _: Double => s"${String.format("%.4f", value.asInstanceOf[Double])}"
        case _: Float => s"${String.format("%.4f", value.asInstanceOf[Float])}"
        case s: String => s
        case i: Int => i.toString
        case b: Boolean => b.toString
        // NOTE: mapping any via json string write requires that the object can be mapped
        // e.g for Maps only use String keys (otherwise it will try to map it to string and fail)
        case e: Any => e.toJson.toString()
      }
      data = data ++ Seq(
        s"$totalErrors",
        s"${String.format("%.4f", weightedTotalErrors)}",
        s"$errorString",
        s"$totalSuccess",
        s"${String.format("%.4f", weightedTotalSuccess)}",
        valueStringFormat
      )
    })
    data.mkString(columnSeparator)
  }

  /**
   * Read metric name to aggregation type mappings from comment lines in csv
   *
   * @param lines
   * @return
   */
  private[writer] def readValueAggregatorMappingFromLines(lines: Seq[String]): Map[String, AggregationType] = {
    lines.filter(s => s.startsWith("K_METRIC_AGGREGATOR_MAPPING"))
      .map(s => {
        try {
          val splitted: Seq[String] = s.split("\\s+").toSeq
          (splitted(1), AggregationType.byName(splitted(2)))
        }
        catch {
          case _: Exception =>
            logger.warn(s"Could not create name to AggregationType mapping from line: $s")
            null
        }
      })
      .filter(x => Objects.nonNull(x))
      .toMap
  }

  override def contentToMetricDocumentAndMetricTypeMapping(content: String): MetricDocument[Tag] = {
    val contentLines: Seq[String] = content.split("\n").toSeq
    val comments: Seq[String] = contentLines.filter(line => line.startsWith("#"))
      .map(x => x.stripPrefix("#").trim)
    val metricNameTypeMapping = readValueAggregatorMappingFromLines(comments)
    val lines: Seq[String] = contentLines
      .filter(line => !line.startsWith("#") && line.trim.nonEmpty)
    val headerColumns: Seq[String] = readHeader(lines.head)
    val rows: Seq[String] = lines.slice(1, lines.length)
    readDocument[Tag](headerColumns, rows, StringTag(""), metricNameTypeMapping)
  }

  override def metricNameToTypeMappingFromContent(content: String): Map[String, AggregationType] = {
    val contentLines: Seq[String] = content.split("\n").toSeq
    val comments: Seq[String] = contentLines.filter(line => line.startsWith("#"))
      .map(x => x.stripPrefix("#").trim)
    readValueAggregatorMappingFromLines(comments)
  }
}
