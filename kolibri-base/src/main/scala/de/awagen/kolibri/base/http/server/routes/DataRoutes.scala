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


package de.awagen.kolibri.base.http.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, parameters, path, pathPrefix}
import akka.http.scaladsl.server.Route
import de.awagen.kolibri.base.config.AppConfig.persistenceModule
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.io.json.EnumerationJsonProtocol.dataFileTypeFormat
import de.awagen.kolibri.base.io.json.OrderedMultiValuesJsonProtocol
import de.awagen.kolibri.base.io.json.OrderedMultiValuesJsonProtocol.OrderedMultiValuesAnyFormat
import de.awagen.kolibri.base.io.reader.ReaderUtils.safeContentRead
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator, PermutatingIndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.values.DistinctValues
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import scala.concurrent.ExecutionContextExecutor


object DataRoutes extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val fileOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.split("/").last.contains("."))
  val inputDataPath: String = AppProperties.config.inputDataPath.get
    .stripSuffix("/")
  val contentReader: Reader[String, Seq[String]] = persistenceModule.persistenceDIModule.reader

  trait DataMsg extends KolibriSerializable

  case class BaseFileDataSourceInfo(totalNrOfSamples: Int, jsonDefinition: String, samples: Seq[Seq[String]]) extends DataMsg

  /**
   * Case class specifying the response for a single file data source.
   * @param fileType - File type the file corresponds to
   * @param fileName - the filename
   * @param identifier - an identifer, specifying what data is used for. Lets say fileType is PARAMETER, the identifier
   *                   should correspond to the url parameter name (if set in the source file as line with prefix FILE_HEADER_IDENTIFIER_PREFIX)
   * @param description - a description for the data (if set in the source file as line with prefix FILE_HEADER_DESCRIPTION_PREFIX)
   * @param totalNrOfSamples - specifies how many samples in total are contained in the file source
   * @param jsonDefinition - the json definition corresponding to the OrderedMultiValues element given by the file (see OrderedMultiValuesJsonProtocol).
   *                       Allows retrieving the whole data by parsing the json definition into the object.
   * @param samples - a selected nr of samples representing the file content
   */
  case class FileDataSourceInfo(fileType: DataFileType.Val, fileName: String, identifier: String, description: String, totalNrOfSamples: Int, jsonDefinition: String, samples: Seq[Seq[String]]) extends DataMsg

  implicit val fileDataSourceInfoFormat: RootJsonFormat[FileDataSourceInfo] = jsonFormat7(FileDataSourceInfo)
  implicit val baseFileDataSourceInfoFormat: RootJsonFormat[BaseFileDataSourceInfo] = jsonFormat3(BaseFileDataSourceInfo)

  /**
   * Data types and parsing methods from file type
   */
  object DataFileType extends Enumeration {

    case class Val(contentToData: String => JsArray) extends super.Val

    def stringToValueSeqFunc(castFunc: String => JsValue): String => JsArray = x => {
      JsArray(x.split("\n").map(x => x.trim)
        .filter(x => x.nonEmpty)
        .map(x => castFunc.apply(x))
        .toSeq: _*)
    }

    def byName(name: String): Val = name.toUpperCase match {
      case "PARAMETER" => PARAMETER
      case "HEADER" => HEADER
      case "BODIES" => BODIES
      case _ => throw new IllegalArgumentException(s"no DataFileType by name '$name' found")
    }

    val PARAMETER, HEADER, BODIES: Val = Val(stringToValueSeqFunc(x => JsString(x)))
  }

  private[this] val DATA_PATH_PREFIX = "data"
  private[this] val FILES_BY_TYPE_PATH = "filesByType"
  private[this] val READ_FILE_PATH = "readFile"
  private[this] val PARAM_TYPE = "type"
  private[this] val GENERATOR_PATH_PREFIX = "generator"
  private[this] val INFO_PATH = "info"

  private[this] val FILE_HEADER_IDENTIFIER_PREFIX = "#identifier="
  private[this] val FILE_HEADER_DESCRIPTION_PREFIX = "#description="
  private[this] val DIRECTORY_PATH_DELIMITER = "/"
  private[this] val ALL_PATH = "all"
  private[this] val EMPTY_STRING = ""


  /**
   * Endpoint to retrieve files with data for specific types.
   * Endpoint: data/filesByType?type=[type], where type is given by
   * DataFileType enum values to retrieve the respective type of data
   *
   * @return Seq of file paths for the given data type
   */
  def getDataFilesByType(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(DATA_PATH_PREFIX) {
        path(FILES_BY_TYPE_PATH) {
          get {
            parameters(PARAM_TYPE) { typeName => {
              val resources = getFilesByType(typeName)
              logger.debug(s"found resources: $resources")
              complete(StatusCodes.OK, resources.toJson.toString())
            }
            }
          }
        }
      })
  }

  def getFilesByType(typeName: String): Seq[String] = {
    val path = s"$inputDataPath/${typeName.toUpperCase}"
    logger.debug(s"checking for files in path: $path")
    fileOverviewReader
      .listResources(path, _ => true)
      .map(filepath => filepath.split(DIRECTORY_PATH_DELIMITER).last.trim)
  }

  def getValuesByTypeAndFile(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix(DATA_PATH_PREFIX) {
        path(READ_FILE_PATH) {
          parameters("type", "identifier") { (contentType, identifier) => {
            val dataType: DataFileType.Val = DataFileType.byName(contentType.toUpperCase)
            val result = getResourceInfoForType(dataType, identifier)
            logger.debug(s"result: $result")
            complete(StatusCodes.OK, result.toJson.toString)
          }
          }
        }
      })
  }

  def getResourceInfoForType(dataType: DataFileType.Val, identifier: String): JsArray = {
    val fullPath = s"$inputDataPath/${dataType.toString().toUpperCase}/$identifier"
    logger.debug(s"trying to read file: $fullPath")
    val content: String = safeContentRead(fullPath, "", logOnFail = true)
    logger.debug(s"content: $content")
    dataType.contentToData.apply(content)
  }

  def getAllIndexedGeneratorInfosForFileData(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix(DATA_PATH_PREFIX) {
        pathPrefix(INFO_PATH) {
          path(ALL_PATH) {
            get {
              parameters("returnNSamples") { numSamples => {
                var responses: Seq[FileDataSourceInfo] = Seq.empty
                DataFileType.values.foreach(x => {
                  val dType = DataFileType.byName(x.toString)
                  val filesForType: Seq[String] = getFilesByType(x.toString)
                  filesForType.map(y => {
                    val fullPath = s"$inputDataPath/${dType.toString().toUpperCase}/$y"
                    try {
                      var dataIdentifier: String = ""
                      var dataDescription: String = ""
                      // data samples collected from file
                      val fileContent: Seq[JsArray] = contentReader.getSource(fullPath)
                        .getLines()
                        .map(x => x.trim)
                        .map {
                          case x if x.startsWith(FILE_HEADER_IDENTIFIER_PREFIX) =>
                            dataIdentifier = x.stripPrefix(FILE_HEADER_IDENTIFIER_PREFIX).trim
                            EMPTY_STRING
                          case x if x.startsWith(FILE_HEADER_DESCRIPTION_PREFIX) =>
                            dataDescription = x.stripPrefix(FILE_HEADER_DESCRIPTION_PREFIX).trim
                            ""
                          case x => x
                        }
                        .filter(x => x.nonEmpty && !x.startsWith("#"))
                        .map(x => dType.contentToData.apply(x))
                        .toSeq
                      val values = DistinctValues[String](dataIdentifier, fileContent.map(x => x.toString()))
                      val fileMap: JsValue = Map(dataIdentifier -> fullPath).toJson
                      val orderedMultiValuesJsObject = JsObject(
                        OrderedMultiValuesJsonProtocol.TYPE_KEY -> JsString(OrderedMultiValuesJsonProtocol.FROM_FILES_LINES_TYPE),
                        OrderedMultiValuesJsonProtocol.VALUES_KEY -> fileMap
                      ).toString()
                      val response = FileDataSourceInfo(
                        dType,
                        fullPath,
                        dataIdentifier,
                        dataDescription,
                        values.values.size,
                        orderedMultiValuesJsObject,
                        values.values.take(numSamples.toInt).map(x => Seq(x))
                      )
                      responses = responses :+ response
                    }
                    catch {
                      case e: Exception =>
                        logger.warn(s"Failed reading file, skipping: '$fullPath''", e)
                    }
                  })
                })
                complete(StatusCodes.OK, responses.sortBy(x => x.fileName).toJson.toString())
              }
              }
            }
          }
        }
      })
  }

  def getIndexedGeneratorInfoForOrderedMultiValuesBody(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix(GENERATOR_PATH_PREFIX) {
        path(INFO_PATH) {
          get {
            parameters("returnNSamples") { numSamples => {
              entity(as[String]) { generatorJson => {
                // pickup string of the actual response, then parse it here
                val values = generatorJson.parseJson.convertTo[OrderedMultiValues]
                val generators: Seq[IndexedGenerator[Any]] =
                  values.values.map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(x.getAll))
                val combinedGenerator: IndexedGenerator[Seq[Any]] = PermutatingIndexedGenerator(generators)
                val response = BaseFileDataSourceInfo(
                  combinedGenerator.size,
                  generatorJson,
                  combinedGenerator.getPart(0, numSamples.toInt).mapGen(x => x.map(y => y.toString)).iterator.toSeq
                )
                complete(StatusCodes.OK, response.toJson.toString())
              }
              }
            }
            }
          }
        }
      })
  }

}
