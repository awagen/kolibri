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


package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.base.cluster.ClusterNodeObj
import de.awagen.kolibri.base.config.AppProperties.config.judgementQueryAndProductDelimiter
import de.awagen.kolibri.base.directives.{Resource, RetrievalDirective}
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.resources.RetrievalError
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{BooleanSeqToDoubleCalculation, Calculation, ComputeResult, ResultRecord}
import de.awagen.kolibri.base.usecase.searchopt.metrics.ComputeFailReason.missingKeyFailReason
import de.awagen.kolibri.base.usecase.searchopt.metrics.ComputeResultFunctions.{countValues, findFirstValue}
import de.awagen.kolibri.base.usecase.searchopt.metrics.PlainMetricValueFunctions.binarizeBooleanSeq
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.values.MetricValue


/**
 * Holding distinct types of calculation definitions.
 */
object Calculations {

  type ComputeResult[+T] = Either[Seq[ComputeFailReason], T]

  trait Record[+T] extends KolibriSerializable {
    def name: String

    def value: T
  }

  case class ResultRecord[+T](name: String, value: ComputeResult[T]) extends Record[ComputeResult[T]]

  trait Calculation[In, +Out] extends KolibriSerializable {
    def calculation: SerializableFunction1[In, Seq[ResultRecord[Out]]]

    def names: Set[String]
  }

  /**
   * Generic class to derive a double-valued metric from a sequence of booleans.
   * @param names - set of metric names, here referring to the set of names of all produced metrics. Only duplicated here
   *              to be able to know the metric names upfront without calculating
   * @param calculation - calculation function, calculating metrics from sequence of booleans
   */
  case class BooleanSeqToDoubleCalculation(names: Set[String], calculation: SerializableFunction1[Seq[Boolean], Seq[ResultRecord[Double]]]) extends Calculation[Seq[Boolean], Double]

  /**
   * Calculation of single result, given a data key to retrieve input data from
   * WeaklyTypedMap.
   * @param names - Set with single name value corresponding to the name for the result / name of calculated metric
   * @param dataKey - the key for the weakly typed map to retrieve the input data
   * @param function - function for value calculation
   * @tparam T: type of input value as retrieved from WeaklyTypedMap
   * @tparam U: type of the result
   */
  case class FromMapCalculation[T, U](names: Set[String], dataKey: String, function: T => ComputeResult[U]) extends Calculation[WeaklyTypedMap[String], U] {
    override val calculation: SerializableFunction1[WeaklyTypedMap[String], Seq[ResultRecord[U]]] = tMap => {
      val data: Option[T] = tMap.get[T](dataKey)
      val result: ComputeResult[U] = data.map(value => function.apply(value))
        .getOrElse(Left(Seq(missingKeyFailReason(dataKey))))
      Seq(ResultRecord(names.head, result))
    }
  }

  /**
   * Similar to JudgementBasedMetricsCalculation, yet here this uses judgements as resource, e.g
   * the set of judgements need to be pre-loaded as node resource.
   * One purpose is to unify all calculation definitions purely based on either fields created by result parsing
   * or from noad resources.
   * @param productIdsKey - the key value used to store the productIds in the WeaklyTypedMap[String]
   * @param queryParamName - name of the parameter used in requests as query parameter
   * @param judgementsResource - the resource identifier for the judgements map
   * @param metricsCalculation - definition of which metrics to calculate
   */
  case class JudgementsFromResourceIRMetricsCalculations(productIdsKey: String,
                                                         queryParamName: String,
                                                         judgementsResource: Resource[Map[String, Double]],
                                                         metricsCalculation: MetricsCalculation) extends Calculation[WeaklyTypedMap[String], Double] {
    def calculationResultIdentifier: Set[String] = metricsCalculation.metrics.map(x => x.name).toSet

    override val names: Set[String] = metricsCalculation.metrics.map(metric => metric.name).toSet

    override val calculation: SerializableFunction1[WeaklyTypedMap[String], Seq[ResultRecord[Double]]] = tMap => {
      val requestTemplate: RequestTemplate = tMap.get[RequestTemplate](REQUEST_TEMPLATE_STORAGE_KEY.name).get
      val query: String = requestTemplate.getParameter(queryParamName).map(x => x.head).getOrElse("")
      val productOpt: Option[Seq[String]] = tMap.get[Seq[String]](productIdsKey)
      val judgementsOrError: Either[RetrievalError[Map[String, Double]], Map[String, Double]] = ClusterNodeObj.getResource(RetrievalDirective.Retrieve(judgementsResource))
      judgementsOrError match {
        case Left(error) =>
          metricsCalculation.metrics.map(x => ResultRecord(x.name, Left(Seq(ComputeFailReason.apply(error.cause.toString)))))
        case Right(judgementsMap) =>
          val judgementsOptSeq: Seq[Option[Double]] = productOpt
            .map(products => {
              products.map(product => {
                val productQueryKey = createKey(query, product)
                judgementsMap.get(productQueryKey)
              })
            })
            .getOrElse(Seq.empty)
          metricsCalculation.calculateAllAndReturnSingleResults(judgementsOptSeq)
      }
    }
  }

  /**
   * Query product delimiter needs to be the same as in the loading part, we
   * refer here to the delimiter set in properties.
   * Function creates query-product key to be used to request judgements from
   * a judgement mapping
   *
   * @return
   */
  private[metrics] def createKey(searchTerm: String, productId: String): String = {
    s"$searchTerm$judgementQueryAndProductDelimiter$productId"
  }

}

object CalculationFunctions {

  def booleanFindFirst(names: Set[String], findTrue: Boolean): Calculation[Seq[Boolean], Double] =
    BooleanSeqToDoubleCalculation(names, seq => Seq(ResultRecord(names.head, findFirstValue(findTrue).apply(seq))))

  def booleanCount(names: Set[String], findTrue: Boolean): Calculation[Seq[Boolean], Double] = {
    BooleanSeqToDoubleCalculation(names, seq => Seq(ResultRecord(names.head, countValues(findTrue).apply(seq))))
  }

}

object ComputeResultFunctions {

  def identity[T]: SerializableFunction1[T, ComputeResult[T]] = new SerializableFunction1[T, ComputeResult[T]] {
    override def apply(msg: T): Either[Seq[ComputeFailReason], T] = Right(msg)
  }

  def findFirstValue(findTrue: Boolean): SerializableFunction1[Seq[Boolean], ComputeResult[Double]] = new SerializableFunction1[Seq[Boolean], ComputeResult[Double]] {
    override def apply(msg: Seq[Boolean]): Either[Seq[ComputeFailReason], Double] =
      msg.indices.find(index => if (findTrue) msg(index) else !msg(index))
        .map(x => Right(x.toDouble))
        .getOrElse(Left(Seq(ComputeFailReason.NO_RESULTS)))
  }

  def countValues(findTrue: Boolean): SerializableFunction1[Seq[Boolean], ComputeResult[Double]] = new SerializableFunction1[Seq[Boolean], ComputeResult[Double]] {
    override def apply(msg: Seq[Boolean]): Either[Seq[ComputeFailReason], Double] =
      Right(msg.indices.count(index => if (findTrue) msg(index) else !msg(index)).toDouble)
  }

  def booleanPrecision(useTrue: Boolean, k: Int): SerializableFunction1[Seq[Boolean], ComputeResult[Double]] = new SerializableFunction1[Seq[Boolean], ComputeResult[Double]] {
    override def apply(msg: Seq[Boolean]): ComputeResult[Double] = {
      val binarizedSeq = binarizeBooleanSeq(!useTrue, msg)
      IRMetricFunctions.precisionAtK(k, 0.9).apply(binarizedSeq)
    }
  }
}

object MetricRowFunctions {

  /**
   * Helper function to generate a MetricRow result from a throwable.
   * Since the scope of the exception might affect multiple metrics
   * (e.g such as when a request fails, which affects all metrics),
   * thus it takes a set of metric names
   *
   * @param e : The throwable to map to MetricRow
   * @return
   */
  def throwableToMetricRowResponse(e: Throwable, valueNames: Set[String], params: Map[String, Seq[String]]): MetricRow = {
    var metricRow = MetricRow.empty.copy(params = params)
    valueNames.foreach(x => {
      val addValue: MetricValue[Double] = MetricValue.createDoubleAvgFailSample(metricName = x, failMap = Map[ComputeFailReason, Int](ComputeFailReason(s"${e.getClass.getName}") -> 1))
      metricRow = metricRow.addMetricDontChangeCountStore(addValue)
    })
    metricRow
  }

  /**
   * Helper function to generate a MetricRow result from a ComputeFailReason
   *
   * @param failReason - ComputeFailReason
   * @return
   */
  def computeFailReasonsToMetricRowResponse(failReason: Seq[ComputeFailReason], metricName: String, params: Map[String, Seq[String]]): MetricRow = {
    var metricRow = MetricRow.empty.copy(params = params)
    failReason.foreach(reason => {
      val addValue: MetricValue[Double] = MetricValue.createDoubleAvgFailSample(metricName = metricName, failMap = Map[ComputeFailReason, Int](reason -> 1))
      metricRow = metricRow.addMetricDontChangeCountStore(addValue)
    })
    metricRow
  }

  def resultEitherToMetricRowResponse(metricName: String, result: ComputeResult[Double], params: Map[String, Seq[String]]): MetricRow = result match {
    case Left(e) => MetricRowFunctions.computeFailReasonsToMetricRowResponse(e, metricName, params)
    case Right(e) =>
      val addValue: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample(metricName, e, 1.0)
      MetricRow.empty.copy(params = params).addMetricDontChangeCountStore(addValue)
  }
}

object MetricValueFunctions {

  def resultRecordToMetricValue(record: ResultRecord[Double]): MetricValue[Double] = {
    record.value match {
      case Left(failReasons) =>
        val countMap: Map[ComputeFailReason, Int] = failReasons.toSet[ComputeFailReason].map(reason => (reason, failReasons.count(fr => fr == reason))).toMap
        MetricValue.createDoubleAvgFailSample(metricName = record.name, failMap = countMap)
      case Right(value) =>
        MetricValue.createDoubleAvgSuccessSample(record.name, value, 1.0)
    }
  }

}

object PlainMetricValueFunctions {

  /**
   * Transform sequence of booleans to sequence of double, where True -> 1.0, False -> 0.0 or the other way around if invert = True
   * @param invert - if True, transforms False -> 1.0, True -> 0.0, otherwise False -> 0.0, True -> 1.0
   * @param seq - the sequence of booleans
   * @return mapped sequence
   */
  def binarizeBooleanSeq(invert: Boolean, seq: Seq[Boolean]): Seq[Double] = {
    seq.indices.map(index => if (!invert) {
      if (seq(index)) 1.0 else 0.0
    } else {
      if (!seq(index)) 1.0 else 0.0
    })
  }

  /**
   * Takes sequence of type T and counts the occurrence for each value.
   * @param seq - sequence of elements of type T
   * @param k - number giving how many of the results (from start) are taken into account
   * @return - Map with key = value, value = count of occurrence
   */
  def valueToOccurrenceCountMap[T](seq: Seq[T], k: Int): Map[T, Int] =  {
    seq.slice(0, k).toSet[T].map[(T, Int)](x => (x, seq.count(y => y == x))).toMap
  }

  /**
   * Given a sequence of values, derive the unique values
   * @param seq - Input sequence
   * @param k - number of first values of input sequence to take into account
   * @return: set of unique values occurring in first k elements of input sequence
   */
  def stringSequenceToUniqueValues[T](seq: Seq[T], k: Int): Set[T]  =  {
    seq.slice(0, k).toSet
  }

  /**
   *
   * @param seq - value seq
   * @param k - number of first values of input sequence to take into account
   * @return count of distinct values occurring in the first k elements
   */
  def stringSequenceToUniqueValuesCount[T](seq: Seq[T], k: Int): Int  =  {
    stringSequenceToUniqueValues(seq, k).size
  }

  /**
   *
   * @param seq - value seq
   * @param value - value to compare input sequence values against to count occurrence
   * @param k - number of first values of input sequence to take into account
   * @return number of occurrences of value
   */
  def occurrencesOfValue[T](seq: Seq[T], value: T, k: Int): Int = {
    seq.count(x => x == value)
  }

}
