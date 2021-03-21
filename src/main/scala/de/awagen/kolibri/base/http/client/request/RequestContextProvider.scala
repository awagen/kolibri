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

package de.awagen.kolibri.base.http.client.request

import de.awagen.kolibri.base.exceptions.InvalidDataException
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues, OrderedMultiValuesBatch}
import de.awagen.kolibri.datatypes.utils.MapUtils.combineMaps
import de.awagen.kolibri.datatypes.utils.{OrderedMultiValuesBatchUtils, PermutationGenerator}
import de.awagen.kolibri.datatypes.values.OrderedValues
import org.slf4j
import org.slf4j.LoggerFactory


case class RequestContextProvider(groupId: String,
                                  contextPath: String,
                                  fixedParams: Map[String, Seq[String]],
                                  variedParams: OrderedMultiValues) extends Serializable {

  val logger: slf4j.Logger = LoggerFactory.getLogger(RequestContextProvider.getClass.toString)

  val parameterNames: Seq[String] = variedParams.getParameterNameSequence
  val nrOfValuesPerParameter: Seq[Int] = variedParams.values.toStream.map(x => x.totalValueCount).toList
  val nrOfElements: Int = variedParams.numberOfCombinations

  def nrOfValuesPerParameterName(name: String): Int = {
    val paramIndex: Int = parameterNames.indexOf(name)
    if (paramIndex < 0) 0 else nrOfValuesPerParameter(paramIndex)
  }

  def createRequestExecutionContext(requestSequenceId: Int, variedParameterMap: Map[String, Any]): RequestContext = {
    val variedParams: Map[String, Seq[String]] = variedParameterMap.map {
      case (a: String, b: Seq[Any]) => (a, b.map(y => y.toString))
      case (a: String, b: Any) => (a, Seq(b.toString))
    }
    val requestContext: RequestContext = new RequestContext(groupId = groupId, requestSequenceId = requestSequenceId, contextPath = contextPath,
      parameters = combineMaps[Seq[String]](variedParams, fixedParams, _ ++ _))
    requestContext
  }

  def iterator(): Iterator[RequestContext] = {
    new Iterator[RequestContext]() {
      private[this] var currentPosition: Int = 0
      private[this] var currentPositionIndices: Seq[Int] = variedParams.values.toStream.map(_ => 0).toList

      override def hasNext: Boolean = {
        variedParams.numberOfCombinations >= currentPosition + 1
      }

      override def next(): RequestContext = {
        val currentIndex = currentPosition
        val variedParameterMap = currentPositionIndices.indices.toStream
          .map(x => parameterNames(x) -> variedParams.values(x).getNthZeroBased(currentPositionIndices(x)).get).toMap

        val tmp = PermutationGenerator.generateNextParameters(currentPositionIndices, nrOfValuesPerParameter, 1)
        if (tmp.nonEmpty) currentPositionIndices = tmp.head

        currentPosition += 1
        createRequestExecutionContext(currentIndex, variedParameterMap)
      }
    }
  }

  def splitIntoBatchIteratorAtMostNrOfBatches(nrOfBatches: Int): Iterator[RequestContextProvider] = {
    OrderedMultiValuesBatchUtils.splitIntoBatchIteratorAtMostNrOfBatches(variedParams, nrOfBatches)
      .map(x => RequestContextProvider(groupId, contextPath, fixedParams, x))
  }

  def splitIntoAtMostNrOfBatches(nrOfBatches: Int): Seq[RequestContextProvider] = {
    val batches: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoAtMostNrOfBatches(variedParams, nrOfBatches)
    batches.to(LazyList).map(x => RequestContextProvider(groupId, contextPath, fixedParams, x))
  }

  def splitIntoBatchIteratorOfSize(batchSize: Int): Iterator[RequestContextProvider] = {
    OrderedMultiValuesBatchUtils.splitIntoBatchIteratorOfSize(variedParams, batchSize).map(x => RequestContextProvider(groupId, contextPath, fixedParams, x))
  }

  def splitIntoBatchesOfSize(batchSize: Int): Seq[RequestContextProvider] = {
    val batches: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoBatchesOfSize(variedParams, batchSize)
    logger.info(s"split into nr of batches: ${batches.size}")
    batches.to(LazyList).map(x => RequestContextProvider(groupId, contextPath, fixedParams, x))
  }

  def splitIntoBatchesPerParamValue(paramName: String): Iterator[RequestContextProvider] = {
    val paramToSplitByOpt: Option[OrderedValues[Any]] = variedParams.values.find(x => x.name.eq(paramName))
    if (paramToSplitByOpt.isEmpty) {
      throw InvalidDataException(s"param with name '$paramName' not found in RequestContextProvider $this, can not split into batches", None)
    }
    val paramToSplitBy = paramToSplitByOpt.get
    val newVariedParams: Seq[OrderedValues[Any]] = variedParams.values.filter(x => !x.name.eq(paramName))

    new Iterator[RequestContextProvider]() {
      private[this] var currentPosition: Int = 0

      override def hasNext: Boolean = currentPosition < paramToSplitBy.totalValueCount

      override def next(): RequestContextProvider = {
        val newFixedParams: Map[String, Seq[String]] = fixedParams + (paramToSplitBy.name -> Seq(
          String.valueOf(paramToSplitBy.getNthZeroBased(currentPosition).get)))
        currentPosition += 1
        RequestContextProvider(groupId,
          contextPath,
          newFixedParams,
          GridOrderedMultiValues(newVariedParams))
      }
    }
  }
}
