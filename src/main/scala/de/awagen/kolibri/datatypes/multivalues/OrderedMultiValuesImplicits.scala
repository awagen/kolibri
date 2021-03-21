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

package de.awagen.kolibri.datatypes.multivalues

import de.awagen.kolibri.datatypes.collections.{BaseIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

object OrderedMultiValuesImplicits {

  val logger: Logger = LoggerFactory.getLogger(OrderedMultiValuesImplicits.getClass.toString)

  implicit class OrderedMultiValuesToParameterIterator(val multiValues: OrderedMultiValues) {

    /**
      * from OrderedMultiValues generate iterator over sequence of named values (_._1 is name of parameter, _._2 is value)
      *
      * @return
      */
    def toNamedParamValuesIterator: Iterator[Seq[(String, Any)]] = new Iterator[Seq[(String, Any)]] {
      val parameterNames: Seq[String] = multiValues.getParameterNameSequence
      val baseIterator: Iterator[Seq[Any]] = multiValues.toParamValuesIterator

      override def hasNext: Boolean = baseIterator.hasNext

      override def next(): Seq[(String, Any)] = {
        parameterNames.zip(baseIterator.next())
      }
    }

    /**
      * from OrderedMultiValues generate iterator over Map of key , values pairs, where key is the name of
      * the parameter in OrderedMultiValues, values is Seq over all values (e.g Seq in case any parameter name
      * is contained multiple times, as might be the case e.g for non-unique url parameters)
      *
      * @return
      */
    def toParamNameValuesMapIterator: Iterator[Map[String, Seq[Any]]] = new Iterator[Map[String, Seq[Any]]] {
      val baseIterator: Iterator[Seq[(String, Any)]] = multiValues.toNamedParamValuesIterator

      override def hasNext: Boolean = baseIterator.hasNext

      override def next(): Map[String, Seq[Any]] = {
        baseIterator.next().groupMapReduce(x => x._1)(x => Seq(x._2))((seq1, seq2) => seq1 ++ seq2)
      }
    }

    /**
      * from OrderedMultiValues generate iterator over sequence of values (the corresponding parameter name
      * for a value would be given by the element with the same index as the respective value in the
      * multiValues.getParameterNameSequence sequence of parameter names
      *
      * @return
      */
    def toParamValuesIterator: Iterator[Seq[Any]] = new Iterator[Seq[Any]] {
      var currentIndex = 0
      val elementCount: Int = multiValues.numberOfCombinations

      override def hasNext: Boolean = currentIndex < elementCount - 1

      override def next(): Seq[Any] = {
        currentIndex += 1
        multiValues.findNthElement(currentIndex - 1).get
      }
    }


    /**
      * transform OrderedMultiValues instance to IndexedGenerator of value sequence (without parameter names)
      *
      * @return
      */
    def toParamValuesIndexedGenerator: IndexedGenerator[Seq[Any]] = new IndexedGenerator[Seq[Any]] {
      override val nrOfElements: Int = multiValues.numberOfCombinations

      override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[Any]] = BaseIndexedGenerator(
        math.max(0, math.min(endIndex, nrOfElements) - math.max(0, startIndex)),
        x => multiValues.findNthElement(startIndex + x)
      )

      override def get(index: Int): Option[Seq[Any]] = multiValues.findNthElement(index)

      override def mapGen[B](f: SerializableFunction1[Seq[Any], B]): IndexedGenerator[B] = BaseIndexedGenerator(
        multiValues.numberOfCombinations,
        x => multiValues.findNthElement(x).map(y => f.apply(y))
      )

      override def iterator: Iterator[Seq[Any]] = multiValues.toParamValuesIterator
    }

    /**
      * transform OrderedMultiValues instance to IndexedGenerator mappings of parameter name
      * to values for that parameter
      *
      * @return
      */
    def toParamNameValuesMapIndexedGenerator: IndexedGenerator[Map[String, Seq[Any]]] = {
      val parameters: Seq[String] = multiValues.getParameterNameSequence
      multiValues.toParamValuesIndexedGenerator
        .mapGen(x => parameters.zip(x))
        .mapGen(x => x.groupMapReduce(x => x._1)(x => Seq(x._2))((seq1, seq2) => seq1 ++ seq2))
    }

    /**
      * transform OrderedMultiValues instance to IndexedGenerator Seq of (parameterName, parameterValue) pairs
      *
      * @return
      */
    def toNamedParamValuesIndexedGenerator: IndexedGenerator[Seq[(String, Any)]] = {
      val parameters: Seq[String] = multiValues.getParameterNameSequence
      multiValues.toParamValuesIndexedGenerator
        .mapGen(x => parameters.zip(x))
    }
  }

}
