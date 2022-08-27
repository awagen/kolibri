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
import de.awagen.kolibri.base.directives.{Resource, RetrievalDirective, WithResources}
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.resources.RetrievalError
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{ComputeResult, ResultRecord}
import de.awagen.kolibri.base.usecase.searchopt.metrics.ComputeFailReason.missingKeyFailReason
import de.awagen.kolibri.base.usecase.searchopt.metrics.Functions.{countValues, findFirstValue, throwableToMetricRowResponse}
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.values.MetricValue
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}


/**
 * Holding distinct types of calculation definitions.
 */
object Calculations {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

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


  trait FutureCalculation[In, ResultIdentifier, Out] extends KolibriSerializable with WithResources {
    val name: String
    val calculationResultIdentifier: ResultIdentifier

    def apply(in: In)(implicit ec: ExecutionContext): Future[Out]
  }

  case class JudgementBasedMetricsCalculation(name: String,
                                              queryParamName: String,
                                              requestTemplateKey: String,
                                              productIdsKey: String,
                                              judgementProviderFactory: JudgementProviderFactory[Double],
                                              metricsCalculation: MetricsCalculation,
                                              excludeParamsFromMetricRow: Seq[String]) extends FutureCalculation[WeaklyTypedMap[String], Set[String], MetricRow] {

    override val calculationResultIdentifier: Set[String] = metricsCalculation.metrics.map(x => x.name).toSet

    override def apply(msg: WeaklyTypedMap[String])(implicit ec: ExecutionContext): Future[MetricRow] = {
      judgementProviderFactory.getJudgements.future
        .map(y => {
          val requestTemplate: RequestTemplate = msg.get[RequestTemplate](requestTemplateKey).get
          val productIds: Seq[String] = msg.get[Seq[String]](productIdsKey).get
          val query: String = requestTemplate.getParameter(queryParamName).map(x => x.head).getOrElse("")
          val parameters: Map[String, Seq[String]] = requestTemplate.parameters
          val judgements: Seq[Option[Double]] = y.retrieveJudgements(query, productIds)
          logger.debug(s"retrieved judgements: $judgements")
          val metricRow: MetricRow = metricsCalculation.calculateAllAndAddAllToMetricRow(immutable.Map(parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*), judgements)
          logger.debug(s"calculated metrics: $metricRow")
          metricRow
        })
        .recover(throwable => {
          var params: Map[String, Seq[String]] = Map.empty
          try {
            params = Map(msg.get[RequestTemplate](requestTemplateKey).get.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*)
          }
          catch {
            case _: Throwable =>
              logger.warn("failed retrieving parameters from sample")
          }
          logger.warn(s"failed retrieving judgements: $throwable")
          throwableToMetricRowResponse(throwable, calculationResultIdentifier, params)
        })
    }

  }

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
   * @param requestTemplateKey - the key under which the request template is stored in the WeaklyTypedMap[String] to be able to retrieve request
   *                           information, such as parameters set and the like
   * @param metricsCalculation - definition of which metrics to calculate
   */
  case class JudgementsFromResourceIRMetricsCalculations(productIdsKey: String,
                                                         queryParamName: String,
                                                         judgementsResource: Resource[Map[String, Double]],
                                                         requestTemplateKey: String,
                                                         metricsCalculation: MetricsCalculation) extends Calculation[WeaklyTypedMap[String], Double] {
    def calculationResultIdentifier: Set[String] = metricsCalculation.metrics.map(x => x.name).toSet

    override val names: Set[String] = metricsCalculation.metrics.map(metric => metric.name).toSet

    override val calculation: SerializableFunction1[WeaklyTypedMap[String], Seq[ResultRecord[Double]]] = tMap => {
      val requestTemplate: RequestTemplate = tMap.get[RequestTemplate](requestTemplateKey).get
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

  /**
   * Calculation providing Future of the computation result, given a WeaklyTypedMap.
   *
   * @param name                        - name of the calculation
   * @param calculationResultIdentifier - the value names that are generated by the computation. Only relevant if the return type U is some kind of map
   * @param function                    - the computation function
   * @tparam U - type of the result of the computation
   */
  case class FromMapFutureCalculation[U, V](name: String, calculationResultIdentifier: V, function: FutureCalculation[WeaklyTypedMap[String], V, U]) extends FutureCalculation[WeaklyTypedMap[String], V, U] {
    override def apply(msg: WeaklyTypedMap[String])(implicit ec: ExecutionContext): Future[U] = {
      function.apply(msg)
    }
  }

  def booleanFindFirst(names: Set[String], findTrue: Boolean): Calculation[Seq[Boolean], Double] =
    BooleanSeqToDoubleCalculation(names, seq => Seq(ResultRecord(names.head, findFirstValue(findTrue).apply(seq))))

  def booleanCount(names: Set[String], findTrue: Boolean): Calculation[Seq[Boolean], Double] = {
    BooleanSeqToDoubleCalculation(names, seq => Seq(ResultRecord(names.head, countValues(findTrue).apply(seq))))
  }

}

object Functions {

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
      val addValue: MetricValue[Double] = MetricValue.createAvgFailSample(metricName = x, failMap = Map[ComputeFailReason, Int](ComputeFailReason(s"${e.getClass.getName}") -> 1))
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
      val addValue: MetricValue[Double] = MetricValue.createAvgFailSample(metricName = metricName, failMap = Map[ComputeFailReason, Int](reason -> 1))
      metricRow = metricRow.addMetricDontChangeCountStore(addValue)
    })
    metricRow
  }

  def resultEitherToMetricRowResponse(metricName: String, result: ComputeResult[Double], params: Map[String, Seq[String]]): MetricRow = result match {
    case Left(e) => computeFailReasonsToMetricRowResponse(e, metricName, params)
    case Right(e) =>
      val addValue: MetricValue[Double] = MetricValue.createAvgSuccessSample(metricName, e, 1.0)
      MetricRow.empty.copy(params = params).addMetricDontChangeCountStore(addValue)
  }

  def resultRecordToMetricValue(record: ResultRecord[Double]): MetricValue[Double] = {
    record.value match {
      case Left(failReasons) =>
        val countMap: Map[ComputeFailReason, Int] = failReasons.toSet[ComputeFailReason].map(reason => (reason, failReasons.count(fr => fr == reason))).toMap
        MetricValue.createAvgFailSample(metricName = record.name, failMap = countMap)
      case Right(value) =>
        MetricValue.createAvgSuccessSample(record.name, value, 1.0)
    }
  }

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

  def binarizeBooleanSeq(invert: Boolean, seq: Seq[Boolean]): Seq[Double] = {
    seq.indices.map(index => if (!invert) {
      if (seq(index)) 1.0 else 0.0
    } else {
      if (!seq(index)) 1.0 else 0.0
    })
  }

  def booleanPrecision(useTrue: Boolean, k: Int): SerializableFunction1[Seq[Boolean], ComputeResult[Double]] = new SerializableFunction1[Seq[Boolean], ComputeResult[Double]] {
    override def apply(msg: Seq[Boolean]): ComputeResult[Double] = {
      val binarizedSeq = binarizeBooleanSeq(!useTrue, msg)
      IRMetricFunctions.precisionAtK(k, 0.9).apply(binarizedSeq)
    }
  }

}
