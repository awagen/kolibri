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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts

import de.awagen.kolibri.base.domain.jobdefinitions.Batch
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.{MappedParameterValues, ParameterValueMapping, ParameterValues, ValueSeqGenProvider, ValueType}
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{CombinedModifier, RequestParameterModifier}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}

class BatchGeneratorsSpec extends UnitTestSpec {

  object Modifiers {

    val requestParameterModifier1: RequestParameterModifier = RequestParameterModifier(Map("q" -> Seq("q1")), replace = false)
    val requestParameterModifier2: RequestParameterModifier = RequestParameterModifier(Map("k" -> Seq("k1")), replace = false)
    val requestParameterModifier3: RequestParameterModifier = RequestParameterModifier(Map("l" -> Seq("l1")), replace = false)
    val requestParameterModifier4: RequestParameterModifier = RequestParameterModifier(Map("m" -> Seq("m1")), replace = false)
    val requestParameterModifier5: RequestParameterModifier = RequestParameterModifier(Map("n" -> Seq("n1")), replace = false)
  }

  object ParamValues {
    val distinctValues1: ParameterValues = ParameterValues(
      "q",
      ValueType.URL_PARAMETER,
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("key1", "key2", "key3"))
    )

    val distinctValues2: ParameterValues = ParameterValues(
      "oo",
      ValueType.URL_PARAMETER,
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("val1", "val2", "val3"))
    )

    val mappedValues1: MappedParameterValues = MappedParameterValues("mappedParam1", ValueType.URL_PARAMETER,
      Map(
        "key1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("key1_val1", "key1_val2")),
        "key2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("key2_val1", "key2_val2"))
      ))
    val mapping1 = new ParameterValueMapping(keyValues = distinctValues1, mappedValues = Seq(mappedValues1), mappingKeyValueAssignments = Seq((0,1)))


  }

  object Generators {
    val url_param_1: IndexedGenerator[RequestParameterModifier] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
      Seq(Modifiers.requestParameterModifier1, Modifiers.requestParameterModifier2)
    )
    val url_param_2: IndexedGenerator[RequestParameterModifier] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
      Seq(Modifiers.requestParameterModifier3, Modifiers.requestParameterModifier4, Modifiers.requestParameterModifier5)
    )
  }

  "BatchGenerators" must {

    "correctly apply batchByGeneratorAtIndex" in {
      // given
      val batchByFirst = BatchGenerators.batchByGeneratorAtIndex(0)
      val batchBySecond = BatchGenerators.batchByGeneratorAtIndex(1)
      // when
      val batchedByFirst: IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]] = batchByFirst.apply(Seq(Generators.url_param_1, Generators.url_param_2))
      val batchedBySecond: IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]] = batchBySecond.apply(Seq(Generators.url_param_1, Generators.url_param_2))
      // then
      batchedByFirst.nrOfElements mustBe 2
      batchedBySecond.nrOfElements mustBe 3
      // batches when batched by first
      batchedByFirst.get(0).get.data.iterator.toSeq mustBe Generators.url_param_2.iterator.toSeq.map(x => CombinedModifier(Seq(
        Modifiers.requestParameterModifier1,
        x)))
      batchedByFirst.get(1).get.data.iterator.toSeq mustBe Generators.url_param_2.iterator.toSeq.map(x => CombinedModifier(Seq(
        Modifiers.requestParameterModifier2,
        x)))
      // batches when batched by second
      batchedBySecond.get(0).get.data.iterator.toSeq mustBe Generators.url_param_1.iterator.toSeq.map(x => CombinedModifier(Seq(
        Modifiers.requestParameterModifier3,
        x)))
      batchedBySecond.get(1).get.data.iterator.toSeq mustBe Generators.url_param_1.iterator.toSeq.map(x => CombinedModifier(Seq(
        Modifiers.requestParameterModifier4,
        x)))
      batchedBySecond.get(2).get.data.iterator.toSeq mustBe Generators.url_param_1.iterator.toSeq.map(x => CombinedModifier(Seq(
        Modifiers.requestParameterModifier5,
        x)))
    }

    "correctly apply batchByGeneratorAtIndex for mapping generator" in {
      import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ParameterValuesImplicits.ParameterValueSeqToRequestBuilderModifier
      import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ParameterValuesImplicits.ParameterValueToRequestBuilderModifier
      // given
      val batchByFirst = BatchGenerators.batchByGeneratorAtIndex(0)
      val batchBySecond = BatchGenerators.batchByGeneratorAtIndex(1)
      val seq = Seq(ParamValues.mapping1, ParamValues.distinctValues2)
        .map(x => x.toSeqGenerator).map(x => x.mapGen(y => y.toModifier))
      // when
      val batchesByFirst = batchByFirst.apply(seq)
      val batchesBySecond = batchBySecond.apply(seq)
      // then
      batchesByFirst.nrOfElements mustBe 2
      batchesBySecond.nrOfElements mustBe 3
      // NOTE: if we batch by a mapping initially, the batching itself will be per key value,
      // yet it will iterate over all other values still, keeping only key value fixed per batch

      // Batching by first (mapping) generator
      batchesByFirst.get(0).get.data.iterator.toSeq mustBe ParamValues.distinctValues2.iterator.toSeq.flatMap(x => {
        ParamValues.mapping1.allValuesGenerator.partitions.get(0).get.iterator.toSeq.map(y => {
          CombinedModifier(Seq(CombinedModifier(y.map(z => z.toModifier)), CombinedModifier(Seq(x.toModifier))))
        })
      })

      batchesByFirst.get(1).get.data.iterator.toSeq mustBe ParamValues.distinctValues2.iterator.toSeq.flatMap(x => {
        ParamValues.mapping1.allValuesGenerator.partitions.get(1).get.iterator.toSeq.map(y => {
          CombinedModifier(Seq(CombinedModifier(y.map(z => z.toModifier)), CombinedModifier(Seq(x.toModifier))))
        })
      })
      // Batching by second generator
      batchesBySecond.get(0).get.data.iterator.toSeq mustBe ParamValues.mapping1.allValuesGenerator.iterator.toSeq.map(x => {
        CombinedModifier(
          Seq(CombinedModifier(Seq(ParamValues.distinctValues2.get(0).get.toModifier)), x.toModifier)
        )
      })
      batchesBySecond.get(1).get.data.iterator.toSeq mustBe ParamValues.mapping1.allValuesGenerator.iterator.toSeq.map(x => {
        CombinedModifier(
          Seq(CombinedModifier(Seq(ParamValues.distinctValues2.get(1).get.toModifier)), x.toModifier)
        )
      })
      batchesBySecond.get(2).get.data.iterator.toSeq mustBe ParamValues.mapping1.allValuesGenerator.iterator.toSeq.map(x => {
        CombinedModifier(
          Seq(CombinedModifier(Seq(ParamValues.distinctValues2.get(2).get.toModifier)), x.toModifier)
        )
      })
    }
  }

}
