/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.metrics.aggregation.writer

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricFormatTestHelper.{doc, histogramDoc1, metricsNameAggregationTypeMapping}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

import scala.util.matching.Regex

class JsonMetricDocumentFormatSpec extends UnitTestSpec {

  val plainMetricDocJson: String =
    """
      |{
      |  "data": [
      |    {
      |      "datasets": [
      |        {
      |          "data": [
      |            0.2,
      |            0.0,
      |            0.0
      |          ],
      |          "failReasons": [
      |            {},
      |            {},
      |            {}
      |          ],
      |          "failSamples": [
      |            0,
      |            0,
      |            0
      |          ],
      |          "name": "metrics1",
      |          "successSamples": [
      |            1,
      |            0,
      |            0
      |          ],
      |          "weightedFailSamples": [
      |            0.0,
      |            0.0,
      |            0.0
      |          ],
      |          "weightedSuccessSamples": [
      |            1.0,
      |            0.0,
      |            0.0
      |          ]
      |        },
      |        {
      |          "data": [
      |            0.4,
      |            0.0,
      |            0.0
      |          ],
      |          "failReasons": [
      |            {},
      |            {},
      |            {}
      |          ],
      |          "failSamples": [
      |            0,
      |            0,
      |            0
      |          ],
      |          "name": "metrics2",
      |          "successSamples": [
      |            1,
      |            0,
      |            0
      |          ],
      |          "weightedFailSamples": [
      |            0.0,
      |            0.0,
      |            0.0
      |          ],
      |          "weightedSuccessSamples": [
      |            1.0,
      |            0.0,
      |            0.0
      |          ]
      |        },
      |        {
      |          "data": [
      |            0.0,
      |            0.1,
      |            0.0
      |          ],
      |          "failReasons": [
      |            {},
      |            {},
      |            {}
      |          ],
      |          "failSamples": [
      |            0,
      |            0,
      |            0
      |          ],
      |          "name": "metrics3",
      |          "successSamples": [
      |            0,
      |            1,
      |            0
      |          ],
      |          "weightedFailSamples": [
      |            0.0,
      |            0.0,
      |            0.0
      |          ],
      |          "weightedSuccessSamples": [
      |            0.0,
      |            1.0,
      |            0.0
      |          ]
      |        },
      |        {
      |          "data": [
      |            0.0,
      |            0.0,
      |            0.3
      |          ],
      |          "failReasons": [
      |            {},
      |            {},
      |            {}
      |          ],
      |          "failSamples": [
      |            0,
      |            0,
      |            0
      |          ],
      |          "name": "metrics4",
      |          "successSamples": [
      |            0,
      |            0,
      |            1
      |          ],
      |          "weightedFailSamples": [
      |            0.0,
      |            0.0,
      |            0.0
      |          ],
      |          "weightedSuccessSamples": [
      |            0.0,
      |            0.0,
      |            1.0
      |          ]
      |        }
      |      ],
      |      "entryType": "DOUBLE_AVG",
      |      "failCount": 0,
      |      "labels": [
      |        {
      |          "p1": [
      |            "v1_1"
      |          ],
      |          "p2": [
      |            "v1_2"
      |          ]
      |        },
      |        {
      |          "p1": [
      |            "v2_1"
      |          ],
      |          "p2": [
      |            "v2_2"
      |          ]
      |        },
      |        {
      |          "p1": [
      |            "v3_1"
      |          ],
      |          "p3": [
      |            "v3_2"
      |          ]
      |        }
      |      ],
      |      "successCount": 1
      |    },
      |    {
      |      "datasets": [],
      |      "entryType": "NESTED_MAP_UNWEIGHTED_SUM_VALUE",
      |      "failCount": 0,
      |      "labels": [
      |        {
      |          "p1": [
      |            "v1_1"
      |          ],
      |          "p2": [
      |            "v1_2"
      |          ]
      |        },
      |        {
      |          "p1": [
      |            "v2_1"
      |          ],
      |          "p2": [
      |            "v2_2"
      |          ]
      |        },
      |        {
      |          "p1": [
      |            "v3_1"
      |          ],
      |          "p3": [
      |            "v3_2"
      |          ]
      |        }
      |      ],
      |      "successCount": 1
      |    }
      |  ],
      |  "name": "doc1",
      |  "timestamp": "$$timestampPlaceholder"
      |}
      |""".stripMargin.replaceAll("\\s+", "")

  val nestedMetricDocJson: String =
    """
      |{
      |  "data": [
      |    {
      |      "datasets": [],
      |      "entryType": "DOUBLE_AVG",
      |      "failCount": 0,
      |      "labels": [
      |        {
      |          "p1": [
      |            "v1_1"
      |          ],
      |          "p2": [
      |            "v1_2"
      |          ]
      |        }
      |      ],
      |      "successCount": 1
      |    },
      |    {
      |      "datasets": [
      |        {
      |          "data": [
      |            {
      |              "key1": {
      |                "1": 1.0,
      |                "2": 2.0
      |              },
      |              "key2": {
      |                "3": 1.0
      |              }
      |            }
      |          ],
      |          "failReasons": [
      |            {}
      |          ],
      |          "failSamples": [
      |            0
      |          ],
      |          "name": "histogram1",
      |          "successSamples": [
      |            1
      |          ],
      |          "weightedFailSamples": [
      |            0.0
      |          ],
      |          "weightedSuccessSamples": [
      |            1.0
      |          ]
      |        }
      |      ],
      |      "entryType": "NESTED_MAP_UNWEIGHTED_SUM_VALUE",
      |      "failCount": 0,
      |      "labels": [
      |        {
      |          "p1": [
      |            "v1_1"
      |          ],
      |          "p2": [
      |            "v1_2"
      |          ]
      |        }
      |      ],
      |      "successCount": 1
      |    }
      |  ],
      |  "name": "histogramDoc1",
      |  "timestamp": "$$timestampPlaceholder"
      |}
      |""".stripMargin.replaceAll("\\s+", "")

  def extractTimestampValueFromJsonDocument(doc: String): String = {
    val timestamp_regex: Regex =  new Regex(".*\"timestamp\":\"(.*)\"")
    val timestampPatternMatch = timestamp_regex.findFirstMatchIn(doc)
    timestampPatternMatch.get.group(1)
  }

  "JsonMetricDocumentFormat" should {

    "correctly write out json from MetricDocument with plain metrics" in {
      // given
      val format = new JsonMetricDocumentFormat(metricsNameAggregationTypeMapping)
      // when
      val jsonResult: String = format.metricDocumentToString(doc)
      // then
      val timestamp = extractTimestampValueFromJsonDocument(jsonResult)
      val expectedResult = plainMetricDocJson.replace("$$timestampPlaceholder", timestamp)
      jsonResult.stripMargin mustBe expectedResult
    }

    "correctly write out json from MetricDocument with nested metrics" in {
      // given
      val format = new JsonMetricDocumentFormat(metricsNameAggregationTypeMapping)
      // when
      val jsonResult = format.metricDocumentToString(histogramDoc1)
      // then
      val timestamp = extractTimestampValueFromJsonDocument(jsonResult)
      val expectedResult = nestedMetricDocJson.replace("$$timestampPlaceholder", timestamp)
      jsonResult.stripMargin mustBe expectedResult
    }

  }

}
