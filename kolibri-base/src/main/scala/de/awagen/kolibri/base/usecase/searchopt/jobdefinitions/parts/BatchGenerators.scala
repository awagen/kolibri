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
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.CombinedModifier
import de.awagen.kolibri.datatypes.collections.generators.{BatchByGeneratorIndexedGenerator, ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}

object BatchGenerators {

  /**
    * From a collection of generators of modifiers to RequestTemplateBuilder, creates batches by the generator at the index
    * given by the passed index. E.g passing 0 will generate batches by the first generator in the collection, meaning
    * each batch will only permutate over a single value of the values contained within the first generator (the same holds
    * for any other index passed, e.g the index determines from which position the generator to batch by is picked)
    *
    * @param batchByIndex : Int - The index of the generator of modifiers to create batches by
    * @return
    */
  def batchByGeneratorAtIndex(batchByIndex: Int): Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] => IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]] =
    modifierGenerators => {
      val generator: IndexedGenerator[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = BatchByGeneratorIndexedGenerator(modifierGenerators, batchByIndex)
        .mapGen(x => x.mapGen(y => CombinedModifier(y)))
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(Range(0, generator.size).map(index => Batch(index, generator.get(index).get)))
    }

}
