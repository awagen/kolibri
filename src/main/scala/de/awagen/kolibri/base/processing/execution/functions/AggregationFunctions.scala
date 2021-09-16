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
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.matching.Regex

object AggregationFunctions {

  /**
    * Within the given directorySubDir (the reader within persistenceDIModule already refers to some folder/bucket),
    * filter files by regex and aggregate those partial csv results, store the result in file named by outputFilename,
    * in the directorySubDir
    *
    * @param persistenceDIModule - module holding the persistence objects
    * @param directorySubDir     - sub-directory where to filter the files and relative to which to store the outputFilename
    * @param filterRegex         - the regex to filter the files by
    * @param outputFilename      - the output filename (might contain additional path prefix relative to directorySubDir)
    */
  case class AggregateFromDirectoryByRegexWeighted(persistenceDIModule: PersistenceDIModule,
                                                   directorySubDir: String,
                                                   filterRegex: Regex,
                                                   sampleIdentifierToWeight: String => Double,
                                                   outputFilename: String) extends Execution[Unit] {

    val directoryReader: DirectoryReader = regexDirectoryReader(filterRegex)

    override def execute: Either[TaskFailType, Unit] = {
      val filteredFiles = directoryReader.listFiles(directorySubDir, _ => true)
      val aggregator = AggregateFilesWeighted(persistenceDIModule, directorySubDir, filteredFiles, sampleIdentifierToWeight, outputFilename)
      aggregator.execute
    }
  }

  /**
    * Similar to AggregateFromDirectoryByRegex above, but takes specific filenames (full paths) to aggregate instead of regex
    *
    * @param persistenceDIModule - module holding the persistence objects
    * @param directorySubDir     - sub-directory where to filter the files and relative to which to store the outputFilename
    * @param files               - the regex to filter the files by
    * @param outputFilename      - the output filename (might contain additional path prefix relative to directorySubDir)
    */
  case class AggregateFilesWeighted(persistenceDIModule: PersistenceDIModule,
                                    directorySubDir: String,
                                    files: Seq[String],
                                    sampleIdentifierToWeight: String => Double,
                                    outputFilename: String) extends Execution[Unit] {
    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    val csvFormat: CSVParameterBasedMetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")

    val aggregationIdentifier: Tag = StringTag("ALL1")

    val fileReader: FileReader = persistenceDIModule.fileReader
    val fileWriter: FileWriter[String, _] = persistenceDIModule.fileWriter

    override def execute: Either[TaskFailType, Unit] = {
      try {
        // find all relevant files, parse them into MetricDocuments on ALL-tag,
        // aggregate all
        logger.info(s"files to aggregate: $files")
        val overallDoc: MetricDocument[Tag] = MetricDocument.empty(aggregationIdentifier)
        files.foreach(file => {
          logger.info(s"adding file: $file")
          val weight: Double = sampleIdentifierToWeight.apply(file.split("/").last)
          val doc: MetricDocument[Tag] = FileUtils.fileToMetricDocument(file, fileReader, aggregationIdentifier).weighted(weight)
          overallDoc.add(doc, ignoreIdDiff = true)
          logger.info(s"done adding file: $file")
        })
        // now we have the overall document, now we need to write it to file
        val relativeWritePath = s"${directorySubDir.stripSuffix("/")}/$outputFilename"
        logger.info(s"writing aggregation to file: $relativeWritePath")
        fileWriter.write(csvFormat.metricDocumentToString(overallDoc), relativeWritePath)
        logger.info(s"done writing aggregation to file: $relativeWritePath")
        Right(())
      }
      catch {
        case e: Exception =>
          logger.error("failed aggregating all", e)
          Left(TaskFailType.FailedByException(e))
      }
    }
  }

  case class DoNothing() extends Execution[Unit] {
    override def execute: Either[TaskFailType, Unit] = Right(())
  }

}
