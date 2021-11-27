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
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.base.io.writer.Writers
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat}
import spray.json.{JsValue, enrichAny}

object ResourceRoutes {

  private[this] val logger: Logger = LoggerFactory.getLogger(ResourceRoutes.getClass)

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
  val PARAM_TYPE = "type"
  val PARAM_TEMPLATE_NAME = "templateName"
  val PARAM_IDENTIFIER = "identifier"
  val RESPONSE_TEMPLATE_KEY = "template"
  val RESPONSE_TEMPLATE_INFO_KEY = "info"


  def storeSearchEvaluationTemplate(implicit system: ActorSystem): Route = {
    import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol._
    corsHandler(
      path("store_job_template") {
        post {
          entity(as[JsValue]) {
            searchEvaluationJson => {
              parameters(PARAM_TYPE, PARAM_TEMPLATE_NAME) { (typeName, templateName) => {
                if (templateName.trim.isEmpty || typeName.startsWith("..") || templateName.startsWith("..")) complete(StatusCodes.BadRequest)
                try {
                  searchEvaluationJson.convertTo[SearchEvaluation]
                  val searchEvaluationContent: String = searchEvaluationJson.prettyPrint
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
                    fileWriter.write(searchEvaluationContent, templateFilePath)
                    complete(StatusCodes.Accepted)
                  }
                }
                catch {
                  case e: Exception =>
                    complete(StatusCodes.BadRequest, e)
                }
              }
              }
            }
          }
        }
      })
  }

  def getJobTemplateTypes(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        pathPrefix(JOBS_PATH_PREFIX) {
          path(TYPES_PATH) {
            get {
              val resources: Seq[String] = directoryOverviewReader
                .listResources(s"$jobTemplatePath", _ => true)
                .map(filepath => filepath.split("/").last.trim)
              complete(StatusCodes.OK, resources.toJson.toString())
            }
          }
        }
      }
    )
  }

  def getJobTemplateOverviewForType(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        pathPrefix(JOBS_PATH_PREFIX) {
          path(OVERVIEW_PATH) {
            get {
              parameters(PARAM_TYPE) { typeName => {
                // retrieve the available json template definitions for the given type
                val resources: Seq[String] = jsonFileOverviewReader
                  .listResources(s"$jobTemplatePath/$typeName", _ => true)
                  .map(filepath => filepath.split("/").last)
                  .filter(filename => filename != contentInfoFileName)
                complete(StatusCodes.OK, resources.toJson.toString())
              }
              }
            }
          }
        }
      }
    )
  }

  def safeContentRead(path: String, default: String, logOnFail: Boolean): String = {
    try {
      contentReader.getSource(path).getLines().mkString("\n")
    }
    catch {
      case e: Exception =>
        if (logOnFail) logger.warn(s"could not load content from path $path", e)
        default
    }
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
