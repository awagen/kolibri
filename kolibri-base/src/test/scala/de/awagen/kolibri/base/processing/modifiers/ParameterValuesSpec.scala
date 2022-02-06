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

  // key values from v1 to v999
  val range1000keyValues: ParameterValues.ParameterValues = ParameterValues.ParameterValues("p1", ValueType.URL_PARAMETER,
    ByFunctionNrLimitedIndexedGenerator.createFromSeq(Range(0, 1000).map(x => s"v$x")))
  // mappings for key values v1 to v999 for parameter mp1 to values mv1_1 to mv1_999 (1:1 mapping)
  val range1000MappedValues1: MappedParameterValues = MappedParameterValues(
    name = "mp1",
    valueType = ValueType.URL_PARAMETER,
    values = Range(0, 1000).map(x => (s"v$x", ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(s"mv1_$x")))).toMap
  )
  // mappings for key values mp1 to mp2 to values mv2_1 to mv2_499)
  val range1000OnlyFirst500MappedValues2: MappedParameterValues = MappedParameterValues(
    name = "mp2",
    valueType = ValueType.URL_PARAMETER,
    values = Range(0, 500).map(x => (s"mv1_$x", ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(s"mv2_$x")))).toMap
  )

  val range1000MappedValuesOnlyFirst500MultipleMappedValues2: MappedParameterValues = MappedParameterValues(
    name = "mp2",
    valueType = ValueType.URL_PARAMETER,
    values = Range(0, 500).map(x => (s"mv1_$x", ByFunctionNrLimitedIndexedGenerator.createFromSeq(
      Range(0, 100).map(y => s"mv2${x}_$y")
    ))
    ).toMap
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
        mappingKeyValueAssignments = Seq((1, 2))
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

    "correctly represent larger mappings and ignore incomplete" in {
      // given
      val mapping = new ParameterValueMapping(values = range1000keyValues, mappedValues = Seq(range1000MappedValues1,
        range1000OnlyFirst500MappedValues2),
        mappingKeyValueAssignments = Seq((1, 2))
      )
      // then
      mapping.nrOfElements mustBe 500
      Range(0, 500).foreach(index => {
        mapping.get(index).get mustBe Seq(
          ParameterValue("p1", URL_PARAMETER, s"v$index"),
          ParameterValue("mp1", URL_PARAMETER, s"mv1_$index"),
          ParameterValue("mp2", URL_PARAMETER, s"mv2_$index")
        )
      })
    }

    "correctly explode mappings for multiple mapped values" in {
      // given
      val mapping = new ParameterValueMapping(values = range1000keyValues, mappedValues = Seq(range1000MappedValues1,
        range1000MappedValuesOnlyFirst500MultipleMappedValues2),
        mappingKeyValueAssignments = Seq((1, 2))
      )
      // then
      mapping.nrOfElements mustBe 500 * 100
      Range(0, 500).foreach(index => {
        Range(0, 100).foreach(index2 => {
          val generatedIndex = index2 + index * 100
          mapping.get(generatedIndex).get mustBe Seq(
            ParameterValue("p1", URL_PARAMETER, s"v$index"),
            ParameterValue("mp1", URL_PARAMETER, s"mv1_$index"),
            ParameterValue("mp2", URL_PARAMETER, s"mv2${index}_$index2")
          )
        })
      })
    }

  }

}
