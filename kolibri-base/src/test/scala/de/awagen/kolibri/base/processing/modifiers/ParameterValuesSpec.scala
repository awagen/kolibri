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


package de.awagen.kolibri.base.processing.modifiers

import de.awagen.kolibri.base.processing.modifiers.ParameterValues.{ParameterValue, ParameterValueMapping, ValueType}
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.MappedParameterValues
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueType.URL_PARAMETER
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator

class ParameterValuesSpec extends UnitTestSpec {

  val parameterValues: ParameterValues.ParameterValues = ParameterValues.ParameterValues("p1", ValueType.URL_PARAMETER,
    ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("v1", "v2", "v3", "v4")))

  val mappedValue1: MappedParameterValues = MappedParameterValues(
    name = "mp1",
    valueType = ValueType.URL_PARAMETER,
    values = Map(
      "v1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("mv1_1", "mv1_2", "mv1_3")),
      "v2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("mv2_1")),
      "v4" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("mv4_1"))
    )
  )

  val mappedValue2: MappedParameterValues = MappedParameterValues(
    name = "mp2",
    valueType = ValueType.URL_PARAMETER,
    values = Map(
      "mv1_1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("mv11_1")),
      "mv1_2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("mv12_1")),
      "mv2_1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("mv22_1"))
    )
  )

  "ParameterValues" must {

    "correctly generate values" in {
      parameterValues.iterator.toSeq mustBe Seq(
        ParameterValue("p1", ValueType.URL_PARAMETER, "v1"),
        ParameterValue("p1", ValueType.URL_PARAMETER, "v2"),
        ParameterValue("p1", ValueType.URL_PARAMETER, "v3"))
    }
  }

  "ParameterValueMapping" must {
      "correctly represent mappings" in {
        // given
        val mapping = new ParameterValueMapping(values = parameterValues, mappedValues = Seq(mappedValue1, mappedValue2),
          mappingKeyValueAssignments = Seq((1,2))
        )
        // when
        val allCombinations = mapping.iterator.toSeq
        // then
        mapping.nrOfElements mustBe 3
        allCombinations mustBe Seq(
          Seq(
            ParameterValue("p1", URL_PARAMETER, "v1"),
            ParameterValue("mp1", URL_PARAMETER, "mv1_1"),
            ParameterValue("mp2", URL_PARAMETER, "mv11_1")
          ),
          Seq(
            ParameterValue("p1", URL_PARAMETER, "v1"),
            ParameterValue("mp1", URL_PARAMETER, "mv1_2"),
            ParameterValue("mp2", URL_PARAMETER, "mv12_1")
          ),
          Seq(
            ParameterValue("p1", URL_PARAMETER, "v2"),
            ParameterValue("mp1", URL_PARAMETER, "mv2_1"),
            ParameterValue("mp2", URL_PARAMETER, "mv22_1")
          )
        )
      }

  }

}
