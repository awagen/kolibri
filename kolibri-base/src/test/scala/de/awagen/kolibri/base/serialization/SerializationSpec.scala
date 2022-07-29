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

import akka.serialization.{SerializationExtension, Serializers}
import akka.testkit.TestKit
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.ValueSeqGenProviderFormat
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueType.URL_PARAMETER
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.{MappedParameterValues, ParameterValueMapping, ValueSeqGenProvider}
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesSpec
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesSpec.{mappedValue1, mappedValue2}
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

class SerializationSpec extends KolibriTestKitNoCluster
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

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

  }

}
