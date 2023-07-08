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

import de.awagen.kolibri.datatypes.types.JsonStructDefs
import de.awagen.kolibri.datatypes.types.JsonStructDefs.NestedFieldSeqStructDef
import de.awagen.kolibri.definitions.io.json.WithStructDef
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates.BatchProcessingStatus
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.FieldDefinitions._
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat}

object BatchProcessingStatusJsonProtocol extends DefaultJsonProtocol {

  private val TYPE_KEY = "type"
  private val NODE_ID_KEY = "nodeId"
  private val FAIL_REASON_KEY = "failReason"
  private val OPEN_TYPE = "OPEN"
  private val IN_PROGRESS_TYPE = "IN_PROGRESS"
  private val DONE_TYPE = "IN_PROGRESS"
  private val FAILED_TYPE = "FAILED"
  private val DONE_WITH_FAILED_POST_ACTIONS_TYPE = "DONE_WITH_FAILED_POST_ACTIONS"

  implicit object BatchProcessingStatusFormat extends JsonFormat[BatchProcessingStatus] with WithStructDef {
    override def read(json: JsValue): BatchProcessingStatus = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case OPEN_TYPE => BatchProcessingStates.Open
        case IN_PROGRESS_TYPE =>
          val nodeId = fields(NODE_ID_KEY).convertTo[String]
          BatchProcessingStates.InProgress(nodeId)
        case DONE_TYPE => BatchProcessingStates.Done
        case FAILED_TYPE =>
          val failReason = fields(FAIL_REASON_KEY).convertTo[String]
          BatchProcessingStates.Failed(failReason)
        case DONE_WITH_FAILED_POST_ACTIONS_TYPE =>
          BatchProcessingStates.DoneWithFailedPostActions
      }
    }

    override def write(obj: BatchProcessingStatus): JsValue = obj match {
      case BatchProcessingStates.Open => JsObject(Map(
        TYPE_KEY -> JsString(OPEN_TYPE)
      ))
      case BatchProcessingStates.InProgress(nodeId) => JsObject(Map(
        TYPE_KEY -> JsString(IN_PROGRESS_TYPE),
        NODE_ID_KEY -> JsString(nodeId)
      ))
      case BatchProcessingStates.Done => JsObject(Map(
        TYPE_KEY -> JsString(DONE_TYPE)
      ))
      case BatchProcessingStates.Failed(failReason) => JsObject(Map(
        TYPE_KEY -> JsString(FAILED_TYPE),
        FAIL_REASON_KEY -> JsString(failReason)
      ))
      case BatchProcessingStates.DoneWithFailedPostActions => JsObject(Map(
        TYPE_KEY -> JsString(DONE_WITH_FAILED_POST_ACTIONS_TYPE)
      ))
    }

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(Seq(OPEN_TYPE, IN_PROGRESS_TYPE, DONE_TYPE, FAILED_TYPE, DONE_WITH_FAILED_POST_ACTIONS_TYPE)),
          required = true,
          description = "Type of the batch processing status."
        )
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          IN_PROGRESS_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(NODE_ID_KEY),
              RegexStructDef(".+".r),
              required = true,
              description = "Hash of the node that is processing the batch."
            )
          ),
          FAILED_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(FAIL_REASON_KEY),
              StringStructDef,
              required = true,
              description = "Reason for the processing failure."
            )
          )
        ))
      )
    )
  }

}
