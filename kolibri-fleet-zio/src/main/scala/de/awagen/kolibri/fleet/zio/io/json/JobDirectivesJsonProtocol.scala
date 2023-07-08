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

import de.awagen.kolibri.datatypes.types.FieldDefinitions._
import de.awagen.kolibri.datatypes.types.JsonStructDefs
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.definitions.io.json.WithStructDef
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat}

object JobDirectivesJsonProtocol extends DefaultJsonProtocol {

  private val TYPE_KEY = "type"
  private val NODE_ID_KEY = "nodeId"
  private val UNKNOWN_TYPE = "UNKNOWN"
  private val STOP_PROCESSING_TYPE = "STOP_PROCESSING"
  private val ONLY_NODE_TYPE = "ONLY_NODE"
  private val PROCESS_TYPE = "PROCESS"


  implicit object JobDirectivesFormat extends JsonFormat[JobDirective] with WithStructDef {
    override def read(json: JsValue): JobDirective = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case UNKNOWN_TYPE => JobDirectives.Unknown
        case STOP_PROCESSING_TYPE => JobDirectives.StopProcessing
        case ONLY_NODE_TYPE =>
          val nodeId = fields(NODE_ID_KEY).convertTo[String]
          JobDirectives.OnlyNode(nodeId)
        case PROCESS_TYPE => JobDirectives.Process
      }
    }

    override def write(obj: JobDirective): JsValue = obj match {
      case JobDirectives.Unknown => JsObject(Map(
        TYPE_KEY -> JsString(UNKNOWN_TYPE)
      ))
      case JobDirectives.StopProcessing => JsObject(Map(
        TYPE_KEY -> JsString(STOP_PROCESSING_TYPE)
      ))
      case JobDirectives.OnlyNode(nodeId) => JsObject(Map(
        TYPE_KEY -> JsString(ONLY_NODE_TYPE),
        NODE_ID_KEY -> JsString(nodeId)
      ))
      case JobDirectives.Process => JsObject(Map(
        TYPE_KEY -> JsString(PROCESS_TYPE)
      ))
    }

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(Seq(UNKNOWN_TYPE, STOP_PROCESSING_TYPE, ONLY_NODE_TYPE, PROCESS_TYPE)),
          required = true,
          description = "Type of the job directive."
        )
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          ONLY_NODE_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(NODE_ID_KEY),
              RegexStructDef(".+".r),
              required = true,
              description = "Hash of the node that shall alone process the respective job."
            )
          )
        ))
      )
    )
  }

}
