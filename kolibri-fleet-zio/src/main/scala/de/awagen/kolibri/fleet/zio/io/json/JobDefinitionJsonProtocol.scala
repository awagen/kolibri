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

import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.FieldDefinitions._
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.types.WithStructDef
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.{MultiAggregator, TagKeyMetricAggregationPerClassAggregator}
import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.definitions.io.json.ParameterValuesJsonProtocol
import de.awagen.kolibri.definitions.io.json.ResourceDirectiveJsonProtocol.GenericResourceDirectiveFormatStruct
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues.ParameterValuesImplicits.ParameterValueSeqToRequestBuilderModifier
import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues.ValueSeqGenDefinition
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.BatchGenerators.batchByGeneratorAtIndex
import de.awagen.kolibri.fleet.zio.config.AppConfig.JsonFormats.executionFormat
import de.awagen.kolibri.fleet.zio.config.AppConfig.JsonFormats.parameterValueJsonProtocol.ValueSeqGenDefinitionFormat
import de.awagen.kolibri.fleet.zio.config.AppConfig.JsonFormats.resourceDirectiveJsonProtocol.GenericResourceDirectiveFormat
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.numAggregatorsPerBatch
import de.awagen.kolibri.fleet.zio.config.{AppConfig, AppProperties}
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{BatchAggregationInfo, JobDefinition, simpleWaitJob}
import de.awagen.kolibri.fleet.zio.execution.JobMessagesImplicits._
import de.awagen.kolibri.fleet.zio.execution.ZIOTask
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.MutableCountingAggregator
import de.awagen.kolibri.fleet.zio.io.json.TaskJsonProtocol._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.DateUtils
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}
import zio.ZIO
import zio.http.Client
import zio.stream.ZStream

import scala.collection.immutable.Seq
import scala.util.Random

object JobDefinitionJsonProtocol extends DefaultJsonProtocol {

  private val TYPE_KEY = "type"
  private val DEF_KEY = "def"

  private val JOB_NAME_KEY = "jobName"
  private val NR_BATCHES_KEY = "nrBatches"
  private val DURATION_IN_MILLIS_KEY = "durationInMillis"

  private val RESOURCE_DIRECTIVES_KEY = "resourceDirectives"
  private val REQUEST_PARAMETERS_KEY = "requestParameters"
  private val TASK_SEQUENCE_KEY = "taskSequence"
  private val BATCH_BY_INDEX_KEY = "batchByIndex"
  private val METRIC_ROW_RESULT_KEY = "metricRowResultKey"
  private val WRAP_UP_ACTIONS_KEY = "wrapUpActions"


  private val JUST_WAIT_TYPE = "JUST_WAIT"
  private val SEARCH_EVALUATION_TYPE = "SEARCH_EVALUATION"
  private val QUERY_BASED_SEARCH_EVALUATION_TYPE = "QUERY_BASED_SEARCH_EVALUATION"
  private val REQUESTING_TASK_SEQUENCE_TYPE = "REQUESTING_TASK_SEQUENCE"


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

  val requestingTaskSequenceStructDef: StructDef[_] =
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(JOB_NAME_KEY),
          RegexStructDef(".+".r),
          required = true,
          description = "Name of the job."
        ),
        FieldDef(
          StringConstantStructDef(RESOURCE_DIRECTIVES_KEY),
          GenericSeqStructDef(GenericResourceDirectiveFormatStruct.structDef),
          required = true,
          description = "Resource directives defining resources that need to be loaded per node before " +
            "the job can be processed."
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_PARAMETERS_KEY),
          GenericSeqStructDef(ParameterValuesJsonProtocol.ValueSeqGenDefinitionFormatStruct.structDef),
          required = true,
          description = "Allows specification of combinations of url parameters, headers and bodies. " +
            "Note that standalone values are permutated with every other values, while mappings allow the mapping " +
            "of values of a key provider to other values that logically belong to that key. Can be used to restrict " +
            "the number of permutations to those that are actually meaningful."
        ),
        FieldDef(
          StringConstantStructDef(BATCH_BY_INDEX_KEY),
          IntStructDef,
          required = true,
          description = "Index of parameter by which to index. Refers to 0-based index of the respective parameter" +
            " in the requestParameters setting."
        ),
        FieldDef(
          StringConstantStructDef(TASK_SEQUENCE_KEY),
          GenericSeqStructDef(ZIOTaskFormat.structDef),
          required = true,
          description = "Sequence of tasks to execute. When defining tasks, make sure that data needed in later tasks" +
            " is generated in tasks coming before."
        ),
        FieldDef(
          StringConstantStructDef(METRIC_ROW_RESULT_KEY),
          RegexStructDef(".+".r),
          required = true,
          description = "Key under which the generated MetricRow is stored. Key can be referenced in tasks defined " +
            "later in the task sequence."
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
            Seq(JUST_WAIT_TYPE, SEARCH_EVALUATION_TYPE, QUERY_BASED_SEARCH_EVALUATION_TYPE, REQUESTING_TASK_SEQUENCE_TYPE)
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
          ),
          REQUESTING_TASK_SEQUENCE_TYPE -> Seq(
            FieldDef(
              StringConstantStructDef(DEF_KEY),
              requestingTaskSequenceStructDef,
              required = true,
              description = "Job definition as defined by sequence of tasks."
            )
          )
        ))
      )
    )

  implicit object JobDefinitionFormat extends JsonFormat[ZIO[Client, Throwable, JobDefinition[_, _, _ <: WithCount]]] with WithStructDef {
    override def read(json: JsValue): ZIO[Client, Throwable, JobDefinition[_, _, _ <: WithCount]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case JUST_WAIT_TYPE =>
          for {
            defFields <- ZIO.attempt(fields(DEF_KEY).asJsObject.fields)
            jobName <- ZIO.attempt(defFields(JOB_NAME_KEY).convertTo[String])
            nrBatches <- ZIO.attempt(defFields(NR_BATCHES_KEY).convertTo[Int])
            durationInMillis <- ZIO.attempt(defFields(DURATION_IN_MILLIS_KEY).convertTo[Long])
            batchAggregationInfo <- ZIO.attempt({
              BatchAggregationInfo(
                "DONE_WAITING",
                () => new MutableCountingAggregator(0, 0)
              )
            })
            jobDef <- ZIO.attempt(
              simpleWaitJob(
                jobName,
                nrBatches,
                durationInMillis,
                1,
                batchAggregationInfo
              ))
          } yield jobDef
        case SEARCH_EVALUATION_TYPE =>
          for {
            defFields <- ZIO.attempt(fields(DEF_KEY))
            searchEvaluationJobDef <- ZIO.attempt(AppConfig.JsonFormats.searchEvaluationJsonProtocol.SearchEvaluationFormat.read(defFields))
            jobDef <- searchEvaluationJobDef.toJobDef
          } yield jobDef

        case QUERY_BASED_SEARCH_EVALUATION_TYPE =>
          for {
            defFields <- ZIO.attempt(fields(DEF_KEY))
            searchEvaluationJobDef <- ZIO.attempt(AppConfig.JsonFormats.queryBasedSearchEvaluationJsonProtocol.QueryBasedSearchEvaluationFormat.read(defFields))
            jobDef <- searchEvaluationJobDef.toJobDef
          } yield jobDef
        case REQUESTING_TASK_SEQUENCE_TYPE =>
          for {
            defFields <- ZIO.attempt(fields(DEF_KEY).asJsObject.fields)
            jobName <- ZIO.attempt(defFields(JOB_NAME_KEY).convertTo[String])
            currentTimeInMillis <- ZIO.attempt(System.currentTimeMillis())
            resourceDirectives <- ZIO.attempt(defFields(RESOURCE_DIRECTIVES_KEY).convertTo[Seq[ResourceDirective[_]]])
            requestParameters <- ZIO.attempt(defFields(REQUEST_PARAMETERS_KEY).convertTo[Seq[ValueSeqGenDefinition[_]]])
            modifiers <- ZIO.attempt(requestParameters.map(x => x.toState).map(x => x.toSeqGenerator).map(x => x.mapGen(y => y.toModifier)))
            batchByIndex <- ZIO.attempt(defFields(BATCH_BY_INDEX_KEY).convertTo[Int])
            modifierBatches <- ZIO.attempt(batchByGeneratorAtIndex(batchByIndex = batchByIndex).apply(modifiers))
            nestedTaskSequenceEffect <- ZIO.attempt(defFields(TASK_SEQUENCE_KEY).convertTo[Seq[ZIO[Client, Throwable, Seq[ZIOTask[_]]]]])
            metricRowResultKey <- ZIO.attempt(defFields(METRIC_ROW_RESULT_KEY).convertTo[String])
            wrapUpActions <- ZIO.attempt(defFields.get(WRAP_UP_ACTIONS_KEY).map(x => x.convertTo[Seq[Execution[Any]]]).getOrElse(Seq.empty))
            // For now we assume that result is of type MetricRow
            aggregationInfo <- ZIO.attempt(BatchAggregationInfo[MetricRow, MetricAggregation[Tag]](
              successKey = metricRowResultKey,
              batchAggregatorSupplier = () => new MultiAggregator(() =>
                new TagKeyMetricAggregationPerClassAggregator(
                  identity,
                  ignoreIdDiff = false
                ), numAggregatorsPerBatch),
              writer = {
                val currentDay = DateUtils.timeInMillisToFormattedDate(currentTimeInMillis)
                AppConfig.persistenceModule.persistenceDIModule.metricAggregationWriter(
                  subFolder = s"${currentDay}/${jobName}",
                  x => {
                    val randomAdd: String = Random.alphanumeric.take(5).mkString
                    s"${x.toString()}-${AppProperties.config.node_hash}-$randomAdd"
                  }
                )
              }
            ))
            taskSequence <- ZStream.fromIterable(nestedTaskSequenceEffect)
              .runFoldZIO(Seq.empty[ZIOTask[_]])((oldSeq, newSeqEffect) => {
                for {
                  newSeq <- newSeqEffect
                } yield oldSeq ++ newSeq
              })
            jobDef <- ZIO.attempt(
              JobDefinition(
                jobName = jobName,
                resourceSetup = resourceDirectives,
                batches = modifierBatches,
                taskSequence = taskSequence,
                aggregationInfo = aggregationInfo,
                wrapUpActions = wrapUpActions
              )
                // NOTE: casting it here does not help against the above loss of type tag, the info gets lost due to generic type of the format
                .asInstanceOf[JobDefinition[RequestTemplateBuilderModifier, MetricRow, MetricAggregation[Tag]]]
            )
          } yield jobDef
      }
    }

    // TODO
    override def write(obj: ZIO[Client, Throwable, JobDefinition[_, _, _ <: WithCount]]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = jobDefStructDef
  }


}
