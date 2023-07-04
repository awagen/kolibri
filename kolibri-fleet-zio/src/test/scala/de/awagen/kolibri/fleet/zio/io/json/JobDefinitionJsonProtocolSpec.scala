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
      |   "successKeyName": "successTestKey",
      |   "failKeyName": "failTestKey"
      |  }
      |  ],
      |  "metricRowResultKey": "testMetricResult"
      |}
      |}
      |""".stripMargin


  "JobDefinitionJsonProtocol" must {
    "correctly parse task sequence job definition" in {
      requestingTaskSequenceJobDefinitionJson.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]]
    }

    "correctly parse just-wait job definition" in {
      justWaitJobDefinitionJson.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]]
    }


  }

}
