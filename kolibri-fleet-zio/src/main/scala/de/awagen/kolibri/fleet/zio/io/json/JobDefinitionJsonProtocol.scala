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


package de.awagen.kolibri.fleet.zio.io.json

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{JobDefinition, simpleWaitJob}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object JobDefinitionJsonProtocol extends DefaultJsonProtocol {

  implicit object JobDefinitionFormat extends JsonFormat[JobDefinition[_, _, _]] {
    override def read(json: JsValue): JobDefinition[_, _, _] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "JUST_WAIT" =>
          val jobName = fields("jobName").convertTo[String]
          val nrBatches = fields("nrBatches").convertTo[Int]
          val durationInMillis = fields("durationInMillis").convertTo[Long]
          simpleWaitJob(jobName, nrBatches, durationInMillis)
      }
    }

    // TODO
    override def write(obj: JobDefinition[_, _, _]): JsValue = """{}""".toJson
  }


}
