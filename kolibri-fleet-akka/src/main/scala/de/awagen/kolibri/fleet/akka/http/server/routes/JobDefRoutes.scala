/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.akka.http.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher0, Route}
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.JsonStructDefsFormat
import de.awagen.kolibri.datatypes.types.JsonStructDefs.StructDef
import de.awagen.kolibri.fleet.akka.http.server.routes.JobDefRoutes.Endpoints.{fullSearchEvaluationEndpoint, queryBasedSearchEvaluationEndpoint}
import de.awagen.kolibri.fleet.akka.http.server.routes.JobTemplateResourceRoutes.TemplateTypeValidationAndExecutionInfo.{SEARCH_EVALUATION, SEARCH_EVALUATION_SHORT}
import de.awagen.kolibri.fleet.akka.io.json.{QueryBasedSearchEvaluationJsonProtocol, SearchEvaluationJsonProtocol}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


// TODO: unify with JobTemplateResourceRoutes
object JobDefRoutes extends DefaultJsonProtocol with CORSHandler {

  val ENDPOINTS_PATH_PREFIX = "endpoints"
  val JOBS_PREFIX = "jobs"
  val SEARCH_EVALUATION_PATH = "search_evaluation"

  val ID_SEARCH_EVALUATION = "search_evaluation"
  val ID_SEARCH_EVALUATION_SHORT = "search_evaluation_short"

  case class EndpointDef(id: String,
                         name: String,
                         endpoint: String,
                         payloadDef: StructDef[_],
                         description: String = "")

  object Endpoints {
    val fullSearchEvaluationEndpoint: EndpointDef = EndpointDef(
      ID_SEARCH_EVALUATION,
      "Search Evaluation (Full)",
      SEARCH_EVALUATION.requestPath,
      SearchEvaluationJsonProtocol.structDef,
      "Endpoint for search evaluation, providing the full range of configuration flexibility. " +
        "Note that for specific use cases, one of the endpoint definitions with less flexibility / options " +
        "might be more convenient."
    )
    val queryBasedSearchEvaluationEndpoint: EndpointDef = EndpointDef(
      ID_SEARCH_EVALUATION_SHORT,
      "Search Evaluation (Query-based, reduced configuration)",
      SEARCH_EVALUATION_SHORT.requestPath,
      QueryBasedSearchEvaluationJsonProtocol.structDef,
      "Endpoint for search evaluation, providing a reduced set of configuration flexibility. " +
        "Particularly useful in case common information retrieval metrics shall be calculated and no custom " +
        "calculations are needed"
    )
  }

  implicit val endpointAndStructDefFormat: RootJsonFormat[EndpointDef] = jsonFormat5(EndpointDef)

  def getSearchEvaluationEndpointAndJobDef(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = ENDPOINTS_PATH_PREFIX / JOBS_PREFIX / SEARCH_EVALUATION_PATH
    corsHandler(
      path(matcher) {
        get {
          complete(StatusCodes.OK, Seq(
            fullSearchEvaluationEndpoint,
            queryBasedSearchEvaluationEndpoint
          )
            .toJson.toString())
        }
      }
    )
  }

}
