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
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.io.json.QueryBasedSearchEvaluationJsonProtocol.queryBasedSearchEvaluationFormat
import de.awagen.kolibri.base.io.reader.ReaderUtils.safeContentRead
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.base.io.writer.Writers
import de.awagen.kolibri.base.processing.JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat, jsonFormat3, mapFormat}
import spray.json.{JsValue, JsonReader, RootJsonFormat, enrichAny}

import scala.reflect.runtime.universe._
import scala.reflect.runtime.universe.typeOf


// TODO: unify with JobDefRoutes. Refers especially to the type checking below before a
//  job template is stored, while job structural descriptions are provided in JobDefRoutes
object JobTemplateResourceRoutes {

  /**
    * Encapsulates validation that the json of passed type can indeed be parsed to the correct type.
    * Also brings the path against which the template can be send as body to cause its actual execution.
    */
  object TemplateTypeValidationAndExecutionInfo extends Enumeration {

    import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol._

    type TemplateTypeValidationAndExecutionInfo = Val[_]

    sealed case class Val[+T: TypeTag](requestPath: String)(implicit jsonReader: JsonReader[T]) extends super.Val {

      def isValid(value: JsValue): Boolean = {
        try {
          value.convertTo[T]
          true
        }
        catch {
          case e: Exception =>
            logger.warn(s"json input conversion to type '${typeOf[T]}' failed", e)
            false
        }
      }
    }

    def getByNameFunc(nameNormalizeFunc: String => String = x => x.toUpperCase.replace("-", "_")): String => Option[Val[_]] = {
      name => {
        val normalizedName = nameNormalizeFunc.apply(name)
        var result: Option[Val[_]] = None
        try {
          val value = TemplateTypeValidationAndExecutionInfo.withName(normalizedName).asInstanceOf[Val[_]]
          result = Some(value)
        }
        catch {
          case _: Exception =>
            logger.warn(s"TemplatePathToJsonValidation of name $normalizedName not found")
        }
        result
      }
    }

    val SEARCH_EVALUATION: Val[_] = Val[SearchEvaluationDefinition]("search_eval_no_ser")
    val SEARCH_EVALUATION_SHORT: Val[_] = Val[QueryBasedSearchEvaluationDefinition]("search_eval_query_reduced")

  }

  private[this] val logger: Logger = LoggerFactory.getLogger(JobTemplateResourceRoutes.getClass)

  val JSON_FILE_SUFFIX = ".json"
  val jsonFileOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(JSON_FILE_SUFFIX))
  val directoryOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => !x.split("/").last.contains("."))
  val contentReader: Reader[String, Seq[String]] = persistenceModule.persistenceDIModule.reader
  val fileWriter: Writers.Writer[String, String, _] = persistenceModule.persistenceDIModule.writer
  val jobTemplatePath: String = AppProperties.config.jobTemplatesPath.get.stripSuffix("/")
  val contentInfoFileName = "info.json"

  val TEMPLATES_PATH_PREFIX = "templates"
  val JOBS_PATH_PREFIX = "jobs"
  val TYPES_PATH = "types"
  val OVERVIEW_PATH = "overview"
  val ALL_PATH = "all"
  val PARAM_TYPE = "type"
  val PARAM_TEMPLATE_NAME = "templateName"
  val PARAM_IDENTIFIER = "identifier"
  val RESPONSE_TEMPLATE_KEY = "template"
  val RESPONSE_TEMPLATE_INFO_KEY = "info"


  def storeSearchEvaluationTemplate(implicit system: ActorSystem): Route = {
    import spray.json._
    corsHandler(
      path("store_job_template") {
        post {
          entity(as[String]) {
            jsonString => {
              val json: JsValue = jsonString.parseJson
              parameters(PARAM_TYPE, PARAM_TEMPLATE_NAME) { (typeName, templateName) => {
                if (templateName.trim.isEmpty || typeName.startsWith("..") || templateName.startsWith("..")) complete(StatusCodes.BadRequest)
                val isValid = TemplateTypeValidationAndExecutionInfo
                  .getByNameFunc()(typeName)
                  .exists(func => func.isValid(json))
                if (!isValid) {
                  val message: String = s"no valid type for typeName: '$typeName'"
                  logger.warn(message)
                  complete(StatusCodes.BadRequest, message)
                }
                else {
                  val jsonString: String = json.prettyPrint
                  // now check whether file corresponding to template name is already there, then deny, otherwise write new template
                  val fullTemplateName: String = templateName.stripPrefix("/").stripSuffix("/").trim match {
                    case n if n.endsWith(".json") => n
                    case n => s"$n.json"
                  }
                  val templateFilePath = s"$jobTemplatePath/$typeName/$fullTemplateName"
                  logger.info(s"checking file path for existence: $templateFilePath")
                  var fileExists: Boolean = false
                  try {
                    contentReader.getSource(templateFilePath)
                    fileExists = true
                  }
                  catch {
                    case _: Exception =>
                      fileExists = false
                  }
                  if (fileExists) complete(StatusCodes.Conflict)
                  else {
                    fileWriter.write(jsonString, templateFilePath)
                    complete(StatusCodes.Accepted)
                  }
                }
              }
              }
            }
          }
        }
      })
  }

  def getAvailableJobTemplateTypes(): Seq[String] = {
    directoryOverviewReader
    .listResources(s"$jobTemplatePath", _ => true)
    .map(filepath => filepath.split("/").last.trim)
  }

  def getJobTemplateTypes(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        pathPrefix(JOBS_PATH_PREFIX) {
          path(TYPES_PATH) {
            get {
              val resources: Seq[String] = getAvailableJobTemplateTypes()
              complete(StatusCodes.OK, resources.toJson.toString())
            }
          }
        }
      }
    )
  }

  def getAvailableJobTemplatesForType(templateType: String): Seq[String] = {
    jsonFileOverviewReader
    .listResources(s"$jobTemplatePath/$templateType", _ => true)
    .map(filepath => filepath.split("/").last)
    .filter(filename => filename != contentInfoFileName)
  }

  def getJobTemplateOverviewForType(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        pathPrefix(JOBS_PATH_PREFIX) {
          path(OVERVIEW_PATH) {
            get {
              parameters(PARAM_TYPE) { typeName => {
                // retrieve the available json template definitions for the given type
                val resources: Seq[String] = getAvailableJobTemplatesForType(typeName)
                complete(StatusCodes.OK, resources.toJson.toString())
              }
              }
            }
          }
        }
      }
    )
  }

  case class TemplateInfo(templateType: String, templateId: String, content: String)

  implicit val templateInfoFormat: RootJsonFormat[TemplateInfo] = jsonFormat3(TemplateInfo)


  def getTemplateTypeToAvailableTemplatesMapping(): Map[String, Seq[TemplateInfo]] = {
    val templateTypes: Seq[String] = getAvailableJobTemplateTypes()
    templateTypes.map(templateType => {
      val jobTemplates: Seq[TemplateInfo] = getAvailableJobTemplatesForType(templateType)
        .map(path => {
          val relativePath = s"$jobTemplatePath/$templateType/$path"
          val content: String = safeContentRead(relativePath, "{}", logOnFail = true)
          TemplateInfo(templateType, path, content)
        })
      (templateType, jobTemplates)
    }).toMap
  }

  def getAvailableTemplatesByType(implicit  system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        pathPrefix(JOBS_PATH_PREFIX) {
          path(ALL_PATH) {
            get {
              val mapping: Map[String, Seq[TemplateInfo]] = getTemplateTypeToAvailableTemplatesMapping()
              complete(StatusCodes.OK, mapping.toJson.toString())
            }
          }
        }
      })
  }

  def getJobTemplateByTypeAndIdentifier(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        path(JOBS_PATH_PREFIX) {
          get {
            parameters(PARAM_TYPE, PARAM_IDENTIFIER) { (typeName, identifier) => {
              // retrieve the content of the definition file defined by type and identifier
              val filepath = s"$jobTemplatePath/$typeName/$identifier"
              val infoFilepath = s"$jobTemplatePath/$typeName/$contentInfoFileName"
              // pick the content
              val content: String = safeContentRead(filepath, "{}", logOnFail = true)
              // pick the information corresponding to the job definition fields
              val contentFieldInfo = safeContentRead(infoFilepath, "{}", logOnFail = false)
              if (content.trim.isEmpty) complete(StatusCodes.NotFound)
              val contentAndInfo: String = s"""{"$RESPONSE_TEMPLATE_KEY": $content, "$RESPONSE_TEMPLATE_INFO_KEY": $contentFieldInfo}"""
              complete(StatusCodes.OK, contentAndInfo)
            }
            }
          }
        }
      }
    )
  }

}
