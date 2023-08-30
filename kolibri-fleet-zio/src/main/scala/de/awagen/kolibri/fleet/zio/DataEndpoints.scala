/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio

import de.awagen.kolibri.datatypes.stores.mutable.PriorityStores.{BasePriorityStore, PriorityStore}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, StringConstantStructDef, StringStructDef, StructDef}
import de.awagen.kolibri.definitions.processing.execution.functions.AggregationFunctions
import de.awagen.kolibri.fleet.zio.DataEndpoints.ResultFileAttributes.{VALUE_PREFIX, evaluationColumnNames}
import de.awagen.kolibri.fleet.zio.DataEndpoints.ResultFileType.{CSV, ResultFileType}
import de.awagen.kolibri.fleet.zio.ServerEndpoints.ResponseContent
import de.awagen.kolibri.fleet.zio.ServerEndpoints.ResponseContentProtocol.responseContentFormat
import de.awagen.kolibri.fleet.zio.config.HttpConfig.corsConfig
import de.awagen.kolibri.fleet.zio.config.{AppConfig, AppProperties}
import de.awagen.kolibri.fleet.zio.metrics.Metrics.CalculationsWithMetrics.countAPIRequests
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.DefaultJsonProtocol.{JsValueFormat, StringJsonFormat, immSeqFormat, jsonFormat2, jsonFormat3, jsonFormat4, mapFormat}
import spray.json._
import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.stream.ZStream
import zio.{IO, Task, ZIO}

import java.io.IOException
import java.nio.charset.Charset

object DataEndpoints {

  object ResultFileType {

    def fromEnding(ending: String): ResultFileType = {
      ending.toUpperCase match {
        case "CSV" => CSV
        case "JSON" => JSON
        case _ => ANY
      }
    }

    sealed case class ResultFileType(fileEnding: String)

    val CSV = ResultFileType(".csv")

    val JSON = ResultFileType(".json")

    val ANY = ResultFileType("")

  }


  object ResultFileAttributes {

    val DIRECTORY_PATH_DELIMITER = "/"

    val FAIL_COUNT_PREFIX: String = "fail-count-"
    val WEIGHTED_FAIL_COUNT_PREFIX: String = "weighted-fail-count-"
    val FAIL_REASONS_PREFIX: String = "failReasons-"
    val SUCCESS_COUNT_PREFIX: String = "success-count-"
    val WEIGHTED_SUCCESS_COUNT_PREFIX: String = "weighted-success-count-"
    val VALUE_PREFIX: String = "value-"

    val SORT_BY_METRIC_PARAM = "sortByMetric"
    val TOP_N_PARAM = "topN"
    val REVERSED_PARAM = "reversed"

    val evaluationColumnNames: Seq[String] = Seq(
      FAIL_COUNT_PREFIX,
      WEIGHTED_FAIL_COUNT_PREFIX,
      FAIL_REASONS_PREFIX,
      SUCCESS_COUNT_PREFIX,
      WEIGHTED_SUCCESS_COUNT_PREFIX,
      VALUE_PREFIX
    )
  }

  /**
   * For a given path (relative to configured base folder), returns the
   * names of the folders contained in it (not full or relative path)
   */
  def getSubFolders(overviewReader: DataOverviewReader, folder: String): IO[IOException, Seq[String]] = {
    ZIO.attemptBlockingIO(overviewReader.listResources(folder, _ => true).map(x => x.stripSuffix("/").split("/").last))
  }

  /**
   * For a given path (relative to configured base folder), returns the
   * name of the files contained in it (not the full or relative path)
   */
  def getFilesForFolder(overviewReader: DataOverviewReader,
                        folder: String,
                        fileType: ResultFileType): IO[IOException, Seq[String]] = {

    ZIO.attemptBlockingIO(
      overviewReader.listResources(folder, name => name.endsWith(fileType.fileEnding))
        .map(x => x.split("/").last)
    )
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

  private[this] def readCsvFromPath(reader: Reader[String, Seq[String]],
                                    path: String,
                                    extractParamNamesFunc: Seq[String] => Seq[String] = paramNamesFromFirstLineFunc,
                                    extractMetricNamesFunc: Seq[String] => Seq[String] = containedMetricNamesFunc,
                                    extractColumnNamesFunc: Seq[String] => Seq[String] = columnNamesFromFirstLine,
                                    extractDataFunc: Seq[String] => Seq[Seq[String]] = dataAsLinesWithoutFirstAndSplitColumnsFunc): Task[CsvFileContent] = {
    ZIO.attemptBlocking({
      val lines: Seq[String] = reader.read(path)
        .filter(line => !line.startsWith("#"))
      val columnNames = extractColumnNamesFunc.apply(lines)
      val metricNames = extractMetricNamesFunc.apply(columnNames)
      val paramNames = extractParamNamesFunc.apply(lines)
      val data = extractDataFunc.apply(lines)
      CsvFileContent(paramNames, metricNames, columnNames, data)
    })
  }

  private[this] def readJsonFromPath(reader: Reader[String, Seq[String]], path: String): IO[IOException, JsValue] = {
    ZIO.attemptBlockingIO(reader.read(path).mkString("\n").parseJson)
  }

  private[this] def getDateToResultFolderMapping: ZIO[DataOverviewReader, Serializable, Map[String, Seq[String]]] = {
    for {
      overviewReader <- ZIO.service[DataOverviewReader]
      resultPath <- ZIO.fromOption(AppProperties.config.outputResultsPath.map(x => x.stripSuffix("/")))
      dateFolders <- getSubFolders(overviewReader, resultPath)
      dateToResultFolderMap <- ZStream.fromIterable(dateFolders)
        .mapZIO(dateFolder => getSubFolders(overviewReader, s"$resultPath/$dateFolder").map(folders => folders.map(f => s"$dateFolder/$f")))
        .runCollect
        .map(x => x.flatMap(y => y.toSeq))
        .map(x => x.map(y => {
          val parts = y.split("/")
          (parts(0), parts(1))
        }).groupMap(tuple => tuple._1)(value => value._2).view.mapValues(x => x.toSeq).toMap)
    } yield dateToResultFolderMap
  }

  private[this] def getResultFilesForDateAndJob(overviewReader: DataOverviewReader, date: String, job: String, resultFileType: ResultFileType): IO[IOException, Seq[String]] = {
    val folder = s"${AppProperties.config.outputResultsPath.get}/$date/$job"
    getFilesForFolder(overviewReader, folder, resultFileType)
  }

  private[this] def getResultContent(reader: Reader[String, Seq[String]], date: String, job: String, file: String): ZIO[Any, Throwable, JsValue] = {
    for {
      fileType <- ZIO.attempt(ResultFileType.fromEnding(file.split("\\.").last))
      filePath <- ZIO.succeed(s"${AppProperties.config.outputResultsPath.get}/$date/$job/$file")
      resultOpt <- ZIO.whenCase(fileType)({
        case CSV =>
          readCsvFromPath(reader, filePath).map(x => x.toJson)
        case _ =>
          readJsonFromPath(reader, filePath)
      })
      result <- ZIO.attempt(resultOpt.get)
    } yield result
  }

  case class SummarizeCommand(dateId: String,
                              jobId: String)

  private val DATE_ID_KEY = "dateId"
  private val JOB_ID_KEY = "jobId"

  // NOTE: could already pick the data available and make the selectors below restricted (e.g dateId and then the correct
  // jobIds
  val summarizeCommandStructDef: StructDef[_] =
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(DATE_ID_KEY),
          StringStructDef,
          required = true,
          description = "dateId for result selection."
        ),
        FieldDef(
          StringConstantStructDef(JOB_ID_KEY),
          StringStructDef,
          required = true,
          description = "jobId for result selection."
        )
      ),
      Seq.empty
    )


  implicit val summarizeCommandFormat = jsonFormat2(SummarizeCommand)

  def dataEndpoints = Http.collectZIO[Request] {
    case Method.GET -> Root / "results" / "folders" =>
      (for {
        dateToResultFolderMapping <- getDateToResultFolderMapping
        response <- ZIO.attempt(Response.json(ResponseContent[Map[String, Seq[String]]](dateToResultFolderMapping, "").toJson.toString))
      } yield response).catchAll(throwable =>
        ZIO.logWarning(s"Error on requesting result folders:\n$throwable")
          *> ZIO.succeed(Response.text(s"Failed requesting result folders"))
      ) @@ countAPIRequests("GET", "/results/folders")
    // endpoint for posting a summarize request. Will generate a summary of all results in the respective path
    // and write the result json into the summary folder within the respective job results folder
    case req@Method.POST -> Root / "results" / "summary" =>
      val summaryLockFilename = "summary.lock"
      (for {
        submittedBody <- req.body.asString(Charset.forName("UTF-8"))
        parsedCmd <- ZIO.attempt(submittedBody.parseJson.convertTo[SummarizeCommand])
        _ <- ZIO.logInfo(s"Received request to summarize results, cmd = '$submittedBody'")
        overviewReader <- ZIO.service[DataOverviewReader]
        summaryBaseFolder <- ZIO.succeed(s"${AppProperties.config.outputResultsPath.get}/${parsedCmd.dateId}/${parsedCmd.jobId}/summary/")
        existingSummaryFiles <- ZIO.attempt(overviewReader
          .listResources(
            summaryBaseFolder,
            _ => true).map(x => x.split("/").last)
        )
        _ <- ZIO.logInfo(s"Available summary files: $existingSummaryFiles")
        response <- ZIO.whenCase(existingSummaryFiles.contains(summaryLockFilename))({
          case false =>
            for {
              _ <- ZIO.logInfo(s"Trying to generate summarize results, cmd = '$submittedBody'")
              fileWriter <- ZIO.service[Writer[String, String, _]]
              lockFilePath <- ZIO.succeed(s"$summaryBaseFolder$summaryLockFilename")
              _ <- ZIO.fromEither(fileWriter.write("", lockFilePath))
              _ <- ZIO.attemptBlocking({
                AggregationFunctions.SummarizeJob(
                  jobResultsFolder = s"${AppProperties.config.outputResultsPath.get}/${parsedCmd.dateId}/${parsedCmd.jobId}",
                  overviewReader = AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(_ => true),
                  fileReader = AppConfig.persistenceModule.persistenceDIModule.reader,
                  writer = AppConfig.persistenceModule.persistenceDIModule.writer,
                  summarySubfolder = "summary"
                ).execute
              })
              response <- ZIO.attempt(Response.json(ResponseContent[String]("Accepted summary creation request", "").toJson.toString))
            } yield response
          case true =>
            ZIO.logInfo(s"Lock file exists, will not re-compute summary, cmd = '$submittedBody'") *>
              ZIO.attempt(Response.json(ResponseContent[String]("", "Lock file is set for summary, thus not processing summary request").toJson.toString))
        })
      } yield response.get).catchAll(throwable =>
        ZIO.logWarning(s"""Error on summarizing job results:\nmsg: ${throwable.getMessage}\ntrace: ${throwable.getStackTrace.mkString("\n")}""")
          *> ZIO.succeed(Response.text(s"Failed summarizing job results"))
      ) @@ countAPIRequests("POST", "/results/summarize")
    // retrieve the content of a specific summary
    case e@Method.GET -> Root / "results" / "summary" / dateId / jobId / "content" =>
      (for {
        summaryName <- ZIO.attempt(e.url.queryParams.get("file").map(x => x.head).getOrElse(""))
        filePath <- ZIO.succeed(s"${AppProperties.config.outputResultsPath.get}/$dateId/$jobId/summary/$summaryName")
        content <- readJsonFromPath(AppConfig.persistenceModule.persistenceDIModule.reader, filePath)
        response <- ZIO.attempt(Response.json(ResponseContent(content, "").toJson.toString))
      } yield response)
        .catchAll(throwable =>
          ZIO.logWarning(s"""Error on requesting summary file content:\nmsg: ${throwable.getMessage}\ntrace: ${throwable.getStackTrace.mkString("\n")}""")
            *> ZIO.succeed(Response.text(s"""Failed requesting summary file content, dateId = '$dateId', jobId = '$jobId', summaryName = '${e.url.queryParams.get("file").map(x => x.head).getOrElse("")}'"""))
        ) @@ countAPIRequests("GET", "/results/summary/[date]/[job]?file=[fileName]")
    // get overview of available summaries for dateId and jobId
    case Method.GET -> Root / "results" / "summary" / dateId / jobId =>
      (for {
        overviewReader <- ZIO.service[DataOverviewReader]
        files <- getFilesForFolder(overviewReader, s"${AppProperties.config.outputResultsPath.get}/$dateId/$jobId/summary", ResultFileType.JSON)
        response <- ZIO.attempt(Response.json(ResponseContent[Seq[String]](files, "").toJson.toString))
      } yield response).catchAll(throwable =>
        ZIO.logWarning(s"Error on requesting list of summary files for dateId '$dateId' and jobId '$jobId':\n$throwable")
          *> ZIO.succeed(Response.text(s"Failed requesting list of summary files for dateId '$dateId' and jobId '$jobId'"))
      ) @@ countAPIRequests("GET", "/results/summary/[date]/[job]")
    case e@Method.GET -> Root / "results" / date / job / "content" =>
      (for {
        reader <- ZIO.service[Reader[String, Seq[String]]]
        content <- getResultContent(reader, date, job, e.url.queryParams.get("file").map(x => x.head).getOrElse(""))
        response <- ZIO.attempt(Response.json(ResponseContent(content, "").toJson.toString))
      } yield response).catchAll(throwable =>
        ZIO.logWarning(s"""Error on requesting result file content:\nmsg: ${throwable.getMessage}\ntrace: ${throwable.getStackTrace.mkString("\n")}""")
          *> ZIO.succeed(Response.text(s"Failed requesting result file content"))
      ) @@ countAPIRequests("GET", "/results/[date]/[job]/content?file=[fileName]")
    case Method.GET -> Root / "results" / date / job =>
      (for {
        overviewReader <- ZIO.service[DataOverviewReader]
        files <- getResultFilesForDateAndJob(overviewReader, date, job, ResultFileType.JSON)
        response <- ZIO.attempt(Response.json(ResponseContent[Seq[String]](files, "").toJson.toString))
      } yield response).catchAll(throwable =>
        ZIO.logWarning(s"Error on requesting result folder content:\n$throwable")
          *> ZIO.succeed(Response.text(s"Failed requesting result folder content"))
      ) @@ countAPIRequests("GET", "/results/[date]/[job]")
  } @@ cors(corsConfig)

}
