/**
  * Copyright 2021 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.akka.io.json

import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestPermutations.MappingModifier
import de.awagen.kolibri.fleet.akka.io.json.ModifierGeneratorProviderJsonProtocol._
import de.awagen.kolibri.fleet.akka.testclasses.UnitTestSpec
import spray.json._

class ModifierGeneratorProviderJsonProtocolSpec extends UnitTestSpec {

  // here we get one key that has a match in all mappings and one that only occurs in one,
  // resulting in 10 overall permutations
  val mappingModifierJson: JsValue =
  """
    |{
    |"keys": {"type": "BY_VALUES_SEQ", "values": ["key1", "key2"]},
    |"paramsMapper": {
    | "replace": true,
    | "values": {
    |   "type": "FROM_JSON_MAP",
    |   "value": {
    |     "key1": {
    |         "test1": {"type": "BY_VALUES_SEQ", "values": [["0.10", "0.11"]]},
    |         "test2": {"type": "BY_VALUES_SEQ", "values": [["0.21", "0.22"]]}
    |     }
    |   }
    | }
    |},
    |"headerMapper": {
    | "replace": true,
    | "values": {
    |   "type": "FROM_JSON_MAP",
    |   "value": {
    |     "key1": {
    |       "hname1": {
    |         "type": "BY_VALUES_SEQ",
    |         "values": ["value1", "value3"]
    |       },
    |       "hname2": {
    |         "type": "BY_VALUES_SEQ",
    |         "values": ["value2", "value4"]
    |       }
    |     }
    |   }
    | }
    |},
    |"bodyMapper": {
    | "values": {
    |   "key1": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]},
    |   "key2": {"type": "BY_VALUES_SEQ", "values": ["val3", "val4"]}
    | }
    |}
    |}
    |""".stripMargin.parseJson

  // here the keys dont match any mapping, thus we dont expect any modifier to come out of it
  // when parsing to MappingModifier
  val mappingModifierWithNonMatchingKeysJson: JsValue =
  """
    |{
    |"keys": {"type": "BY_VALUES_SEQ", "values": ["nonmatching1", "nonmatching2"]},
    |"paramsMapper": {
    | "replace": true,
    | "values": {
    |   "type": "FROM_JSON_MAP",
    |   "value": {
    |     "key1": {
    |         "test1": {"type": "BY_VALUES_SEQ", "values": [["0.10", "0.11"]]},
    |         "test2": {"type": "BY_VALUES_SEQ", "values": [["0.21", "0.22"]]}
    |     }
    |   }
    | }
    |},
    |"headerMapper": {
    | "replace": true,
    | "values": {
    |   "type": "FROM_JSON_MAP",
    |   "value": {
    |     "key1": {
    |       "hname1": {
    |         "type": "BY_VALUES_SEQ",
    |         "values": ["value1", "value3"]
    |       },
    |       "hname2": {
    |         "type": "BY_VALUES_SEQ",
    |         "values": ["value2", "value4"]
    |       }
    |     }
    |   }
    | }
    |},
    |"bodyMapper": {
    | "values": {
    |   "key1": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]},
    |   "key2": {"type": "BY_VALUES_SEQ", "values": ["val3", "val4"]}
    | }
    |}
    |}
    |""".stripMargin.parseJson

  val onlyKeysMappingModifierJson: JsValue =
    """{"keys": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]}}""".stripMargin.parseJson

  "correctly parse MappingModifier" in {
    val mappingModifier = mappingModifierJson.convertTo[MappingModifier]
    mappingModifier.modifiers.size mustBe 1
    val modifiers: Seq[Modifier[RequestTemplateBuilder]] = mappingModifier.modifiers.head.iterator.toSeq
    modifiers.size mustBe 10
  }

  "correctly parse MappingModifier with non-matching keys to empty modifier" in {
    val mappingModifier = mappingModifierWithNonMatchingKeysJson.convertTo[MappingModifier]
    mappingModifier.modifiers.size mustBe 1
    val modifiers: Seq[Modifier[RequestTemplateBuilder]] = mappingModifier.modifiers.head.iterator.toSeq
    modifiers.size mustBe 0
  }

}
