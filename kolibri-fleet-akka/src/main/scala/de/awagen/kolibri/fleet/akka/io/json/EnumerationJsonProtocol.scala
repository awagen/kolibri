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


package de.awagen.kolibri.fleet.akka.io.json

import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.EnumerationProtocol
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnableSinkType.ActorRunnableSinkType
import de.awagen.kolibri.fleet.akka.http.server.routes.DataRoutes.DataFileType
import de.awagen.kolibri.fleet.akka.jobdefinitions.{MapTransformerFlows, ProcessingActorProps}
import de.awagen.kolibri.fleet.akka.jobdefinitions.ProcessingActorProps.ProcessingActorProps
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue}

object EnumerationJsonProtocol extends DefaultJsonProtocol {

  implicit object dataFileTypeFormat extends EnumerationProtocol[DataFileType.Val] {
    override def read(json: JsValue): DataFileType.Val = {
      json match {
        case JsString(txt) => DataFileType.byName(txt)
        case e => throw DeserializationException(s"Expected a value from DataFileType.Val but got value $e")
      }
    }
  }

  implicit object processingActorPropsFormat extends EnumerationProtocol[ProcessingActorProps] {
    override def read(json: JsValue): ProcessingActorProps = {
      json match {
        case JsString(txt) => ProcessingActorProps.withName(txt).asInstanceOf[ProcessingActorProps]
        case e => throw DeserializationException(s"Expected a value from ProcessingActorProps but got value $e")
      }
    }
  }

  implicit object mapTransformerFlowsFormat extends EnumerationProtocol[MapTransformerFlows.Val] {
    override def read(json: JsValue): MapTransformerFlows.Val = {
      json match {
        case JsString(txt) =>
          val enumVal = MapTransformerFlows.withName(txt)
          enumVal.asInstanceOf[MapTransformerFlows.Val]
        case e => throw DeserializationException(s"Expected a value from TransformerFlows but got value $e")
      }
    }
  }

  implicit object actorRunnableSinkTyoeFormat extends EnumerationProtocol[ActorRunnableSinkType] {
    override def read(json: JsValue): ActorRunnableSinkType = {
      json match {
        case JsString(txt) => ActorRunnableSinkType.withName(txt)
        case e => throw DeserializationException(s"Expected a value from ActorRunnableSinkType but got value $e")
      }
    }
  }

}
