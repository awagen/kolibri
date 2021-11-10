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
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.io.reader.DataOverviewReader
import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat}
import spray.json.enrichAny

object ResourceRoutes {

  val JSON_FILE_SUFFIX = ".json"
  val dataOverviewReader: DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(JSON_FILE_SUFFIX))
  val JOB_TEMPLATE_PATH = "templates/jobs"


  def getJobTemplateOverviewForType(implicit system: ActorSystem): Route = {
    corsHandler(
      pathPrefix("templates") {
        pathPrefix("jobs") {
          path("overview") {
            get {
              parameters("type") { typeName => {
                // retrieve the available json template definitions for the given type
                val resources: Seq[String] = dataOverviewReader
                  .listResources(s"$JOB_TEMPLATE_PATH/$typeName", _ => true)
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

}
