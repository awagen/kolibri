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

import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.testclasses.UnitTestSpec
import spray.json._
import zio.ZIO
import zio.http.Client

class JobDefinitionJsonProtocolSpec extends UnitTestSpec {

  val justWaitJobDefinitionJson: String =
    """
      |{
      |  "type": "JUST_WAIT",
      |  "def": {
      |    "jobName": "waitingJob",
      |    "nrBatches": 10,
      |    "durationInMillis": 1000
      |  }
      |}
      |""".stripMargin

  val requestingTaskSequenceJobDefinitionJson: String =
    """
      |{
      |"type": "REQUESTING_TASK_SEQUENCE",
      |"def": {
      |  "jobName": "taskSequenceJob",
      |  "resourceDirectives": [
      |    {
      |      "type": "JUDGEMENT_PROVIDER",
      |      "values": {
      |        "resource": {
      |          "resourceType": "JUDGEMENT_PROVIDER",
      |          "identifier": "ident1"
      |        },
      |        "supplier": {
      |          "type": "JUDGEMENTS_FROM_FILE",
      |          "file": "data/test_judgements.txt"
      |        }
      |      }
      |    }
      |  ],
      |  "requestParameters": [
      |    {
      |      "type": "STANDALONE",
      |      "values": {
      |        "name": "q",
      |        "values_type": "URL_PARAMETER",
      |        "values": {
      |          "type": "FROM_ORDERED_VALUES_TYPE",
      |          "values": {
      |            "type": "FROM_VALUES_TYPE",
      |            "name": "q",
      |            "values": [
      |              "q1",
      |              "q2",
      |              "q3",
      |              "q4",
      |              "q5"
      |            ]
      |          }
      |        }
      |      }
      |    }
      |  ],
      |  "batchByIndex": 0,
      |  "taskSequence": [
      |    {
      |       "type": "REQUEST_PARSE",
      |       "parsingConfig": {
      |         "selectors": [
      |           {
      |             "name": "productIds",
      |             "castType": "STRING",
      |             "selector": "\\ data \\ products \\\\ productId"
      |           },
      |           {
      |             "name": "numFound",
      |             "castType": "DOUBLE",
      |             "selector": "\\ data \\ numFound"
      |           }
      |         ]
      |    },
      |    "taggingConfig": {
      |       "requestTagger": {
      |         "type": "REQUEST_PARAMETER",
      |         "parameter": "query",
      |         "extend": false
      |       },
      |       "parsingResultTagger": {
      |           "type": "NOTHING"
      |       }
      |   },
      |   "connections": [
      |     {
      |       "host": "test-service-1",
      |       "port": 80,
      |       "useHttps": false
      |     },
      |     {
      |       "host": "test-service-2",
      |       "port": 81,
      |       "useHttps": false
      |     },
      |     {
      |       "host": "test-service-3",
      |       "port": 81,
      |       "useHttps": false
      |     }
      |   ],
      |   "requestMode": "REQUEST_ALL_CONNECTIONS",
      |   "contextPath": "testContextPath",
      |   "fixedParams": {
      |     "k1": ["v1", "v2"]
      |   },
      |   "httpMethod": "GET",
      |   "successKeyName": "parsedResults",
      |   "failKeyName": "parsedResultsFailed"
      |  },
      |  {
      |    "type": "METRIC_CALCULATION",
      |    "parsedDataKey": "parsedResults",
      |    "calculations": [
      |      {
      |        "type": "IR_METRICS",
      |        "queryParamName": "q",
      |        "productIdsKey": "productIds",
      |        "judgementsResource": {
      |          "resourceType": "JUDGEMENT_PROVIDER",
      |          "identifier": "ident1"
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
      |  "successKeyName": "metricCalculationResults",
      |  "failKeyName": "metricCalculationFailure"
      |}
      |],
      |"metricRowResultKey": "metricCalculationResults"
      |}
      |}
      |""".stripMargin


  "JobDefinitionJsonProtocol" must {
    "correctly parse task sequence job definition" in {
      requestingTaskSequenceJobDefinitionJson.parseJson.convertTo[ZIO[Client, Throwable, JobDefinition[_, _, _ <: WithCount]]]
    }

    "correctly parse just-wait job definition" in {
      justWaitJobDefinitionJson.parseJson.convertTo[ZIO[Client, Throwable, JobDefinition[_, _, _ <: WithCount]]]
    }


  }

}
