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


package de.awagen.kolibri.base.serialization

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.serialization.{SerializationExtension, Serializers}
import de.awagen.kolibri.base.actors.KolibriTypedTestKitNoCluster
import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.ValueSeqGenProviderFormat
import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol.queryAndParamProviderFormat
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueType.URL_PARAMETER
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.{MappedParameterValues, ParameterValueMapping, ValueSeqGenProvider}
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesSpec
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesSpec.{mappedValue1, mappedValue2}
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import spray.json._

object ConfigOverwrites {
  val configSettings = new java.util.HashMap[String, Any]()
  configSettings.put("akka.actor.serialize-messages", "on")
  configSettings.put("akka.actor.serialize-creators", "on")
}

class SerializationSpec extends KolibriTypedTestKitNoCluster(ConfigOverwrites.configSettings) {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  def serializeAndBack(original: AnyRef): AnyRef = {
    // Get the Serialization Extension
    val serialization = SerializationExtension(system)
    // Turn it into bytes, and retrieve the serializerId and manifest, which are needed for deserialization
    val bytes = serialization.serialize(original).get
    val serializerId = serialization.findSerializerFor(original).identifier
    val manifest = Serializers.manifestFor(serialization.findSerializerFor(original), original)
    // Turn it back into an object
    serialization.deserialize(bytes, serializerId, manifest).get
  }

  object Samples {

    val mappingSample: String =
      """
         |{
         |      "type": "MAPPING",
         |      "values": {
         |        "key_values": {
         |          "type": "FROM_ORDERED_VALUES_TYPE",
         |          "values": {
         |            "type": "FROM_FILENAME_KEYS_TYPE",
         |            "directory": "data/fileMappingSingleValueTest",
         |            "filesSuffix": ".txt",
         |            "name": "keyId"
         |          },
         |          "values_type": "URL_PARAMETER"
         |        },
         |        "mapped_values": [
         |          {
         |            "type": "CSV_MAPPING_TYPE",
         |            "name": "mapped_id",
         |            "values_type": "URL_PARAMETER",
         |            "values": "data/csvMappedParameterTest/mapping1.csv",
         |            "column_delimiter": ",",
         |            "key_column_index": 0,
         |            "value_column_index": 1
         |          },
         |          {
         |            "type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
         |            "directory": "data/fileMappingSingleValueTest",
         |            "files_suffix": ".txt",
         |            "name": "value",
         |            "values_type": "URL_PARAMETER"
         |          }
         |        ],
         |        "key_mapping_assignments": [
         |          [
         |            0,
         |            1
         |          ],
         |          [
         |            0,
         |            2
         |          ]
         |        ]
         |      }
         |    }
         |""".stripMargin

    val jobSample: String =
      """
        |{
        |  "jobName": "jobName1",
        |  "requestTasks": 2,
        |  "fixedParams": {
        |    "lang": ["en"],
        |    "start": ["0"],
        |    "rows": ["100"]
        |  },
        |  "contextPath": "query/search_path",
        |  "connections": [
        |    {
        |      "host": "searchapi1",
        |      "port": 443,
        |      "useHttps": true
        |    },
        |    {
        |      "host": "searchapi2",
        |      "port": 443,
        |      "useHttps": true
        |    }
        |  ],
        |  "requestParameterPermutateSeq": [
{
        |      "type": "MAPPING",
        |      "values": {
        |        "key_values": {
        |          "type": "FROM_ORDERED_VALUES_TYPE",
        |          "values": {
        |            "type": "FROM_FILENAME_KEYS_TYPE",
        |            "directory": "data/fileMappingSingleValueTest",
        |            "filesSuffix": ".txt",
        |            "name": "keyId"
        |          },
        |          "values_type": "URL_PARAMETER"
        |        },
        |        "mapped_values": [
        |          {
        |            "type": "CSV_MAPPING_TYPE",
        |            "name": "mapped_id",
        |            "values_type": "URL_PARAMETER",
        |            "values": "data/csvMappedParameterTest/mapping1.csv",
        |            "column_delimiter": ",",
        |            "key_column_index": 0,
        |            "value_column_index": 1
        |          },
        |          {
        |            "type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
        |            "directory": "data/fileMappingSingleValueTest",
        |            "files_suffix": ".txt",
        |            "name": "value",
        |            "values_type": "URL_PARAMETER"
        |          }
        |        ],
        |        "key_mapping_assignments": [
        |          [
        |            0,
        |            1
        |          ],
        |          [
        |            0,
        |            2
        |          ]
        |        ]
        |      }
        |    },
        |    {
        |      "type": "STANDALONE",
        |      "values": {
        |        "type": "FROM_ORDERED_VALUES_TYPE",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_VALUES_TYPE",
        |          "name": "seomValuesParam1",
        |          "values": [
        |            "subCommodityCode:0.2"
        |          ]
        |        }
        |      }
        |    },
        |    {
        |      "type": "STANDALONE",
        |      "values": {
        |        "type": "FROM_ORDERED_VALUES_TYPE",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_VALUES_TYPE",
        |          "name": "someValuesParam2",
        |          "values": [
        |            "3.0"
        |          ]
        |        }
        |      }
        |    },
        |    {
        |      "type": "STANDALONE",
        |      "values": {
        |        "type": "FROM_ORDERED_VALUES_TYPE",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_VALUES_TYPE",
        |          "name": "someRangeParam1",
        |          "values": [
        |            "0.0",
        |            "2.0",
        |            "4.0"
        |          ]
        |        }
        |      }
        |    },
        |    {
        |      "type": "STANDALONE",
        |      "values": {
        |        "type": "FROM_ORDERED_VALUES_TYPE",
        |        "values_type": "HEADER",
        |        "values": {
        |          "type": "FROM_VALUES_TYPE",
        |          "name": "header-name",
        |          "values": ["header-value"]
        |        }
        |      }
        |    }
        |  ],
        |  "batchByIndex": 0,
        |  "parsingConfig": {
        |    "selectors": [
        |      {
        |        "name": "productIds",
        |        "castType": "STRING",
        |        "selector": "\\ data \\ products \\\\ productId"
        |      },
        |      {
        |        "name": "numFound",
        |        "castType": "DOUBLE",
        |        "selector": "\\ data \\ numFound"
        |      }
        |    ]
        |  },
        |  "excludeParamsFromMetricRow": [
        |      "query",
        |      "lang",
        |      "start",
        |      "rows"
        |  ],
        |  "taggingConfiguration": {
        |    "initTagger": {
        |      "type": "REQUEST_PARAMETER",
        |      "parameter": "query",
        |      "extend": false
        |    },
        |    "processedTagger": {
        |      "type": "NOTHING"
        |    },
        |    "resultTagger": {
        |      "type": "NOTHING"
        |    }
        |  },
        |  "requestTemplateStorageKey": "requestTemplate",
        |  "mapFutureMetricRowCalculation": {
        |    "functionType": "IR_METRICS",
        |    "name": "irMetrics",
        |    "queryParamName": "query",
        |    "requestTemplateKey": "requestTemplate",
        |    "productIdsKey": "productIds",
        |    "judgementProvider": {
        |      "type": "FILE_BASED",
        |      "filename": "data/json_lines_judgements.json"
        |    },
        |    "metricsCalculation": {
        |      "metrics": [],
        |      "judgementHandling": {
        |        "validations": [
        |          "EXIST_RESULTS",
        |          "EXIST_JUDGEMENTS"
        |        ],
        |        "handling": "AS_ZEROS"
        |      }
        |    },
        |    "excludeParams": [
        |      "query",
        |      "lang",
        |      "start",
        |      "rows"
        |    ]
        |  },
        |  "singleMapCalculations": [
        |    {
        |      "name": "NUM_FOUND",
        |      "dataKey": "numFound",
        |      "type": "IDENTITY"
        |    }
        |  ],
        |  "allowedTimePerElementInMillis": 1000,
        |  "allowedTimePerBatchInSeconds": 6000,
        |  "allowedTimeForJobInSeconds": 720000,
        |  "expectResultsFromBatchCalculations": false,
        |  "wrapUpFunction": {
        |    "type": "AGGREGATE_FROM_DIR_BY_REGEX",
        |    "weightProvider": {
        |      "type": "CONSTANT",
        |      "weight": 1.0
        |    },
        |    "regex": ".*[(]query=.+[)].*",
        |    "outputFilename": "(ALL1)",
        |    "readSubDir": "test-results/jobName1",
        |    "writeSubDir": "test-results/jobName1"
        |  }
        |}
        |""".stripMargin

  }

  object Actors {
    object MirrorActor {
      case class Sent(obj: AnyRef, self: ActorRef[Received]) extends KolibriSerializable
      case class Received(obj: AnyRef) extends KolibriSerializable

      def apply(): Behavior[Sent] = Behaviors.receiveMessage {
        case Sent(greeting, recipient) =>
          recipient ! Received(greeting)
          Behaviors.same
      }
    }
  }

  "Serialization" must {

    "work properly on ParameterValueMappings" in {
      // given
      val original = new ParameterValueMapping(keyValues = ParameterValuesSpec.parameterValues, mappedValues = Seq(mappedValue1, mappedValue2),
        mappingKeyValueAssignments = Seq((1, 2)))
      // when, then
      serializeAndBack(original)
    }

    "MappedParameterValues should be serializable" in {
      // given
      val mappings: Map[String, Seq[String]] = Map(
        "a" -> Seq("a1", "a2")
      )
      val values = MappedParameterValues("testName", URL_PARAMETER, mappings.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2))))
      // when, then
      serializeAndBack(values)
    }

    "ByFunctionNrLimitedIndexedGenerator should be serializable" in {
      // given
      val generator = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("a", "b"))
      // when, then
      serializeAndBack(generator)
    }

    "parsed mapping sample must be serializable" in {
      // given
      val parsed = Samples.mappingSample.parseJson.convertTo[ValueSeqGenProvider]
      // when, then
      serializeAndBack(parsed)
    }

    "serialization across actors" in {
      // given
      val parsed: ValueSeqGenProvider = Samples.mappingSample.parseJson.convertTo[ValueSeqGenProvider]
      val castParsed = parsed.asInstanceOf[ParameterValueMapping]
      val senderActor: ActorRef[Actors.MirrorActor.Sent] = testKit.spawn(Actors.MirrorActor(), "mirror")
      val testProbe = testKit.createTestProbe[Actors.MirrorActor.Received]()
      senderActor ! Actors.MirrorActor.Sent(parsed, testProbe.ref)
      val msg: Actors.MirrorActor.Received =  testProbe.expectMessageType[Actors.MirrorActor.Received]
      val value = msg.obj.asInstanceOf[ParameterValueMapping]
      value.keyValues.name mustBe castParsed.keyValues.name
      value.keyValues.values.size mustBe castParsed.keyValues.size
      value.keyValues.valueType mustBe castParsed.keyValues.valueType
      value.mappingKeyValueAssignments mustBe castParsed.mappingKeyValueAssignments
      value.mappedValues.size mustBe castParsed.mappedValues.size
    }

    "serialization of job message across actors" in {
      val parsed: SearchEvaluation = Samples.jobSample.parseJson.convertTo[SearchEvaluation]
      val senderActor: ActorRef[Actors.MirrorActor.Sent] = testKit.spawn(Actors.MirrorActor(), "mirror")
      val testProbe = testKit.createTestProbe[Actors.MirrorActor.Received]()
      senderActor ! Actors.MirrorActor.Sent(parsed, testProbe.ref)
      val msg: Actors.MirrorActor.Received =  testProbe.expectMessageType[Actors.MirrorActor.Received]
      val value = msg.obj.asInstanceOf[SearchEvaluation]
      value.jobName mustBe parsed.jobName
    }

  }

}
