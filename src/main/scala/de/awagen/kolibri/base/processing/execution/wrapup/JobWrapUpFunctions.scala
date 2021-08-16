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


package de.awagen.kolibri.base.processing.execution.wrapup

import de.awagen.kolibri.base.config.di.modules.Modules.PersistenceDIModule
import de.awagen.kolibri.base.io.reader.{DirectoryReader, FileReader}
import de.awagen.kolibri.base.io.writer.Writers.FileWriter
import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

import scala.util.matching.Regex

object JobWrapUpFunctions {

  trait JobWrapUpFunction[+T] extends KolibriSerializable {

    def execute: Either[TaskFailType, T]

  }

  case class AggregateAllFromDirectory(persistenceDIModule: PersistenceDIModule,
                                       directorySubDir: String,
                                       filterRegex: Regex,
                                       outputFilename: String) extends JobWrapUpFunction[Unit] {
    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    val csvFormat: CSVParameterBasedMetricDocumentFormat = CSVParameterBasedMetricDocumentFormat("\t")

    val aggregationIdentifier: Tag = StringTag("ALL1")

    val directoryReader: DirectoryReader = persistenceDIModule.directoryReader(
      new SerializableFunction1[String, Boolean] {
        override def apply(v1: String): Boolean = filterRegex.matches(v1)
      }
    )
    val fileReader: FileReader = persistenceDIModule.fileReader
    val fileWriter: FileWriter[String, _] = persistenceDIModule.fileWriter

    override def execute: Either[TaskFailType, Unit] = {
      try {
        // find all relevant files, parse them into MetricDocuments on ALL-tag,
        // aggregate all
        val filteredFiles = directoryReader.listFiles(directorySubDir, _ => true)
        logger.info(s"found files to aggregate: $filteredFiles")
        val overallDoc: MetricDocument[Tag] = MetricDocument.empty(aggregationIdentifier)
        filteredFiles.foreach(file => {
          logger.info(s"adding file: $file")
          val lines: Seq[String] = fileReader.getSource(file).getLines()
            .filter(line => !line.startsWith("#") && line.trim.nonEmpty)
            .toSeq
          val headerColumns: Seq[String] = csvFormat.readHeader(lines.head)
          val rows: Seq[String] = lines.slice(1, lines.length)
          val doc: MetricDocument[Tag] = csvFormat.readDocument(headerColumns, rows, aggregationIdentifier)
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

  case class DoNothing() extends JobWrapUpFunction[Unit] {
    override def execute: Either[TaskFailType, Unit] = Right(())
  }

}
