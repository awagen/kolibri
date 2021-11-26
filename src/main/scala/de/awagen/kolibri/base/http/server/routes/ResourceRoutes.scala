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
import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat}
import spray.json.enrichAny

object ResourceRoutes {

  val JSON_FILE_SUFFIX = ".json"
  val dataOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(JSON_FILE_SUFFIX))
  val contentReader: Reader[String, Seq[String]] = persistenceModule.persistenceDIModule.reader
  val jobTemplatePath: String = AppProperties.config.jobTemplatesPath.get.stripSuffix("/")


  def getJobTemplateOverviewForType(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix("templates") {
        pathPrefix("jobs") {
          path("overview") {
            get {
              parameters("type") { typeName => {
                // retrieve the available json template definitions for the given type
                val resources: Seq[String] = dataOverviewReader
                  .listResources(s"$jobTemplatePath/$typeName", _ => true)
                  .map(filepath => filepath.split("/").last)
                complete(StatusCodes.OK, resources.toJson.toString())
              }
              }
            }
          }
        }
      }
    )
  }

  def getJobTemplateByTypeAndIdentifier(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix("templates") {
        path("jobs") {
          get {
            parameters("type", "identifier") { (typeName, identifier) => {
              // retrieve the content of the definition file defined by type and identifier
              val filepath = s"$jobTemplatePath/$typeName/$identifier"
              try {
                val content = contentReader.getSource(filepath).getLines().mkString("\n")
                complete(StatusCodes.OK, content)
              }
              catch {
                case e: Exception =>
                  complete(StatusCodes.NotFound, e)
              }
            }}
          }
        }
      }
    )
  }

}
