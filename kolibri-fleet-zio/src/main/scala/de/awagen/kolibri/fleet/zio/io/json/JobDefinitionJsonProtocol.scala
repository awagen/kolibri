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

import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.definitions.domain.jobdefinitions.Batch
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object JobDefinitionJsonProtocol extends DefaultJsonProtocol {

  implicit object JobDefinitionFormat extends JsonFormat[JobDefinition[_]] {
    override def read(json: JsValue): JobDefinition[_] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "JUST_WAIT" =>
          val jobName = fields("jobName").convertTo[String]
          val nrBatches = fields("nrBatches").convertTo[Int]
          val durationInMillis = fields("durationInMillis").convertTo[Long]
          JobDefinition[Int](
            jobName = jobName,
            resourceSetup = Seq.empty,
            batches = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
              Range(0, nrBatches, 1)
                .map(batchNr => Batch(batchNr, ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(1))))
            ),
            taskSequence = Seq(SimpleWaitTask(durationInMillis)),
            resultConsumer = _ => ())
      }
    }

    // TODO
    override def write(obj: JobDefinition[_]): JsValue = """{}""".toJson
  }


}
