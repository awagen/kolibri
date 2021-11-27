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
import akka.http.scaladsl.server.Directives.{complete, get, parameters, path, pathPrefix}
import akka.http.scaladsl.server.Route
import de.awagen.kolibri.base.config.AppConfig.persistenceModule
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.io.reader.{DataOverviewReader, Reader}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat}
import spray.json.enrichAny

object ResourceRoutes {

  private[this] val logger: Logger = LoggerFactory.getLogger(ResourceRoutes.getClass)

  val JSON_FILE_SUFFIX = ".json"
  val dataOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(JSON_FILE_SUFFIX))
  val contentReader: Reader[String, Seq[String]] = persistenceModule.persistenceDIModule.reader
  val jobTemplatePath: String = AppProperties.config.jobTemplatesPath.get.stripSuffix("/")
  val contentInfoFileName = "info.json"

  val TEMPLATES_PATH_PREFIX = "templates"
  val JOBS_PATH_PREFIX = "jobs"
  val OVERVIEW_PATH = "overview"
  val PARAM_TYPE = "type"
  val PARAM_IDENTIFIER = "identifier"
  val RESPONSE_TEMPLATE_KEY = "template"
  val RESPONSE_TEMPLATE_INFO_KEY = "info"


  def getJobTemplateOverviewForType(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix(TEMPLATES_PATH_PREFIX) {
        pathPrefix(JOBS_PATH_PREFIX) {
          path(OVERVIEW_PATH) {
            get {
              parameters(PARAM_TYPE) {typeName => {
                // retrieve the available json template definitions for the given type
                val resources: Seq[String] = dataOverviewReader
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

  def safeContentRead(path: String, default: String,  logOnFail: Boolean): String = {
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
