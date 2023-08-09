/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio

import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.JsonStructDefsFormat
import de.awagen.kolibri.datatypes.types.JsonStructDefs.StructDef
import de.awagen.kolibri.definitions.io.json.ExecutionJsonProtocol.ExecutionFormat
import de.awagen.kolibri.fleet.zio.ServerEndpoints.ResponseContentProtocol.responseContentFormat
import de.awagen.kolibri.fleet.zio.ServerEndpoints.{ResponseContent, corsConfig}
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol
import de.awagen.kolibri.fleet.zio.metrics.Metrics.CalculationsWithMetrics.countAPIRequests
import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat, jsonFormat5}
import spray.json.{RootJsonFormat, enrichAny}
import zio.ZIO
import zio.http.HttpAppMiddleware.cors
import zio.http._

object JobDefsServerEndpoints {

  val ID_JOB_DEF = "jobDefinition"
  val TASK_DEF = "task"
  val JOB_POST_PATH = "job"
  val TASK_POST_PATH = "task"

  case class EndpointDef(id: String,
                         name: String,
                         endpoint: String,
                         payloadDef: StructDef[_],
                         description: String = "")

  object Endpoints {
    val taskSequencePostEndpoint: EndpointDef = EndpointDef(
      ID_JOB_DEF,
      "Configure distinct types of evaluations.",
      JOB_POST_PATH,
      JobDefinitionJsonProtocol.jobDefStructDef,
      "Endpoint for composition of needed processing."
    )

    val taskPostEndpoint = EndpointDef(
      TASK_DEF,
      "Configure single-step tasks such as aggregations or other analysis tasks",
      TASK_POST_PATH,
      ExecutionFormat.structDef,
      "Endpoint for composition of single tasks, such as aggregations or other analysis"
    )
  }

  implicit val endpointAndStructDefFormat: RootJsonFormat[EndpointDef] = jsonFormat5(EndpointDef)

  def jobDefEndpoints = Http.collectZIO[Request] {
    case Method.GET -> Root / "jobs" / "structs" =>
      ZIO.succeed(
        Response.json(ResponseContent(Seq(Endpoints.taskSequencePostEndpoint, Endpoints.taskPostEndpoint), "").toJson.toString())) @@ countAPIRequests("GET", "/jobs/structs")
  } @@ cors(corsConfig)

}
