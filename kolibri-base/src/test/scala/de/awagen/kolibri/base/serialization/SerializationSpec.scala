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
import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.ValueSeqGenDefinitionFormat
import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol.queryAndParamProviderFormat
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueType.URL_PARAMETER
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.{MappedParameterValues, ParameterValueMappingDefinition, ValueSeqGenDefinition}
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesDefinitionSpec
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesDefinitionSpec.{mappedValue1, mappedValue2}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
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
        |          "name": "keyId",
        |          "values_type": "URL_PARAMETER",
        |          "values": {
        |            "type": "FROM_ORDERED_VALUES_TYPE",
        |            "values": {
        |              "type": "FROM_FILENAME_KEYS_TYPE",
        |              "directory": "data/fileMappingSingleValueTest",
        |              "filesSuffix": ".txt",
        |              "name": "keyId"
        |            }
        |          }
        |        },
        |        "mapped_values": [
        |          {
        |            "name": "mapped_id",
        |            "values_type": "URL_PARAMETER",
        |            "values": {
        |              "type": "CSV_MAPPING_TYPE",
        |              "values": "data/csvMappedParameterTest/mapping1.csv",
        |              "column_delimiter": ",",
        |              "key_column_index": 0,
        |              "value_column_index": 1
        |            }
        |          },
        |          {
        |            "name": "value",
        |            "values_type": "URL_PARAMETER",
        |            "values": {
        |              "type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
        |              "directory": "data/fileMappingSingleValueTest",
        |              "files_suffix": ".txt"
        |            }
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
        |  "resourceDirectives": [
        |    {
        |      "type": "MAP_STRING_TO_DOUBLE_VALUE",
        |      "values": {
        |        "resource": {
        |          "resourceType": "MAP_STRING_TO_DOUBLE_VALUE",
        |          "identifier": "ident1"
        |        },
        |        "supplier": {
        |          "type": "JUDGEMENTS_FROM_FILE",
        |          "file": "test-judgements/test_judgements.txt"
        |        }
        |     }
        |    }
        |  ],
        |  "requestParameterPermutateSeq": [
              {
        |      "type": "MAPPING",
        |            "values": {
        |        "key_values": {
        |          "name": "keyId",
        |          "values_type": "URL_PARAMETER",
        |          "values": {
        |            "type": "FROM_ORDERED_VALUES_TYPE",
        |            "values": {
        |              "type": "FROM_FILENAME_KEYS_TYPE",
        |              "directory": "data/fileMappingSingleValueTest",
        |              "filesSuffix": ".txt",
        |              "name": "keyId"
        |            }
        |          }
        |        },
        |        "mapped_values": [
        |          {
        |            "name": "mapped_id",
        |            "values_type": "URL_PARAMETER",
        |            "values": {
        |              "type": "CSV_MAPPING_TYPE",
        |              "values": "data/csvMappedParameterTest/mapping1.csv",
        |              "column_delimiter": ",",
        |              "key_column_index": 0,
        |              "value_column_index": 1
        |            }
        |          },
        |          {
        |            "name": "value",
        |            "values_type": "URL_PARAMETER",
        |            "values": {
        |              "type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
        |              "directory": "data/fileMappingSingleValueTest",
        |              "files_suffix": ".txt"
        |            }
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
        |        "name": "seomValuesParam1",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_ORDERED_VALUES_TYPE",
        |          "values": {
        |            "type": "FROM_VALUES_TYPE",
        |            "name": "someValuesParam1",
        |            "values": [
        |              "subValue1:0.2"
        |            ]
        |          }
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

    val otherJobSample =
      """
        |{
        |  "jobName": "testJob1",
        |  "requestTasks": 5,
        |  "fixedParams": {
        |    "k1": [
        |      "v1",
        |      "v2"
        |    ],
        |    "k2": [
        |      "v3"
        |    ]
        |  },
        |  "contextPath": "search",
        |  "connections": [
        |    {
        |      "host": "search-service",
        |      "port": 80,
        |      "useHttps": false
        |    },
        |    {
        |      "host": "search-service1",
        |      "port": 81,
        |      "useHttps": false
        |    }
        |  ],
        |  "resourceDirectives": [
        |    {
        |      "type": "MAP_STRING_TO_DOUBLE_VALUE",
        |      "values": {
        |        "resource": {
        |          "resourceType": "MAP_STRING_TO_DOUBLE_VALUE",
        |          "identifier": "ident1"
        |        },
        |        "supplier": {
        |          "type": "JUDGEMENTS_FROM_FILE",
        |          "file": "test-judgements/test_judgements.txt"
        |        }
        |      }
        |    },
        |    {
        |      "type": "MAP_STRING_TO_STRING_VALUES",
        |      "values": {
        |        "resource": {
        |          "resourceType": "MAP_STRING_TO_STRING_VALUES",
        |          "identifier": "prefixToFilesLines1"
        |        },
        |        "supplier": {
        |          "type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
        |          "directory": "data/fileMappingSingleValueTest",
        |          "files_suffix": ".txt"
        |        }
        |      }
        |    }
        |  ],
        |  "requestParameterPermutateSeq": [
        |    {
        |      "type": "MAPPING",
        |      "values": {
        |        "key_values": {
        |          "name": "keyId",
        |          "values_type": "URL_PARAMETER",
        |          "values": {
        |            "type": "FROM_ORDERED_VALUES_TYPE",
        |            "values": {
        |              "type": "FROM_FILENAME_KEYS_TYPE",
        |              "directory": "data/fileMappingSingleValueTest",
        |              "filesSuffix": ".txt",
        |              "name": "keyId"
        |            }
        |          }
        |        },
        |        "mapped_values": [
        |          {
        |            "name": "mapped_id",
        |            "values_type": "URL_PARAMETER",
        |            "values": {
        |              "type": "CSV_MAPPING_TYPE",
        |              "values": "data/csvMappedParameterTest/mapping1.csv",
        |              "column_delimiter": ",",
        |              "key_column_index": 0,
        |              "value_column_index": 1
        |            }
        |          },
        |          {
        |            "name": "value",
        |            "values_type": "URL_PARAMETER",
        |            "values": {
        |              "type": "VALUES_FROM_NODE_STORAGE",
        |              "identifier": "prefixToFilesLines1"
        |            }
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
        |        "name": "q",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_ORDERED_VALUES_TYPE",
        |          "values": {
        |            "type": "FROM_FILES_LINES_TYPE",
        |            "name": "q",
        |            "file": "test-paramfiles/test_queries.txt"
        |          }
        |        }
        |      }
        |    },
        |    {
        |      "type": "STANDALONE",
        |      "values": {
        |        "name": "a1",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_ORDERED_VALUES_TYPE",
        |          "values": {
        |            "type": "FROM_VALUES_TYPE",
        |            "name": "a1",
        |            "values": [
        |              "0.45",
        |              "0.32"
        |            ]
        |          }
        |        }
        |      }
        |    },
        |    {
        |      "type": "STANDALONE",
        |      "values": {
        |        "name": "o",
        |        "values_type": "URL_PARAMETER",
        |        "values": {
        |          "type": "FROM_ORDERED_VALUES_TYPE",
        |          "values": {
        |            "type": "FROM_RANGE_TYPE",
        |            "name": "o",
        |            "start": 0.0,
        |            "end": 2000.0,
        |            "stepSize": 1.0
        |          }
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
        |        "selector": "\\ response \\ docs \\\\ product_id"
        |      },
        |      {
        |        "name": "bools",
        |        "castType": "BOOLEAN",
        |        "selector": "\\ response \\ docs \\\\ bool"
        |      }
        |    ]
        |  },
        |  "excludeParamsFromMetricRow": [
        |    "q"
        |  ],
        |  "taggingConfiguration": {
        |    "initTagger": {
        |      "type": "REQUEST_PARAMETER",
        |      "parameter": "q",
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
        |    "queryParamName": "q",
        |    "requestTemplateKey": "requestTemplate",
        |    "productIdsKey": "productIds",
        |    "judgementProvider": {
        |      "type": "FILE_BASED",
        |      "filename": "test-judgements/test_judgements.txt"
        |    },
        |    "metricsCalculation": {
        |      "metrics": [
        |        {"name": "DCG_10", "function": {"type": "DCG", "k": 10}},
        |        {"name": "NDCG_10", "function": {"type": "NDCG", "k": 10}},
        |        {"name": "PRECISION_4", "function": {"type": "PRECISION", "k": 4, "threshold":  0.1}},
        |        {"name": "ERR_10", "function": {"type": "ERR", "k": 10}}
        |      ],
        |      "judgementHandling": {
        |        "validations": [
        |          "EXIST_RESULTS",
        |          "EXIST_JUDGEMENTS"
        |        ],
        |        "handling": "AS_ZEROS"
        |      }
        |    },
        |    "excludeParams": [
        |      "q"
        |    ]
        |  },
        |  "singleMapCalculations": [
        |    {
        |      "name": "FIRST_TRUE_BOOL",
        |      "dataKey": "bools",
        |      "type": "FIRST_TRUE"
        |    },
        |    {
        |      "name": "COUNT_TRUE_BOOL",
        |      "dataKey": "bools",
        |      "type": "TRUE_COUNT"
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
        |    "regex": ".*[(]q=.+[)]-.*",
        |    "outputFilename": "(ALL1)",
        |    "readSubDir": "test-results/testJob1",
        |    "writeSubDir": "test-results/testJob1"
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
      val original = new ParameterValueMappingDefinition(keyValues = ParameterValuesDefinitionSpec.parameterValues, mappedValues = Seq(mappedValue1, mappedValue2),
        mappingKeyValueAssignments = Seq((1, 2)))
      // when, then
      serializeAndBack(original)
    }

    "MappedParameterValues should be serializable" in {
      // given
      val mappings: Map[String, Seq[String]] = Map(
        "a" -> Seq("a1", "a2")
      )
      val values = MappedParameterValues(
        "testName",
        URL_PARAMETER,
        new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
          override def apply(): Map[String, IndexedGenerator[String]] = {
            mappings.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
          }
        })
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
      val parsed = Samples.mappingSample.parseJson.convertTo[ValueSeqGenDefinition[_]]
      // when, then
      serializeAndBack(parsed)
    }

    "serialization across actors" in {
      // given
      val parsed: ValueSeqGenDefinition[_] = Samples.mappingSample.parseJson.convertTo[ValueSeqGenDefinition[_]]
      val castParsed = parsed.asInstanceOf[ParameterValueMappingDefinition]
      val senderActor: ActorRef[Actors.MirrorActor.Sent] = testKit.spawn(Actors.MirrorActor(), "mirror")
      val testProbe = testKit.createTestProbe[Actors.MirrorActor.Received]()
      senderActor ! Actors.MirrorActor.Sent(parsed, testProbe.ref)
      val msg: Actors.MirrorActor.Received = testProbe.expectMessageType[Actors.MirrorActor.Received]
      val value = msg.obj.asInstanceOf[ParameterValueMappingDefinition]
      value.keyValues.name mustBe castParsed.keyValues.name
      value.keyValues.values.apply().size mustBe castParsed.keyValues.toState.size
      value.keyValues.valueType mustBe castParsed.keyValues.valueType
      value.mappingKeyValueAssignments mustBe castParsed.mappingKeyValueAssignments
      value.mappedValues.size mustBe castParsed.mappedValues.size
    }

    "serialization of job message across actors" in {
      val parsed: SearchEvaluation = Samples.jobSample.parseJson.convertTo[SearchEvaluation]
      val senderActor: ActorRef[Actors.MirrorActor.Sent] = testKit.spawn(Actors.MirrorActor(), "mirror1")
      val testProbe = testKit.createTestProbe[Actors.MirrorActor.Received]()
      senderActor ! Actors.MirrorActor.Sent(parsed, testProbe.ref)
      val msg: Actors.MirrorActor.Received = testProbe.expectMessageType[Actors.MirrorActor.Received]
      val value = msg.obj.asInstanceOf[SearchEvaluation]
      value.jobName mustBe parsed.jobName
    }

    "serialize job message with file loads and node storage retrieval" in {
      Samples.otherJobSample.parseJson.convertTo[SearchEvaluation]
    }

  }

}
