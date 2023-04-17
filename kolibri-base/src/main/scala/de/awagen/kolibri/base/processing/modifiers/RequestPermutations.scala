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

import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers.{BodyMapper, HeadersMapper, ParamsMapper}
import de.awagen.kolibri.base.processing.modifiers.RequestModifiersUtils._
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers._
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator, OneAfterAnotherIndexedGenerator, PermutatingIndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues

object RequestPermutations {

  trait ModifierGeneratorProvider extends KolibriSerializable {

    def partitions: IndexedGenerator[IndexedGenerator[Modifier[RequestTemplateBuilder]]]

    def modifiers: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]]

  }


  /**
    * Given a generator of key values, generate the correct generators that permutate over all given key-specific combination
    * of mapper values
    *
    * @param keyGen        - the generator generating the keys that are mapped to yield the modifiers
    * @param paramsMapper  - a mapper to yield parameter modifiers
    * @param headersMapper -  a mapper to yield header modifiers
    * @param bodyMapper    - a mapper to yield body modifiers
    */
  case class MappingModifier(keyGen: IndexedGenerator[String],
                             paramsMapper: ParamsMapper,
                             headersMapper: HeadersMapper,
                             bodyMapper: BodyMapper) extends ModifierGeneratorProvider {

    /**
     * Generate Seq of generators where each generator corresponds to the combinations belonging to single key
     * @return
     */
    def perKeyPermutations: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      var permutationsPerKeySeq: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = Seq.empty
      keyGen.iterator.foreach(key => {
        val paramsOpt: Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = paramsMapper.getModifiersForKey(key)
        val headersOpt: Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = headersMapper.getModifiersForKey(key)
        val bodiesOpt: Option[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = bodyMapper.getModifiersForKey(key)
        var generators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = Seq.empty
        paramsOpt.foreach(x => generators = generators :+ x)
        headersOpt.foreach(x => generators = generators :+ x)
        bodiesOpt.foreach(x => generators = generators :+ x)
        if (generators.nonEmpty) {
          val permutations: IndexedGenerator[Modifier[RequestTemplateBuilder]] = PermutatingIndexedGenerator(generators).mapGen(x => CombinedModifier(x))
          permutationsPerKeySeq = permutationsPerKeySeq :+ permutations
        }
      })
      permutationsPerKeySeq
    }

    /**
     * to allow batching by key, and the parameters for distinct keys shall not be applied at the same time
     * partitions provide the sequential list of IndexedGenerators that can be mixed in with the other IndexedGenerators
     * to be permutated with them separately.
     * Thus each generator generated reflects the values valid for a single key.
     * @return
     */
    override def partitions: IndexedGenerator[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(perKeyPermutations)
    }

    /**
     * @return Seq with single generator that generates the values for a single key, then the values for the next
     *         till values for all keys where generated, one after another.
     */
    override def modifiers: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      Seq(OneAfterAnotherIndexedGenerator(perKeyPermutations))
    }
  }

  /**
    * Container for the distinct values to generate Modifier[RequestTemplateBuilder] from. One single parameter name
    * in the case of params and headers a generator is created providing all defined values.
    * For bodies its a single generator providing all distinct body values. bodyContentType determines the format
    * of the passed body
    *
    * @param params
    * @param headers
    * @param bodies
    * @param bodyContentType
    */
  case class RequestPermutation(params: OrderedMultiValues,
                                headers: OrderedMultiValues,
                                bodies: Seq[String],
                                bodyReplacements: OrderedMultiValues,
                                headerValueReplacements: OrderedMultiValues,
                                urlParameterReplacements: OrderedMultiValues,
                                bodyContentType: String = "application/json") extends ModifierGeneratorProvider {
    val paramModifierGenerators: Seq[IndexedGenerator[RequestParameterModifier]] = multiValuesToRequestParamModifiers(params, replace = false)
      .filter(gen => gen.size > 0)
    val headerModifierGenerators: Seq[IndexedGenerator[HeaderModifier]] = multiValuesToHeaderModifiers(headers, replace = false)
      .filter(gen => gen.size > 0)
    val bodyModifierGenerator: Option[IndexedGenerator[BodyModifier]] = if (bodies.isEmpty) None else Some(bodiesToBodyModifier(bodies, bodyContentType))
    val bodyReplaceModifierGenerators: Seq[IndexedGenerator[BodyReplaceModifier]] = multiValuesToBodyReplaceModifiers(bodyReplacements)
      .filter(gen => gen.size > 0)
    val urlParameterReplaceModifierGenerators: Seq[IndexedGenerator[UrlParameterReplaceModifier]] = multiValuesToUrlParameterReplaceModifiers(urlParameterReplacements)
      .filter(gen => gen.size > 0)
    val headerValuesReplaceModifierGenerators: Seq[IndexedGenerator[HeaderValueReplaceModifier]] = multiValuesToHeaderValueReplaceModifiers(headerValueReplacements)
      .filter(gen => gen.size > 0)



    /**
     * First single-element generators are generated that refer to the single param
     * modifiers, then the header modifiers, then the body modifiers, and no combinations of them will be generated.
     * Thus its a generator of single-element generators only containing one single modification to the request.
     */
    override def partitions: IndexedGenerator[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      OneAfterAnotherIndexedGenerator(modifiers).mapGen(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(x)))
    }

    /**
     * @return Seq of generators appended in following order: 1) param modifier generators, 2) header modifier
     *         generators, 3) body modifier generators
     */
    override def modifiers: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      val combined: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] =
        paramModifierGenerators ++ headerModifierGenerators ++ bodyReplaceModifierGenerators ++
          urlParameterReplaceModifierGenerators ++ headerValuesReplaceModifierGenerators
      bodyModifierGenerator.map(x => combined :+ x).getOrElse(combined)
    }
  }

}