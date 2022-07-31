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
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.io.json.EnumerationJsonProtocol.dataFileTypeFormat
import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.{FormatOps, ValueSeqGenProviderFormat}
import de.awagen.kolibri.base.io.reader.FileReaderUtils.JsValueOps._
import de.awagen.kolibri.base.io.reader.FileReaderUtils._
import de.awagen.kolibri.base.io.reader.ReaderUtils.safeContentRead
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, FileReaderUtils, Reader}
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ParameterValuesImplicits.ParameterValueSeqToRequestBuilderModifier
import de.awagen.kolibri.base.processing.modifiers.ParameterValues._
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{CombinedModifier, RequestTemplateBuilderModifier}
import de.awagen.kolibri.datatypes.collections.generators.{IndexedGenerator, PermutatingIndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import scala.concurrent.{Await, ExecutionContextExecutor}


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
   *
   * @param fileType         - File type the file corresponds to
   * @param fileName         - the filename
   * @param identifier       - an identifer, specifying what data is used for. Lets say fileType is PARAMETER, the identifier
   *                         should correspond to the url parameter name (if set in the source file as line with prefix FILE_HEADER_IDENTIFIER_PREFIX)
   * @param description      - a description for the data (if set in the source file as line with prefix FILE_HEADER_DESCRIPTION_PREFIX)
   * @param totalNrOfSamples - specifies how many samples in total are contained in the file source
   * @param jsonDefinition   - the json definition corresponding to the OrderedMultiValues element given by the file (see OrderedMultiValuesJsonProtocol).
   *                         Allows retrieving the whole data by parsing the json definition into the object.
   * @param samples          - a selected nr of samples representing the file content
   */
  case class FileDataSourceInfo(group: String,
                                isMapping: Boolean,
                                fileType: DataFileType.Val,
                                fileName: String,
                                identifier: String,
                                description: String,
                                totalNrOfSamples: Int,
                                jsonDefinition: JsValue,
                                samples: JsArray) extends DataMsg

  implicit val fileDataSourceInfoFormat: RootJsonFormat[FileDataSourceInfo] = jsonFormat9(FileDataSourceInfo)
  implicit val baseFileDataSourceInfoFormat: RootJsonFormat[BaseFileDataSourceInfo] = jsonFormat3(BaseFileDataSourceInfo)

  /**
   * Data types and parsing methods from file type
   */
  object DataFileType extends Enumeration {

    case class Val(group: String,
                   isMapping: Boolean,
                   contentToData: String => JsValue,
                   subFolder: String,
                   valueToSize: JsValue => Int,
                   valueToSampleOfSize: Int => JsValue => JsArray) extends super.Val

    def byName(name: String): Val = name.toUpperCase match {
      case "PARAMETER" => PARAMETER
      case "PARAMETER_MAPPING_CSV" => PARAMETER_MAPPING_CSV
      case "PARAMETER_MAPPING_JSON" => PARAMETER_MAPPING_JSON
      case "HEADER" => HEADER
      case "HEADER_MAPPING_CSV" => HEADER_MAPPING_CSV
      case "HEADER_MAPPING_JSON" => HEADER_MAPPING_JSON
      case "BODIES" => BODIES
      case "BODIES_MAPPING_CSV" => BODIES_MAPPING_CSV
      case "BODIES_MAPPING_JSON" => BODIES_MAPPING_JSON
      case _ => throw new IllegalArgumentException(s"no DataFileType by name '$name' found")
    }

    val PARAMETER: Val = Val("PARAMETER", false, stringToValueSeqFunc(x => JsString(x)), "PARAMETER", JS_ARRAY_SIZE, num => jsArraySamples(num))
    val HEADER: Val = Val("HEADER", false, stringToValueSeqFunc(x => JsString(x)), "HEADER", JS_ARRAY_SIZE, num => jsArraySamples(num))
    val BODIES: Val = Val("BODIES", false, stringToValueSeqFunc(x => x.parseJson), "BODIES", JS_ARRAY_SIZE, num => jsArraySamples(num))
    val PARAMETER_MAPPING_CSV: Val = Val("PARAMETER", true, csvToMapping(x => x(1)), "PARAMETER_MAPPINGS/CSV", JS_OBJECT_KEY_SIZE, num => jsObjectMappingSamples(num))
    val PARAMETER_MAPPING_JSON: Val = Val("PARAMETER", true, parseJsonContent, "PARAMETER_MAPPINGS/JSON", JS_OBJECT_KEY_SIZE, num => jsObjectMappingSamples(num))
    val HEADER_MAPPING_CSV: Val = Val("HEADER", true, csvToMapping(x => x(1)), "HEADER_MAPPINGS/CSV", JS_OBJECT_KEY_SIZE, num => jsObjectMappingSamples(num))
    val HEADER_MAPPING_JSON: Val = Val("HEADER", true, parseJsonContent, "HEADER_MAPPINGS/JSON", JS_OBJECT_KEY_SIZE, num => jsObjectMappingSamples(num))
    val BODIES_MAPPING_CSV: Val = Val("BODIES", true, csvToMapping(x => x(1).parseJson), "BODIES_MAPPINGS/CSV", JS_OBJECT_KEY_SIZE, num => jsObjectMappingSamples(num))
    val BODIES_MAPPING_JSON: Val = Val("BODIES", true, parseJsonContent, "BODIES_MAPPINGS/JSON", JS_OBJECT_KEY_SIZE, num => jsObjectMappingSamples(num))

    val PARAMETER_TYPES: Seq[Val] = Seq(PARAMETER, PARAMETER_MAPPING_CSV, PARAMETER_MAPPING_JSON)
    val HEADER_TYPES: Seq[Val] = Seq(HEADER, HEADER_MAPPING_CSV, HEADER_MAPPING_JSON)
    val BODY_TYPES: Seq[Val] = Seq(BODIES, BODIES_MAPPING_CSV, BODIES_MAPPING_JSON)
  }

  private[this] val DATA_PATH_PREFIX = "data"
  private[this] val FILES_BY_TYPE_PATH = "filesByType"
  private[this] val READ_FILE_PATH = "readFile"
  private[this] val PARAM_TYPE = "type"
  private[this] val GENERATOR_PATH_PREFIX = "generator"
  private[this] val INFO_PATH = "info"
  private[this] val REQUEST_SAMPLE_PATH = "requestSample"

  private[this] val FILE_HEADER_IDENTIFIER_KEY = "identifier"
  private[this] val FILE_HEADER_DESCRIPTION_KEY = "description"
  private[this] val FILE_HEADER_CSV_COLUMN_DELIMITER_KEY = "delimiter"
  private[this] val DIRECTORY_PATH_DELIMITER = "/"
  private[this] val ALL_PATH = "all"

  private[this] val RETURN_N_SAMPLES_PARAM = "returnNSamples"


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

  /**
   * Given a particular type name, returns all available files for it
   *
   * @param typeName - the file type
   * @return
   */
  private[routes] def getFilesByType(typeName: String): Seq[String] = {
    val path = s"$inputDataPath/${DataFileType.byName(typeName.toUpperCase).subFolder}"
    logger.debug(s"checking for files in path: $path")
    try {
      fileOverviewReader
        .listResources(path, _ => true)
        .map(filepath => filepath.split(DIRECTORY_PATH_DELIMITER).last.trim)
    }
    catch {
      case e: Exception =>
        logger.error(s"Could not read directory for typeName '$typeName'", e)
        Seq.empty
    }
  }

  /**
   * For a particular type and identifier, load the available data and return it
   *
   * @param system
   * @return
   */
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

  /**
   * Given a particular DataFileType and identifier, read full content and apply the dataType's value casting.
   *
   * @param dataType   - the data type for the identifier
   * @param identifier - the resource identifier (e.g right now simple the filePath relative to the datatype's folder)
   * @return
   */
  private[routes] def getResourceInfoForType(dataType: DataFileType.Val, identifier: String): JsValue = {
    val fullPath = s"$inputDataPath/${dataType.subFolder}/$identifier"
    logger.debug(s"trying to read file: $fullPath")
    val content: String = safeContentRead(fullPath, "", logOnFail = true)
    logger.debug(s"content: $content")
    dataType.contentToData.apply(content)
  }

  /**
   * Endpoint to retrieve available data files for all data types.
   *
   * @param system
   * @return
   */
  def getAllIndexedGeneratorInfosForFileData(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix(DATA_PATH_PREFIX) {
        pathPrefix(INFO_PATH) {
          path(ALL_PATH) {
            get {
              parameters(RETURN_N_SAMPLES_PARAM) { numSamples => {
                var responses: Seq[FileDataSourceInfo] = Seq.empty
                DataFileType.values.foreach(x => {
                  val dType = DataFileType.byName(x.toString)
                  val filesForType: Seq[String] = getFilesByType(x.toString)
                  filesForType.map(y => {
                    val fullPath = s"$inputDataPath/${dType.subFolder}/$y"
                    try {
                      val fileContent: String = contentReader.getSource(fullPath).getLines().mkString("\n")
                      val metaData: Map[String, String] = FileReaderUtils.extractMetaData(fileContent)
                      logger.debug(s"found metaData for file '$fullPath': $metaData")
                      val dataIdentifier: String = metaData.getOrElse(FILE_HEADER_IDENTIFIER_KEY, "")
                      val dataDescription: String = metaData.getOrElse(FILE_HEADER_DESCRIPTION_KEY, "")
                      val parsedData = dType.contentToData.apply(fileContent)
                      val dataSize: Int = dType.valueToSize(parsedData)
                      val dataSamples: JsArray = dType.valueToSampleOfSize(numSamples.toInt).apply(parsedData)

                      val parameterValueType: ValueType.Value = dType match {
                        case e if DataFileType.PARAMETER_TYPES.contains(e) => ValueType.URL_PARAMETER
                        case e if DataFileType.BODY_TYPES.contains(e) => ValueType.BODY
                        case e if DataFileType.HEADER_TYPES.contains(e) => ValueType.HEADER
                      }

                      val parameterValueJsObject = dType match {
                        case e if e == DataFileType.PARAMETER || e == DataFileType.HEADER || e == DataFileType.BODIES =>
                          FormatOps.valuesFromLinesJson(parameterValueType, dataIdentifier, fullPath)
                        case e if e == DataFileType.PARAMETER_MAPPING_CSV || e == DataFileType.HEADER_MAPPING_CSV ||
                          e == DataFileType.BODIES_MAPPING_CSV =>
                          FormatOps.valuesFromCsvMapping(
                            parameterValueType,
                            dataIdentifier,
                            fullPath,
                            metaData.getOrElse(FILE_HEADER_CSV_COLUMN_DELIMITER_KEY, ",")
                          )
                        case e if e == DataFileType.PARAMETER_MAPPING_JSON || e == DataFileType.HEADER_MAPPING_JSON ||
                          e == DataFileType.BODIES_MAPPING_JSON =>
                          FormatOps.valuesFromJsonMapping(
                            parameterValueType,
                            dataIdentifier,
                            fullPath
                          )
                      }

                      val response = FileDataSourceInfo(
                        dType.group,
                        dType.isMapping,
                        dType,
                        fullPath,
                        dataIdentifier,
                        dataDescription,
                        dataSize,
                        parameterValueJsObject.parseJson,
                        dataSamples
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

  /**
   * Route takes a sequence of ValueSeqGenProvider json definitions
   * (which are either single standalong values or mappings with key provider and assigned values)
   * and generates the combinations resulting from permutating the ValueSeqGenProvider sequence
   *
   * @param system
   * @return
   */
  def getIndexedGeneratorInfoForValueSeqGenProviderSeqBody(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      pathPrefix(GENERATOR_PATH_PREFIX) {
        path(INFO_PATH) {
          post {
            parameters(RETURN_N_SAMPLES_PARAM) { numSamples => {
              entity(as[String]) { generatorJson => {
                val values = generatorJson.parseJson.convertTo[Seq[ValueSeqGenProvider]]
                val modifierGenerators: Seq[IndexedGenerator[RequestTemplateBuilderModifier]] = values.map(x => x.toSeqGenerator).map(x => x.mapGen(y => y.toModifier))
                val combinedGenerator: IndexedGenerator[Seq[Any]] = PermutatingIndexedGenerator(modifierGenerators)
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

  def getExampleQueriesForValueSeqGenProviderSequence(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    import scala.concurrent.duration._

    corsHandler(
      pathPrefix(GENERATOR_PATH_PREFIX) {
        path(REQUEST_SAMPLE_PATH) {
          post {
            parameters(RETURN_N_SAMPLES_PARAM) { (numSamples) => {
              entity(as[String]) { generatorJson => {
                val values = generatorJson.parseJson.convertTo[Seq[ValueSeqGenProvider]]
                val modifierGenerators: Seq[IndexedGenerator[RequestTemplateBuilderModifier]] = values.map(x => x.toSeqGenerator).map(x => x.mapGen(y => y.toModifier))
                val sampleRequestTemplateBuilderSupplier: () => RequestTemplateBuilder = () => new RequestTemplateBuilder().withContextPath("test")
                val permutatingModifierGenerator: IndexedGenerator[RequestTemplateBuilderModifier] = PermutatingIndexedGenerator(modifierGenerators)
                  .mapGen(x => CombinedModifier(x))

                val resultingRequestSample: Seq[JsObject] = permutatingModifierGenerator
                  .getPart(0, numSamples.toInt).iterator.toSeq.map(modifier => {
                  val requestTemplate: RequestTemplate = modifier.apply(sampleRequestTemplateBuilderSupplier.apply()).build()
                  val bodyStr: String = Await.result(requestTemplate.body.toStrict(1 second), 1 second)
                    .data.decodeString("UTF-8")
                  val body = bodyStr.trim match {
                    case "" => new JsObject(Map.empty)
                    case e => e.parseJson
                  }
                  JsObject(
                    Map(
                      "body" -> body,
                      "request" -> JsString(requestTemplate.query),
                      "header" -> JsObject(requestTemplate.headers
                        .map(header => (header.name(), JsString(header.value)))
                        .toMap
                      )
                    )
                  )
                })
                complete(StatusCodes.OK, resultingRequestSample.toJson.toString())
              }
              }
            }
            }
          }
        }
      })
  }

}
