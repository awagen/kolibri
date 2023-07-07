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

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.definitions.domain.Connections
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations.JudgementsFromResourceIRMetricsCalculations
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.{CalculateMetricsTask, MergeTwoMetricRows, RequestJsonAndParseValuesTask, TwoMapInputCalculation}
import de.awagen.kolibri.fleet.zio.execution.ZIOTask
import de.awagen.kolibri.fleet.zio.io.json.TaskJsonProtocol._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.requests.RequestMode
import de.awagen.kolibri.fleet.zio.testclasses.UnitTestSpec
import spray.json._

class TaskJsonProtocolSpec extends UnitTestSpec {

  val REQUEST_MODE_PLACEHOLDER = "##REQUEST_MODE_PLACEHOLDER"

  def requestAndParseTaskJson: String =
    """
      |{
      |"type": "REQUEST_PARSE",
      |"parsingConfig": {
      |  "selectors": [
      |    {
      |      "name": "productIds",
      |      "castType": "STRING",
      |      "selector": "\\ data \\ products \\\\ productId"
      |    },
      |    {
      |      "name": "numFound",
      |      "castType": "DOUBLE",
      |      "selector": "\\ data \\ numFound"
      |    }
      |  ]
      |},
      |"taggingConfig": {
      |  "requestTagger": {
      |    "type": "REQUEST_PARAMETER",
      |    "parameter": "query",
      |    "extend": false
      |  },
      |  "parsingResultTagger": {
      |    "type": "NOTHING"
      |  }
      |},
      |"connections": [
      |  {
      |    "host": "test-service-1",
      |    "port": 80,
      |    "useHttps": false
      |  },
      |  {
      |    "host": "test-service-2",
      |    "port": 81,
      |    "useHttps": false
      |  },
      |  {
      |    "host": "test-service-3",
      |    "port": 81,
      |    "useHttps": false
      |  }
      |],
      |"requestMode": "##REQUEST_MODE_PLACEHOLDER",
      |"contextPath": "testContextPath",
      |"fixedParams": {
      |  "k1": ["v1", "v2"]
      |},
      |"httpMethod": "GET",
      |"successKeyName": "successTestKey",
      |"failKeyName": "failTestKey"
      |}""".stripMargin

  val calculateMetricsTaskJson =
    """
      |{
      |  "type": "METRIC_CALCULATION",
      |  "parsedDataKey": "parsedFields",
      |  "calculations": [
      |    {
      |      "type": "IR_METRICS",
      |      "queryParamName": "q",
      |      "productIdsKey": "productIds",
      |      "judgementsResource": {
      |        "resourceType": "JUDGEMENT_PROVIDER",
      |        "identifier": "ident1"
      |      },
      |      "metricsCalculation": {
      |        "metrics": [
      |          {"name": "DCG_10", "function": {"type": "DCG", "k": 10}},
      |          {"name": "NDCG_10", "function": {"type": "NDCG", "k": 10}},
      |          {"name": "PRECISION_k=4&t=0.1", "function": {"type": "PRECISION", "k": 4, "threshold":  0.1}},
      |          {"name": "RECALL_k=4&t=0.1", "function": {"type": "RECALL", "k": 4, "threshold":  0.1}},
      |          {"name": "ERR_10", "function": {"type": "ERR", "k": 10}}
      |        ],
      |        "judgementHandling": {
      |          "validations": [
      |            "EXIST_RESULTS",
      |            "EXIST_JUDGEMENTS"
      |          ],
      |          "handling": "AS_ZEROS"
      |        }
      |      }
      |    }
      |  ],
      |  "metricNameToAggregationTypeMapping": {
      |    "DCG_10": "DOUBLE_AVG",
      |    "NDCG_10": "DOUBLE_AVG",
      |    "PRECISION_k=4&t=0.1": "DOUBLE_AVG",
      |    "RECALL_k=4&t=0.1": "DOUBLE_AVG",
      |    "ERR_10": "DOUBLE_AVG"
      |  },
      |  "excludeParamsFromMetricRow": [],
      |  "successKeyName": "testSuccessKey1",
      |  "failKeyName": "testFailKey1"
      |}""".stripMargin

  val twoMapInputTask =
    """
      |{
      |"type": "MAP_COMPARISON",
      |"input1": "inputKey1",
      |"input2": "inputKey2",
      |"calculations": [{
      |  "type": "JACCARD_SIMILARITY",
      |  "name": "jaccard",
      |  "data1Key": "key1",
      |  "data2Key": "key2"
      |}],
      |"metricNameToAggregationTypeMapping": {
      |    "jaccard": "DOUBLE_AVG"
      |},
      |"excludeParamsFromMetricRow": [],
      |"successKeyName": "twoMapInSuccessKey",
      |"failKeyName": "twoMapInFailKey"
      |}
      |""".stripMargin

  val mergeTwoRowsTask =
    """
      |{
      |"type": "MERGE_METRIC_ROWS",
      |"input1": "inputKey1",
      |"input2": "inputKey2",
      |"successKeyName": "mergeTwoRowsSuccessKey",
      |"failKeyName": "mergeTwoRowsFailKey"
      |}
      |""".stripMargin


  val connection1 = Connections.Connection("test-service-1", 80, useHttps = false, None)
  val connection2 = Connections.Connection("test-service-2", 81, useHttps = false, None)
  val connection3 = Connections.Connection("test-service-3", 81, useHttps = false, None)


  "TaskJsonProtocol" must {

    "correctly parse sequence of request and parse tasks" in {
      // given, when
      val taskDefRequestAll: Seq[RequestJsonAndParseValuesTask] = requestAndParseTaskJson.replace(REQUEST_MODE_PLACEHOLDER, RequestMode.REQUEST_ALL_CONNECTIONS.toString).parseJson.convertTo[Seq[ZIOTask[WeaklyTypedMap[String]]]]
        .asInstanceOf[Seq[RequestJsonAndParseValuesTask]]
      val taskDefDistribute: Seq[RequestJsonAndParseValuesTask] = requestAndParseTaskJson.replace(REQUEST_MODE_PLACEHOLDER, RequestMode.DISTRIBUTE_LOAD.toString).parseJson.convertTo[Seq[ZIOTask[WeaklyTypedMap[String]]]]
        .asInstanceOf[Seq[RequestJsonAndParseValuesTask]]
      // then
      taskDefRequestAll.size mustBe 3
      taskDefDistribute.size mustBe 1
      taskDefRequestAll.head.connectionSupplier() mustBe connection1
      taskDefRequestAll(1).connectionSupplier() mustBe connection2
      taskDefRequestAll(2).connectionSupplier() mustBe connection3
      Set(connection1, connection2, connection3).contains(taskDefDistribute.head.connectionSupplier())
    }

    "correctly parse metrics calculation task" in {
      // given, when
      val task = calculateMetricsTaskJson.parseJson.convertTo[ZIOTask[MetricRow]]
        .asInstanceOf[CalculateMetricsTask]
      val calculations = task.calculations.asInstanceOf[Seq[JudgementsFromResourceIRMetricsCalculations]]
      val expectedMetricNames = Set("DCG_10", "NDCG_10", "PRECISION_k=4&t=0.1", "RECALL_k=4&t=0.1", "ERR_10")
      // then
      calculations.size mustBe 1
      calculations.head.names mustBe expectedMetricNames
      task.metricNameToAggregationTypeMapping.keySet mustBe expectedMetricNames
    }

    "correctly parse two map input calculation" in {
      // given, when
      val task = twoMapInputTask.parseJson.convertTo[ZIOTask[MetricRow]]
        .asInstanceOf[TwoMapInputCalculation]
      // then
      task.calculations.size mustBe 1
      task.calculations.flatMap(x => x.names).toSet mustBe Set("jaccard")
    }

    "correctly parse merge-two-metric-rows task" in {
      // given, when
      val task = mergeTwoRowsTask.parseJson.convertTo[ZIOTask[MetricRow]]
        .asInstanceOf[MergeTwoMetricRows]
      // then
      task.key1 mustBe NamedClassTyped[ProcessingMessage[MetricRow]]("inputKey1")
      task.key2 mustBe NamedClassTyped[ProcessingMessage[MetricRow]]("inputKey2")
      task.successKey mustBe NamedClassTyped[ProcessingMessage[MetricRow]]("mergeTwoRowsSuccessKey")
      task.failKey mustBe NamedClassTyped[ProcessingMessage[TaskFailType]]("mergeTwoRowsFailKey")
    }

  }

}
