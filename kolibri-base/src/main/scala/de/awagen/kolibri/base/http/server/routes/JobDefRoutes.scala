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
import akka.http.scaladsl.server.{PathMatcher0, Route}
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.JsonStructDefsFormat
import de.awagen.kolibri.datatypes.types.JsonStructDefs.StructDef
import akka.http.scaladsl.server.Directives._
import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JobDefRoutes extends DefaultJsonProtocol with CORSHandler {

  val ENDPOINTS_PATH_PREFIX = "endpoints"
  val JOBS_PREFIX = "jobs"
  val SEARCH_EVALUATION_PATH = "search_evaluation"

  case class EndpointAndStructDef(endpoint: String, jobDef: StructDef[_])

  implicit val endpointAndStructDefFormat: RootJsonFormat[EndpointAndStructDef] = jsonFormat2(EndpointAndStructDef)

  def getSearchEvaluationEndpointAndJobDef(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = ENDPOINTS_PATH_PREFIX / JOBS_PREFIX / SEARCH_EVALUATION_PATH
    corsHandler(
      path(matcher) {
        get {
          val result = EndpointAndStructDef("search_eval_no_ser", SearchEvaluationJsonProtocol.structDef)
          complete(StatusCodes.OK, result.toJson.toString())
        }
      }
    )
  }

}
