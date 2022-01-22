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
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, parameters, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import de.awagen.kolibri.base.config.AppConfig.persistenceModule
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.io.json.OrderedMultiValuesJsonProtocol.OrderedMultiValuesAnyFormat
import de.awagen.kolibri.base.io.reader.ReaderUtils.safeContentRead
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator, PermutatingIndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
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

  case class IndexedGeneratorInfo(totalNrOfSamples: Int, jsonDefinition: String, samples: Seq[Seq[String]]) extends DataMsg

  implicit val orderedMultiValuesBatchGeneratorFormat: RootJsonFormat[IndexedGeneratorInfo] = jsonFormat3(IndexedGeneratorInfo)

  /**
   * Data types and parsing methods from file type
   */
  object DataFileType extends Enumeration {

    case class Val(contentToData: String => JsArray) extends super.Val

    def stringToValueSeqFunc(castFunc: String => JsValue): String => JsArray = x => {
      JsArray(x.split("\n").map(x => x.trim)
        .filter(x => x.nonEmpty)
        .map(x => castFunc.apply(x))
        .toSeq:_*)
    }

    def byName(name: String): Val = name.toUpperCase match {
      case "PARAMETER" => PARAMETER
      case "HEADER" => HEADER
      case "BODIES" => BODIES
      case _ => throw new IllegalArgumentException(s"no DataFileType by name '$name' found")
    }

    val PARAMETER, HEADER, BODIES: Val = Val(stringToValueSeqFunc(x => JsString(x)))
  }

  val DATA_PATH_PREFIX = "data"
  val FILES_BY_TYPE_PATH = "filesByType"
  val READ_FILE_PATH = "readFile"
  val PARAM_TYPE = "type"

  val DIRECTORY_PATH_DELIMITER = "/"

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
              val path = s"$inputDataPath/${typeName.toUpperCase}"
              logger.debug(s"checking for files in path: $path")
              val resources: Seq[String] = fileOverviewReader
                .listResources(path, _ => true)
                .map(filepath => filepath.split(DIRECTORY_PATH_DELIMITER).last.trim)
              logger.debug(s"found resources: $resources")
              complete(StatusCodes.OK, resources.toJson.toString())
            }
            }
          }
        }
      })
  }

  def getValuesByTypeAndFile(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix(DATA_PATH_PREFIX) {
        path(READ_FILE_PATH) {
          parameters("type", "identifier") { (contentType, identifier) => {
            val dataType: DataFileType.Val = DataFileType.byName(contentType.toUpperCase)
            val fullPath = s"$inputDataPath/$contentType/$identifier"
            logger.debug(s"trying to read file: $fullPath")
            val content: String = safeContentRead(fullPath, "", logOnFail = true)
            logger.debug(s"content: $content")
            val result = dataType.contentToData.apply(content)
            logger.debug(s"result: $result")
            complete(StatusCodes.OK, result.toJson.toString)
          }
          }
        }
      })
  }

  def getIndexedGeneratorInfo(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix("generator") {
        path("info") {
          get {
            parameters("returnNSamples") { numSamples => {
              entity(as[String]) { generatorJson => {
                // pickup string of the actual response, then parse it here
                val values = generatorJson.parseJson.convertTo[OrderedMultiValues]
                val generators: Seq[IndexedGenerator[Any]] =
                  values.values.map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(x.getAll))
                val combinedGenerator: IndexedGenerator[Seq[Any]] = PermutatingIndexedGenerator(generators)
                val response = IndexedGeneratorInfo(
                  combinedGenerator.size,
                  generatorJson,
                  combinedGenerator.getPart(0, numSamples.toInt).mapGen(x => x.map(y => y.toString)).iterator.toSeq
                )
                complete(StatusCodes.OK, response.toJson.toString())
              }}
            }}
          }
        }
      })
  }

}
