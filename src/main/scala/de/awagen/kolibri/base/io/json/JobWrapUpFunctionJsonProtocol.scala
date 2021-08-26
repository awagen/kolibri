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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.config.di.modules.Modules
import de.awagen.kolibri.base.processing.execution.wrapup.JobWrapUpFunctions.{AggregateAllFromDirectory, DoNothing, JobWrapUpFunction}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

import scala.util.matching.Regex

object JobWrapUpFunctionJsonProtocol extends DefaultJsonProtocol {

  implicit object UnitWrapUpFunctionFormat extends JsonFormat[JobWrapUpFunction[Unit]] {
    override def read(json: JsValue): JobWrapUpFunction[Unit] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "AGGREGATE_ALL" =>
          val regex: Regex = fields("regex").convertTo[String].r
          val outputFilename: String = fields("outputFilename").convertTo[String]
          val directorySubDir: String = fields("subDir").convertTo[String]
          val persistenceDIModule: Modules.PersistenceDIModule = AppConfig.persistenceModule.persistenceDIModule
          AggregateAllFromDirectory(
            persistenceDIModule,
            directorySubDir,
            regex,
            outputFilename
          )
        case "DO_NOTHING" => DoNothing()
      }
    }

    // TODO
    override def write(obj: JobWrapUpFunction[Unit]): JsValue = """{}""".toJson
  }

}
