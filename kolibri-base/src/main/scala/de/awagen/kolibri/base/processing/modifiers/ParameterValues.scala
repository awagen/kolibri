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

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.util.ByteString
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{CombinedModifier, RequestTemplateBuilderModifier}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator, OneAfterAnotherIndexedGenerator, PartitionByGroupIndexedGenerator, PermutatingIndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.SerializableCallable

import scala.collection.mutable.ArrayBuffer

object ParameterValues {

  object ParameterValuesImplicits {

    implicit class ParameterValueToRequestBuilderModifier(value: ParameterValue) {
      def toModifier: RequestTemplateBuilderModifier = {
        value.valueType match {
          case ValueType.URL_PARAMETER =>
            RequestTemplateBuilderModifiers.RequestParameterModifier(Map(value.name -> Seq(value.value)), replace = false)
          case ValueType.HEADER =>
            RequestTemplateBuilderModifiers.HeaderModifier(Seq(RawHeader(value.name, value.value)), replace = false)
          case ValueType.BODY =>
            RequestTemplateBuilderModifiers.BodyModifier(HttpEntity.Strict(ContentTypes.`application/json`, ByteString(value.value)))
        }
      }
    }

    implicit class ParameterValueSeqToRequestBuilderModifier(values: Seq[ParameterValue]) {
      def toModifier: RequestTemplateBuilderModifier = {
        CombinedModifier(values.map(x => x.toModifier))
      }
    }
  }

  object ValueType extends Enumeration with KolibriSerializable {
    type ValueType = Value
    val URL_PARAMETER, HEADER, BODY = Value
  }

  sealed trait ValueSeqGenProvider extends KolibriSerializable {

    /**
     * In the case of a single parameter with distinct values this would just put each value in a separate Seq(element),
     * while in case of a generator already providing Seq of ParameterValues the generator can just be reused as is.
     * Just serves to be able to put any kind of values (standalone and mapped values) in a collection of generators
     * and calculate the combined generators of Seq[ParameterValue]
     *
     * @return
     */
    def toSeqGenerator: IndexedGenerator[Seq[ParameterValue]]

  }

  /**
   * Define parameter values with name and given type and range of values.
   *
   * @param name      - name of the parameter
   * @param valueType - value type
   * @param values    - single values the parameter can take
   */
  case class ParameterValues(name: String,
                             valueType: ValueType.Value,
                             values: IndexedGenerator[String]) extends IndexedGenerator[ParameterValue] with ValueSeqGenProvider {
    private[this] val parameterValueGenerator = values.mapGen(x => ParameterValue(name, valueType, x))
    override val nrOfElements: Int = values.nrOfElements

    override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[ParameterValue] = {
      parameterValueGenerator.getPart(startIndex, endIndex)
    }

    override def get(index: Int): Option[ParameterValue] = parameterValueGenerator.get(index)

    override def mapGen[B](f: SerializableCallable.SerializableFunction1[ParameterValue, B]): IndexedGenerator[B] = {
      parameterValueGenerator.mapGen(f)
    }

    override def toSeqGenerator: IndexedGenerator[Seq[ParameterValue]] = this.mapGen(x => Seq(x))
  }

  case class MappedParameterValues(name: String,
                                   valueType: ValueType.Value,
                                   values: Map[String, IndexedGenerator[String]]) extends KolibriSerializable


  case class ParameterValue(name: String, valueType: ValueType.Value, value: String) extends KolibriSerializable


  /**
   *
   * @param values
   */
  case class ParameterValuesGenSeqToValueSeqGenerator(values: Seq[ValueSeqGenProvider]) extends IndexedGenerator[Seq[ParameterValue]] {
    val seqGenerators: Seq[IndexedGenerator[Seq[ParameterValue]]] = values.map(x => x.toSeqGenerator)
    val generator: IndexedGenerator[Seq[ParameterValue]] = PermutatingIndexedGenerator(seqGenerators).mapGen(x => x.flatten)

    override val nrOfElements: Int = seqGenerators.map(x => x.size).product

    override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[ParameterValue]] = generator.getPart(startIndex, endIndex)

    override def get(index: Int): Option[Seq[ParameterValue]] = generator.get(index)

    override def mapGen[B](f: SerializableCallable.SerializableFunction1[Seq[ParameterValue], B]): IndexedGenerator[B] = {
      generator.mapGen(f)
    }
  }

  /**
   * Describe a mapping of generated values to mappedValues. By optionally passing mappingKeyValueAssignments
   * This allows mapping some value in the mappedValues seq be mapped to the mapped values of another element
   * in mappedValues that is coming before. Here index 0 indicates the non-mapped values as key, while starting from
   * index 1 the values from mappedValues can be referenced.
   * Some assertions are made here regarding mappingKeyValueAssignments:
   * 1) strictly 2nd index > 1st index (e.g key value must come before in the sequence of values)
   * 2) start value can not be < 0
   * 3) any defined index is smaller than 1 + mappedValues.length (+ 1 comes from having values generated by 'values'
   * on index 0)
   *
   * @param keyValues                  - unmapped values
   * @param mappedValues               - values mapped by another value
   * @param mappingKeyValueAssignments - If empty, all mapped values will be mapped against the unmapped values.
   *                                   With index 0 representing the unmapped values and other mapped values
   *                                   are starting from index 1 in the sequence they are defined in in the passed Seq
   *
   */
  class ParameterValueMapping(val keyValues: ParameterValues,
                              val mappedValues: Seq[MappedParameterValues],
                              val mappingKeyValueAssignments: Seq[(Int, Int)]) extends IndexedGenerator[Seq[ParameterValue]] with ValueSeqGenProvider {
    val cleanedKeyValues: ParameterValues = removeTopLevelKeysWithMissingMappings(keyValues)
    // a few assumptions such that we can just linearly from start to end generate our values
    // tuple index1 must always smaller than index2
    assert(!mappingKeyValueAssignments.exists(x => x._1 > x._2), s"Second index in mapping tuples must be bigger than first index, violated" +
      s"in $mappingKeyValueAssignments")
    // tuple index1 must always be >= 0
    assert(!mappingKeyValueAssignments.exists(x => x._1 < 0))
    // index2 is always smaller then 1 + mappedValues.length
    assert(!mappingKeyValueAssignments.exists(x => x._2 >= mappedValues.length + 1))


    private[modifiers] def removeTopLevelKeysWithMissingMappings(pVals: ParameterValues): ParameterValues = {
      // extract those indices we need to check (only those mapped to index 0)
      val checkMappedValueIndices = ArrayBuffer(mappedValues.indices: _*)
      mappingKeyValueAssignments.filter(x => x._1 != 0).map(x => x._2).foreach(index => checkMappedValueIndices.remove(index - 1))
      var removeValues = Seq.empty[String]
      pVals.values.iterator.foreach(value => {
        // check for value whether has matches in each mappedValue
        val missesMatch = checkMappedValueIndices.toSeq.exists(x => !mappedValues(x).values.contains(value))
        if (missesMatch) removeValues = removeValues :+ value
      })
      var newParameterValues = pVals.values.iterator.toSeq
      removeValues.foreach(value => {
        newParameterValues = newParameterValues.filter(x => x != value)
      })
      ParameterValues(pVals.name, pVals.valueType, ByFunctionNrLimitedIndexedGenerator.createFromSeq(newParameterValues))
    }

    val fromToMappingIndices: Seq[(Int, Int)] = mappedValues.indices.map(mappedIndex => {
      mappingKeyValueAssignments.find(x => x._2 == mappedIndex + 1)
        .map(x => (x._1, mappedIndex))
        .getOrElse((0, mappedIndex))
    })

    /**
     * Generates all mapped values for a given ParameterValue providing the key and a single MappedParameterValue instance
     *
     * @param parameterValue - single value providing the key
     * @param mappedValues   - the mapped value to generate parameter values for
     * @return
     */
    private[modifiers] def generateMappedValueFromParameterValue(parameterValue: ParameterValue, mappedValues: MappedParameterValues): Option[IndexedGenerator[ParameterValue]] = {
      mappedValues.values.get(parameterValue.value)
        .map(gen => gen.mapGen(v => ParameterValue(mappedValues.name, mappedValues.valueType, v)))
    }

    /**
     * For a passed ParameterValue, generate all permutations with the mapped values. This allows backward reference
     * to allow mapping to another mapped value
     *
     * @param pValue - the single ParameterValue to generate mappings for (should usually be an element from the unmapped
     *               values provided by 'values'
     * @return
     */
    private[modifiers] def generateValuesWithIndexedBackwardReferences(pValue: ParameterValue): IndexedGenerator[Seq[ParameterValue]] = {
      // the partial generators produced so far. As soon as one mapped value is dependent on another mapped value
      // they need to be merged in the same generator, thus the generator of Seq[ParameterValue]
      var mappedSoFar: Seq[IndexedGenerator[Seq[ParameterValue]]] = Seq.empty
      // the indices contained per mappedSoFar index. To know which generator to extend by a new value
      // 0-based indices in the order of mappedValues
      var mappedIndices: Seq[Seq[Int]] = Seq.empty

      mappedValues.indices.foreach(mappedIndex => {
        val keyIndex: Int = fromToMappingIndices(mappedIndex)._1
        if (keyIndex == 0) {
          // were adding a generator of the single mapped values and place the index this value corresponds to
          // to be able to resolve further mappings
          generateMappedValueFromParameterValue(pValue, mappedValues(mappedIndex))
            .map(x => x.mapGen(y => Seq(y)))
            .foreach(x => {
              mappedSoFar = mappedSoFar :+ x
              mappedIndices = mappedIndices :+ Seq(mappedIndex)
            })
        }
        else {
          // first find the generator and within it the value index to map against (index correction by -1 as
          // mapped values start at index 1
          val mappedGeneratorIndexTupleOpt: Option[(Int, Int)] = mappedIndices.indices
            .find(x => mappedIndices(x).contains(keyIndex - 1))
            .map(index => (index, mappedIndices(index).indexOf(keyIndex - 1)))
          // looking at tuple._1 element of mappedSoFar and picking the tuple._2th value to extend the respective generator
          mappedGeneratorIndexTupleOpt.map(tuple => {
            val matchedGenerator: IndexedGenerator[Seq[ParameterValue]] = mappedSoFar(tuple._1)
            // now we need to extend that generator using an OneAfterAnotherGenerator for each mapped value of the new mapped
            // parameter and the already existing ones
            // now update the mapping for its particular index and add the just added mapped value index to the index seq
            val generatorWithNewValuesAdded: IndexedGenerator[Seq[ParameterValue]] = mergeValueGeneratorWithMapped(matchedGenerator, mappedValues(mappedIndex), tuple._2)
            mappedSoFar = mappedSoFar.updated(tuple._1, generatorWithNewValuesAdded)
            mappedIndices = mappedIndices.updated(tuple._1, mappedIndices(tuple._1) :+ tuple._2)
          })
        }
      })
      PermutatingIndexedGenerator(mappedSoFar)
        .mapGen(x => x.flatten)
        // place the key value back in at start of values
        .mapGen(x => Seq(pValue) ++ x)
    }

    /**
     * Takes a generator generating sequence of parameter values of which one represents the key to map the mapped value,
     * by. Returns the generator resulting from merging in the mappedValue, e.g by permutating each sequence generated
     * by the keyGenerator by the values mapped to the key. This is done by, for each Seq[ParameterValue] generated
     * by the keyGenerator, pick the mapped values and create an IndexedGenerator[ParameterValue] that differs only
     * in the mapped value. All in this way resulting sequence generators are then combined in a wrapping generator
     * that just generates values from one generator after the other.
     *
     * @param keyGenerator
     * @param mappedValue
     * @param keyIndex
     * @return
     */
    private[modifiers] def mergeValueGeneratorWithMapped(keyGenerator: IndexedGenerator[Seq[ParameterValue]], mappedValue: MappedParameterValues, keyIndex: Int): IndexedGenerator[Seq[ParameterValue]] = {
      val allPermutationsSeq: Seq[IndexedGenerator[Seq[ParameterValue]]] = keyGenerator.iterator.map(valueSeq => {
        val key: ParameterValue = valueSeq(keyIndex)
        val valuesForKeyOpt: Option[IndexedGenerator[ParameterValue]] = mappedValue.values.get(key.value)
          .map(x =>
            x.mapGen(y =>
              ParameterValue(mappedValue.name, mappedValue.valueType, y)
            )
          )
        // now create generator of the combined data which keeps the current value sequence constant and just
        // varies the mapped values
        valuesForKeyOpt.map(valuesGen => valuesGen.mapGen(value => {
          valueSeq :+ value
        }))
      })
        .filter(x => x.nonEmpty)
        .map(x => x.get)
        .toSeq
      // generate all values one after another by wrapping the generators belonging to single sequences generated
      // by the keyGenerator into a generator that just generates multiple generators after each other
      OneAfterAnotherIndexedGenerator(allPermutationsSeq)
    }


    /**
     * generator whose top-level generator is defined by the unmapped values, e.g each of the lower level generators
     * contained will always contain a single value of the unmapped values.
     *
     * @return
     */
    private[modifiers] def generatorsByValues: IndexedGenerator[IndexedGenerator[Seq[ParameterValue]]] = {
      cleanedKeyValues.mapGen(x => {
        if (mappingKeyValueAssignments.isEmpty) {
          generateValuesFromKeyValue(x)
        }
        else {
          generateValuesWithIndexedBackwardReferences(x)
        }
      })
    }

    /**
     * Given a single key value, generate all full Seq[ParameterValue] values generated from it
     * (note that all mappings will need to exist, e.g if some mapping refers to another mapped value,
     * also this full chain of mappings has to exist to have the parameter sequence included here, e.g
     * no partials will be returned
     *
     * @param keyValue
     * @return
     */
    private[modifiers] def generateValuesFromKeyValue(keyValue: ParameterValue): IndexedGenerator[Seq[ParameterValue]] = {
      // generate the mapped values and generate all permutations for every unmapped value
      val filteredMappedValues: Seq[IndexedGenerator[ParameterValue]] = mappedValues.map(mappedVal => {
        generateMappedValueFromParameterValue(keyValue, mappedVal)
      })
        .filter(v => v.nonEmpty)
        .map(v => v.get)

      PermutatingIndexedGenerator(Seq(
        ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(keyValue))
      ) ++ filteredMappedValues)
    }

    // to generate all combinations, we use generator that keeps the partitioning by key generator values
    val allValuesGenerator: IndexedGenerator[Seq[ParameterValue]] = PartitionByGroupIndexedGenerator(
      generatorsByValues.iterator.toSeq)

    override val nrOfElements: Int = allValuesGenerator.nrOfElements

    override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[ParameterValue]] = allValuesGenerator.getPart(startIndex, endIndex)

    override def get(index: Int): Option[Seq[ParameterValue]] = allValuesGenerator.get(index)

    override def mapGen[B](f: SerializableCallable.SerializableFunction1[Seq[ParameterValue], B]): IndexedGenerator[B] =
      allValuesGenerator.mapGen(f)

    /**
     * explicitly define partitions such that partitioning strictly happens by the key values and not by
     * the mapped values.
     *
     * @return
     */
    override def partitions: IndexedGenerator[IndexedGenerator[Seq[ParameterValue]]] = generatorsByValues

    override def toSeqGenerator: IndexedGenerator[Seq[ParameterValue]] = this
  }

}
