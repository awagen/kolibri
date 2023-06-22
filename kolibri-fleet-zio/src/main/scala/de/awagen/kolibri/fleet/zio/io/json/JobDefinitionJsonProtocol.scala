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

import de.awagen.kolibri.datatypes.types.JsonStructDefs.{ConditionalFields, DoubleStructDef, IntStructDef, NestedFieldSeqStructDef, RegexStructDef, StringChoiceStructDef, StringConstantStructDef, StructDef}
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{BatchAggregationInfo, JobDefinition, simpleWaitJob}
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.countingAggregator
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}
import de.awagen.kolibri.fleet.zio.execution.JobMessagesImplicits._
import de.awagen.kolibri.datatypes.types.FieldDefinitions._
import de.awagen.kolibri.datatypes.types.WithStructDef

object JobDefinitionJsonProtocol extends DefaultJsonProtocol {

  private val TYPE_KEY = "type"
  private val DEF_KEY = "def"

  private val JOB_NAME_KEY = "jobName"
  private val NR_BATCHES_KEY = "nrBatches"
  private val DURATION_IN_MILLIS_KEY = "durationInMillis"

  private val JUST_WAIT_TYPE = "JUST_WAIT"
  private val SEARCH_EVALUATION_TYPE = "SEARCH_EVALUATION"
  private val QUERY_BASED_SEARCH_EVALUATION_TYPE = "QUERY_BASED_SEARCH_EVALUATION"


  val simpleWaitStructDef: StructDef[_] =
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(JOB_NAME_KEY),
          RegexStructDef(".+".r),
          required = true,
          description = "Name of the job."
        ),
        FieldDef(
          StringConstantStructDef(NR_BATCHES_KEY),
          IntStructDef,
          required = true,
          description = "Number of batches."
        ),
        FieldDef(
          StringConstantStructDef(DURATION_IN_MILLIS_KEY),
          DoubleStructDef,
          required = true,
          description = "Waiting time per element."
        )
      ),
      Seq.empty
    )

  val jobDefStructDef: StructDef[_] =
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(
            Seq(JUST_WAIT_TYPE, SEARCH_EVALUATION_TYPE, QUERY_BASED_SEARCH_EVALUATION_TYPE)
          ),
          required = true,
          description = "Name of the job"
        )
      ),
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          JUST_WAIT_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(DEF_KEY),
              simpleWaitStructDef,
              required = true,
              description = "Job definition for the wait job."
            )
          ),
          SEARCH_EVALUATION_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(DEF_KEY),
              AppConfig.JsonFormats.searchEvaluationJsonProtocol.structDef,
              required = true,
              description = "Job definition for the search evaluation job."
            )
          ),
          QUERY_BASED_SEARCH_EVALUATION_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(DEF_KEY),
              AppConfig.JsonFormats.queryBasedSearchEvaluationJsonProtocol.structDef,
              required = true,
              description = "Job definition for the search evaluation job."
            )
          )
        ))
      )
    )

  implicit object JobDefinitionFormat extends JsonFormat[JobDefinition[_, _, _ <: WithCount]] with WithStructDef {
    override def read(json: JsValue): JobDefinition[_, _, _ <: WithCount] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case JUST_WAIT_TYPE =>
          val defFields: Map[String, JsValue] = fields(DEF_KEY).asJsObject.fields
          val jobName = defFields(JOB_NAME_KEY).convertTo[String]
          val nrBatches = defFields(NR_BATCHES_KEY).convertTo[Int]
          val durationInMillis = defFields(DURATION_IN_MILLIS_KEY).convertTo[Long]
          val batchAggregationInfo: BatchAggregationInfo[Unit, JobDefinitions.ValueWithCount[Int]] = BatchAggregationInfo(
            SimpleWaitTask.successKey,
            () => countingAggregator(0, 0)
          )
          simpleWaitJob(
            jobName,
            nrBatches,
            durationInMillis,
            1,
            batchAggregationInfo
          )
        case SEARCH_EVALUATION_TYPE =>
          val defFields = fields(DEF_KEY)
          val searchEvaluationJobDef = AppConfig.JsonFormats.searchEvaluationJsonProtocol.SearchEvaluationFormat.read(defFields)
          searchEvaluationJobDef.toJobDef
        case QUERY_BASED_SEARCH_EVALUATION_TYPE =>
          val defFields = fields(DEF_KEY)
          val searchEvaluationJobDef = AppConfig.JsonFormats.queryBasedSearchEvaluationJsonProtocol.QueryBasedSearchEvaluationFormat.read(defFields)
          searchEvaluationJobDef.toJobDef
      }
    }

    // TODO
    override def write(obj: JobDefinition[_, _, _ <: WithCount]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = jobDefStructDef
  }


}
