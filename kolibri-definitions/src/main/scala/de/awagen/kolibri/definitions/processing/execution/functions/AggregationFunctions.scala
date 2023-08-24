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

import de.awagen.kolibri.datatypes.metrics.MetricSummary
import de.awagen.kolibri.datatypes.metrics.MetricSummary.metricSummaryFormat
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.definitions.provider.WeightProviders.WeightProvider
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.{JsonMetricDocumentFormat, MetricDocumentFormat}
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{JsValueFormat, StringJsonFormat, mapFormat}
import spray.json.enrichAny

import scala.util.matching.Regex

object AggregationFunctions {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Within the given directorySubDir (the reader within persistenceDIModule already refers to some folder/bucket),
   * filter files by regex and aggregate those partial csv results, store the result in file named by outputFilename,
   * in the directorySubDir
   *
   * @param readSubDir     - sub-directory where to filter the files and relative to which to store the outputFilename
   * @param writeSubDir
   * @param filterRegex    - the regex to filter the files by
   * @param sampleIdentifierToWeight
   * @param outputFilename - the output filename (might contain additional path prefix relative to directorySubDir)
   */
  case class AggregateFromDirectoryByRegexWeighted(directoryReaderFunc: SerializableFunction1[Regex, DataOverviewReader],
                                                   fileReader: Reader[String, Seq[String]],
                                                   fileWriter: Writer[String, String, _],
                                                   formatIdToFormatMap: Map[String, MetricDocumentFormat],
                                                   readSubDir: String,
                                                   writeSubDir: String,
                                                   filterRegex: Regex,
                                                   sampleIdentifierToWeight: WeightProvider[String],
                                                   outputFilename: String) extends Execution[Unit] {

    override def execute: Either[TaskFailType, Unit] = {
      val filteredFiles: Seq[String] = directoryReaderFunc(filterRegex).listResources(readSubDir, _ => true)
      val aggregator = AggregateFilesWeighted(fileReader, fileWriter, formatIdToFormatMap, writeSubDir, filteredFiles,
        sampleIdentifierToWeight, outputFilename)
      aggregator.execute
    }
  }


  /**
   * Summarize results in job result folder and write summary to summary folder.
   * @param jobResultsFolder - folder where results for the job are located (relative to base folder)
   * @param overviewReader - reader of directory content
   * @param fileReader - file reader
   * @param writer - file writer
   * @param criterionMetricName - the metric name of the metric used to compare variants (parameter settings)
   * @param summarySubfolder - the folder where the summary is persisted.
   */
  case class SummarizeJob(jobResultsFolder: String,
                          overviewReader: DataOverviewReader,
                          fileReader: Reader[String, Seq[String]],
                          writer: Writer[String, String, _],
                          criterionMetricName: String,
                          summarySubfolder: String = "summary") extends Execution[Unit] {
    override def execute: Either[TaskFailType, Unit] = {
      logger.info(s"Looking for result files in folder: $jobResultsFolder")
      val resultFiles = overviewReader.listResources(jobResultsFolder, fileName => fileName.endsWith(".json"))
      logger.info(s"Found files: $resultFiles")
      var tagToResultMap: Seq[MetricDocument[Tag]] = Seq.empty
      resultFiles.foreach(file => {
        try {
          logger.info(s"Trying to add file for summary: $file")
          val doc: MetricDocument[Tag] = FileUtils.fileToMetricDocument(file, fileReader, new JsonMetricDocumentFormat())
          tagToResultMap = tagToResultMap :+ doc
        }
        catch {
          case e: Exception =>
            logger.warn(s"""Failed adding file to summary: $file\nmsg: ${e.getMessage}\ntrace: ${e.getStackTrace.mkString("\n")}""")
        }
      })
      val tagToMetricSummary: Map[Tag, MetricSummary] = MetricSummary.summarize(tagToResultMap.map(x => (x.id, x.rows.values.toSeq)), criterionMetricName)
      val metricSummaryJsonString = tagToMetricSummary.map(x => (x._1.toString, metricSummaryFormat.write(x._2))).toJson.toString()
      // write to file
      writer.write(metricSummaryJsonString, s"$jobResultsFolder/$summarySubfolder/summary_${criterionMetricName}.json")
      Right(())
    }
  }



  /**
   * Similar to AggregateFromDirectoryByRegex above, but takes specific filenames (full paths) to aggregate instead of regex
   *
   * @param writeSubDir    - sub-directory where to filter the files and relative to which to store the outputFilename
   * @param files          - the regex to filter the files by
   * @param sampleIdentifierToWeight
   * @param outputFilename - the output filename (might contain additional path prefix relative to directorySubDir)
   */
  case class AggregateFilesWeighted(fileReader: Reader[String, Seq[String]],
                                    fileWriter: Writer[String, String, _],
                                    formatIdToFormatMap: Map[String, MetricDocumentFormat],
                                    writeSubDir: String,
                                    files: Seq[String],
                                    sampleIdentifierToWeight: WeightProvider[String],
                                    outputFilename: String) extends Execution[Unit] {
    // dummy tag to aggregate under
    val aggregationIdentifier: Tag = StringTag("AGG")

    override def execute: Either[TaskFailType, Unit] = {
      try {
        // find all relevant files, parse them into MetricDocuments on ALL-tag,
        // aggregate all
        logger.info(s"files to aggregate: $files")
        //        val formatIdToFormatMap: Map[String, MetricDocumentFormat] = AppProperties.config.metricDocumentFormats
        //          .map(x => (x.identifier.toLowerCase, x)).toMap
        var docPerFormatIdMap: Map[String, MetricDocument[Tag]] = Map.empty
        files.foreach(file => {
          try {
            logger.info(s"adding file: $file")
            val weight: Double = sampleIdentifierToWeight.apply(file.split("/").last.split("-").head)
            val formatId = file.split("\\.").last.toLowerCase
            if (!docPerFormatIdMap.keySet.contains(formatId)) {
              docPerFormatIdMap = docPerFormatIdMap + (formatId -> MetricDocument.empty(aggregationIdentifier))
            }
            formatIdToFormatMap.get(formatId).foreach(format => {
              val doc: MetricDocument[Tag] = FileUtils.fileToMetricDocument(file, fileReader, format).weighted(weight)
              docPerFormatIdMap(formatId).add(doc, ignoreIdDiff = true)
              logger.info(s"done adding file for format '$formatId': $file")
            })
          }
          catch {
            case e: Exception =>
              logger.warn(s"Exception on aggregating file '$file'", e)
          }
        })
        val result: Seq[Either[TaskFailType, Unit]] = docPerFormatIdMap.map(formatIdDocumentPair => {
          // now we have the overall document, now we need to write it to file
          val normedOutputFileName = if (outputFilename.endsWith(formatIdDocumentPair._1)) outputFilename else s"$outputFilename.${formatIdDocumentPair._1}"
          val relativeWritePath = s"${writeSubDir.stripSuffix("/")}/$normedOutputFileName"
          logger.info(s"writing aggregation to file: $relativeWritePath")
          formatIdToFormatMap.get(formatIdDocumentPair._1).map(format => {
            fileWriter.write(format.metricDocumentToString(formatIdDocumentPair._2), relativeWritePath)
            logger.info(s"done writing aggregation for formatId '${formatIdDocumentPair._1}' to file: $relativeWritePath")
            Right(())
          }).getOrElse(Left(TaskFailType.FailedWrite))
        }).toSeq
        result.find(x => x.isLeft).getOrElse(Right(()))
      }
      catch {
        case e: Exception =>
          logger.error("failed aggregating all", e)
          Left(TaskFailType.FailedByException(e))
      }
    }
  }

  case class MultiExecution(executions: Seq[Execution[Any]]) extends Execution[Any] {
    override def execute: Either[TaskFailType, Any] = {
      executions.foreach(x => x.execute)
      Right(())
    }
  }

  case class DoNothing() extends Execution[Unit] {
    override def execute: Either[TaskFailType, Unit] = Right(())
  }

}
