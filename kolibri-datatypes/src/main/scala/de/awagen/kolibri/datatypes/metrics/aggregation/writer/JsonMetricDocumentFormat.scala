/**
 * Copyright 2022 Andreas Wagenmann
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
import de.awagen.kolibri.datatypes.io.json.ComputeFailReasonJsonProtocol.ComputeFailReasonFormat
import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.aggregateTypeFormat
import de.awagen.kolibri.datatypes.io.json.TimeStampJsonProtocol.TimeStampFormat
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.JsonMetricDocumentFormat.{DataSet, DataSetBuilder, Document, DocumentBuilder, EntriesBuilder, jsonDocumentFormat}
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricDocument.ParamMap
import de.awagen.kolibri.datatypes.stores.MetricRow.ResultCountStore
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.RunningValue
import de.awagen.kolibri.datatypes.values.aggregation.AggregateValue
import de.awagen.kolibri.datatypes.values.{MetricValue, RunningValues}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol.{DoubleJsonFormat, IntJsonFormat, StringJsonFormat, immSeqFormat, jsonFormat3, jsonFormat5, jsonFormat7, mapFormat}
import spray.json._

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.{Date, Objects}

object JsonMetricDocumentFormat {

  val DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  /**
   * DataSet represents collection of values for the same metrics.
   * It also contains complementary information such as number of samples
   * aggregated in the data (successful vs failed, weighted success and fail samples
   * and in case of fails, the mapping of ComputeFailReasons to the counts of occurrence
   *
   * @param name                   - name of the metric
   * @param data                   - the actual data
   * @param successSamples         - number of successfully computed results that are aggregated per data point
   * @param weightedSuccessSamples - as success_samples, but in case the aggregated samples carry weights != 1.0, will deviate from it
   *                               since on adding each sample count is multiplied by its weight
   * @param failSamples            - number of failed compute results aggregated in the sample
   * @param weightedFailSamples    - same as case above for the success samples, but for the failed samples
   * @param failReasons            - per data point a mapping of specific ComputeFailReasons to the count of occurrence (e.g how many samples are yielding the fail reason)
   */
  case class DataSet(name: String,
                     data: Seq[Any],
                     successSamples: Seq[Int],
                     weightedSuccessSamples: Seq[Double],
                     failSamples: Seq[Int],
                     weightedFailSamples: Seq[Double],
                     failReasons: Seq[Map[ComputeFailReason, Int]])

  implicit val jsonDocDatasetFormat: RootJsonFormat[DataSet] = jsonFormat7(DataSet)

  case class DataSetBuilder(name: String) {

    private[this] var dataSeq: Seq[Any] = Seq.empty
    private[this] var successSamplesSeq: Seq[Int] = Seq.empty
    private[this] var weightedSuccessSamplesSeq: Seq[Double] = Seq.empty
    private[this] var failSamplesSeq: Seq[Int] = Seq.empty
    private[this] var weightedFailSamplesSeq: Seq[Double] = Seq.empty
    private[this] var failReasonsSeq: Seq[Map[ComputeFailReason, Int]] = Seq.empty


    def addSample(dataSample: Any,
                  successSamples: Int,
                  weightedSuccessSamples: Double,
                  failSamples: Int,
                  weightedFailSamples: Double,
                  failReasons: Map[ComputeFailReason, Int]): Unit = {
      dataSeq = dataSeq :+ dataSample
      successSamplesSeq = successSamplesSeq :+ successSamples
      weightedSuccessSamplesSeq = weightedSuccessSamplesSeq :+ weightedSuccessSamples
      failSamplesSeq = failSamplesSeq :+ failSamples
      weightedFailSamplesSeq = weightedFailSamplesSeq :+ weightedFailSamples
      failReasonsSeq = failReasonsSeq :+ failReasons
    }

    def build: DataSet = DataSet(
      name = name,
      data = dataSeq,
      successSamples = successSamplesSeq,
      weightedSuccessSamples = weightedSuccessSamplesSeq,
      failSamples = failSamplesSeq,
      weightedFailSamples = weightedFailSamplesSeq,
      failReasons = failReasonsSeq
    )

  }

  /**
   * Collecting data belonging to the same AggregationType and labels.
   * Each measure is provided in a separate DataSet
   *
   * @param entryType - the type of entries specifying the data, e.g sequence vs histogram,...
   * @param labels    - labels for each data point. Note that the data seq of the DataSets
   *                  needs to have same length as labels, otherwise its unclear where
   *                  something might be missing and thus the data can not properly be represented.
   *                  Note that these labels determine which group the datasets belong to, which
   *                  in the experiments will usually mean parameter sets of type Map[String, Seq[String]].
   *                  The values of each data point might come with additional groupings,
   *                  such as in the case of histograms (e.g numerical label (e.g position) + count)
   * @param datasets  - one dataset per metric
   */
  case class Entries(entryType: AggregationType,
                     labels: Seq[Any],
                     datasets: Seq[DataSet],
                     successCount: Int,
                     failCount: Int)

  implicit val jsonDocEntriesFormat: RootJsonFormat[Entries] = jsonFormat5(Entries)

  case class EntriesBuilder(entryType: AggregationType) {
    private[this] var labels: Seq[Any] = Seq.empty
    private[this] var datasets: Seq[DataSet] = Seq.empty
    private[this] var successCount: Int = 0
    private[this] var failCount: Int = 0

    def addLabel(label: Any): Unit = {
      labels = labels :+ label
    }

    def addDataset(dataset: DataSet): Unit = {
      datasets = datasets :+ dataset
    }

    def setSuccessCount(count: Int): Unit = {
      successCount = count
    }

    def setFailCount(count: Int): Unit = {
      failCount = count
    }

    def build: Entries = Entries(entryType, labels, datasets, successCount, failCount)
  }

  /**
   * Representation of a full json document, representing one result (aggregated or single).
   * Further information such as payloads used for the jobs that generated the data
   * are stored separately from this.
   *
   * @param name      - name specifying the document. Usually some experiment name
   * @param timestamp - timestamp of the time the results were retrieved.
   * @param data      - the actual data
   */
  case class Document(name: String,
                      timestamp: Timestamp,
                      data: Seq[Entries])

  val jsonDocumentFormat: RootJsonFormat[Document] = jsonFormat3(Document)

  case class DocumentBuilder(name: String) {

    private[this] var timestamp: Timestamp = _
    private[this] var data: Seq[Entries] = Seq.empty

    def withTimeStamp(timestamp: Timestamp): Unit = {
      this.timestamp = timestamp
    }

    def addEntry(entries: Entries): Unit = {
      data = data :+ entries
    }

    def build: Document = Document(
      name,
      timestamp,
      data
    )

  }


}


class JsonMetricDocumentFormat() extends MetricDocumentFormat {

  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * We first fill the MetricDocument into the above defined Document and then
   * just use spray json write method to dump it into a file.
   * For converting a file back to json, we can just use spray json read.
   *
   * @param ma
   * @return
   */
  override def metricDocumentToString(ma: MetricDocument[_]): String = {
    val metricNameToTypeMapping = MetricDocumentFormatHelper.getMetricNameToAggregationTypeMap(ma)
    val metricNameToDefaultMetricValueMap: Map[String, MetricValue[Any]] = ma.getMetricNames.map(metricName => {
      ma.rows.values.find(x => x.metrics.keySet.contains(metricName))
        .map(x => (metricName, x.metrics(metricName).emptyCopy()))
    }).filter(x => x.nonEmpty).map(x => x.get).toMap

    val sortedMetricNames = metricNameToDefaultMetricValueMap.keySet.toSeq.sorted

    // for each metricName, create a DataSet. Then we iteratively extend Entries with the new label
    // corresponding to a new set of parameters and then fill in new data points for every single metric
    val metricNameToDataSetMap: Map[String, DataSetBuilder] = sortedMetricNames.map(x => (x, DataSetBuilder(x))).toMap

    val aggregationTypeToEntriesBuilderMap: Map[String, EntriesBuilder] = metricNameToTypeMapping.map(x => (x._2.toString(), EntriesBuilder(x._2)))

    // iterating over all parameter settings
    ma.rows.keys.foreach(keyMap => {
      val metricsForParameters: MetricRow = ma.rows(keyMap)
      // adding the label to each entries builder
      aggregationTypeToEntriesBuilderMap.values.foreach(entriesBuilder => {
        entriesBuilder.addLabel(keyMap)
        // NOTE: those might be incorrect in case rows do not contain the same metrics,
        // since they do not distinguish by metric type,
        // yet usually we should have all metrics in all rows
        entriesBuilder.setSuccessCount(metricsForParameters.countStore.successCount)
        entriesBuilder.setFailCount(metricsForParameters.countStore.failCount)
      })

      // for each metric extracting data for the current setting of parameters
      sortedMetricNames.foreach(metricName => {
        val metricValueContainer: MetricValue[Any] = metricsForParameters.metrics.getOrElse(metricName, metricNameToDefaultMetricValueMap(metricName))
        val metricValue = metricValueContainer.biValue.value2
        val failRecordValue: AggregateValue[Map[ComputeFailReason, Int]] = metricValueContainer.biValue.value1

        // adding a datapoint corresponding to the current label
        // given by the parameters
        val datasetBuilder: DataSetBuilder = metricNameToDataSetMap(metricName)
        datasetBuilder.addSample(
          dataSample = metricValue.value,
          successSamples = metricValue.numSamples,
          weightedSuccessSamples = metricValue.weight,
          failSamples = failRecordValue.numSamples,
          weightedFailSamples = failRecordValue.weight,
          failReasons = failRecordValue.value
        )
      })
      // now we have filled in all datapoints for every metric for the current parameter settings
    })

    val metricNameToDataSet: Map[String, DataSet] = sortedMetricNames.map(name => {
      (name, metricNameToDataSetMap(name).build)
    }).toMap

    // now go by metric and add to the right entriesBuilder
    sortedMetricNames.foreach(metricName => {
      val aggregationType: AggregationType = metricNameToTypeMapping.getOrElse(
        metricName, AggregationType.DOUBLE_AVG)
      val entryBuilder: EntriesBuilder = aggregationTypeToEntriesBuilderMap(aggregationType.toString())
      entryBuilder.addDataset(metricNameToDataSet(metricName))
    })

    // now build all entries and add to the documentBuilder
    val documentBuilder = DocumentBuilder(ma.id.toString)
    aggregationTypeToEntriesBuilderMap.values.foreach(entriesBuilder => {
      documentBuilder.addEntry(entriesBuilder.build)
    })
    documentBuilder.withTimeStamp(new Timestamp(new Date().getTime))
    val document = documentBuilder.build
    implicit val dw: JsonFormat[Document] = jsonDocumentFormat
    document.toJson.toString()
  }

  /**
   * Extract mapping of metric names to aggregation type from document
   */
  def documentToMetricTypeMapping(doc: Document): Map[String, AggregationType] = {
    doc.data.flatMap(entries => {
      entries.datasets.map(dataset => (dataset.name, entries.entryType))
    }).toMap
  }

  /**
   * Taking in a string containing a json in the format needed by the above-defined
   * json document, translate to the common data structure used to group and merge results
   * @param content - full json string (e.g from a result file)
   * @return - MetricDocument[_]
   */
  def contentToMetricDocumentAndMetricTypeMapping(content: String): MetricDocument[Tag] = {
    implicit val dw: JsonFormat[Document] = jsonDocumentFormat
    val document = content.parseJson.convertTo[Document]
    jsonDocumentToMetricDocument(document)
  }

  def metricValueForAggregationType(aggregationType: AggregationType,
                                    weightedSuccessCount: Double,
                                    successCount: Int,
                                    value: Any): RunningValue[_] = {
    aggregationType match {
      case AggregationType.DOUBLE_AVG =>
        RunningValues.doubleAvgRunningValue(weightedSuccessCount, successCount, value.asInstanceOf[Double])
      case AggregationType.MAP_WEIGHTED_SUM_VALUE =>
        RunningValues.mapValueWeightedSumRunningValue(weightedSuccessCount, successCount, value.asInstanceOf[Map[String, Double]])
      case AggregationType.MAP_UNWEIGHTED_SUM_VALUE =>
        RunningValues.mapValueUnweightedSumRunningValue(weightedSuccessCount, successCount, value.asInstanceOf[Map[String, Double]])
      case AggregationType.NESTED_MAP_WEIGHTED_SUM_VALUE =>
        RunningValues.nestedMapValueWeightedSumUpRunningValue(weightedSuccessCount, successCount, value.asInstanceOf[Map[String, Map[String, Double]]])
      case AggregationType.NESTED_MAP_UNWEIGHTED_SUM_VALUE =>
        RunningValues.nestedMapValueUnweightedSumUpRunningValue(weightedSuccessCount, successCount, value.asInstanceOf[Map[String, Map[String, Double]]])
    }
  }

  /**
   * From existing json format Document, create MetricDocument.
   * Used to have a common aggregation format from which different outputs can be set (e.g csv, json,...)
   *
   * @param doc
   * @return
   */
  def jsonDocumentToMetricDocument(doc: Document): MetricDocument[Tag] = {
    val tag = StringTag(doc.name)
    val metricDocument = MetricDocument.empty(tag)

    // collect distinct params for which to compose MetricRows
    val distinctParams: Seq[ParamMap] = doc.data.foldLeft[Set[ParamMap]](Set.empty)((soFar, next) => {
      soFar ++ next.labels.map(x => x.asInstanceOf[ParamMap]).toSet
    }).toSeq

    // per parameter setting create one metric row, fill it with all the data and add it to the
    // metric document
    distinctParams.foreach(params => {
      var metricRow: MetricRow = null
      // check correct indices in all entries
      doc.data.indices.foreach(index => {
        val entries = doc.data(index)
        val entryType: AggregationType = entries.entryType
        if (Objects.isNull(metricRow)) {
          val resultCountStore = new ResultCountStore(entries.successCount, entries.failCount)
          metricRow = MetricRow(resultCountStore, params, Map.empty)
        }
        val valueIndex = entries.labels.indexOf(params)
        if (valueIndex >= 0) {
          entries.datasets.map(dataset => {
            val name: String = dataset.name
            val value: Any = dataset.data(valueIndex)
            val successCount: Int = dataset.successSamples(valueIndex)
            val failCount: Int = dataset.failSamples(valueIndex)
            val weightedSuccessSamples: Double = dataset.weightedSuccessSamples(valueIndex)
            // NOTE: right now we assume fail counts to be pure counts without any weight, thus weightedFailSamples not utilized here
            // val weightedFailSamples: Double = dataset.weightedFailSamples(valueIndex)
            val failReasons: Map[ComputeFailReason, Int] = dataset.failReasons(valueIndex)

            // now compose the MetricValues to add to the metricRow
            val runningValue = metricValueForAggregationType(
              entryType,
              weightedSuccessSamples,
              successCount,
              value)

            val metricValue: MetricValue[Any] = MetricValue.createMetricValue(
              metricName = name,
              failCount = failCount,
              failMap = failReasons,
              runningValue = runningValue
            )

            logger.info(s"Adding MetricValue to MetricRow: $metricValue")
            metricRow = metricRow.addMetricDontChangeCountStore(metricValue)

          })
        }
      })
      if (Objects.nonNull(metricRow)) {
        logger.info(s"Adding metric row to document: $metricRow")
        metricDocument.add(metricRow)
      }
      else {
        logger.warn(s"MetricRow is null for params '$params', skipping addition to document")
      }
    })

    metricDocument

  }

  /**
   * From file content, extract the metric name to aggregation type mappings
   */
  override def metricNameToTypeMappingFromContent(content: String): Map[String, AggregationType] = {
    implicit val dw: JsonFormat[Document] = jsonDocumentFormat
    val document = content.parseJson.convertTo[Document]
    documentToMetricTypeMapping(document)
  }
}
