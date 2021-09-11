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

import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.CalculationResult
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

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  type CalculationResult[T] = Either[Seq[ComputeFailReason], T]

  trait Calculation[In, Out] extends KolibriSerializable {
    val name: String

    def apply(in: In): Out
  }

  trait FutureCalculation[In, ResultIdentifier, Out] extends KolibriSerializable {
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
          val metricRow: MetricRow = metricsCalculation.calculateAll(immutable.Map(parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*), judgements)
          logger.debug(s"calculated metrics: $metricRow")
          metricRow
        })
        .recover(throwable => {
          var params: Map[String, Seq[String]] = Map.empty
          try {
            params = Map(msg.get[RequestTemplate](requestTemplateKey).get.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)):_*)
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

  case class BaseBooleanCalculation(name: String, function: Seq[Boolean] => CalculationResult[Double]) extends Calculation[Seq[Boolean], CalculationResult[Double]] {
    override def apply(msg: Seq[Boolean]): CalculationResult[Double] = {
      function.apply(msg)
    }
  }

  case class FromMapCalculation[T, U](name: String, dataKey: String, function: T => CalculationResult[U]) extends Calculation[WeaklyTypedMap[String], CalculationResult[U]] {
    override def apply(msg: WeaklyTypedMap[String]): CalculationResult[U] = {
      val data: Option[T] = msg.get[T](dataKey)
      data.map(value => function.apply(value))
        .getOrElse(Left(Seq(missingKeyFailReason(dataKey))))
    }
  }

  /**
    * Calculation providing Future of the computation result, given a WeaklyTypedMap.
    * @param name - name of the calculation
    * @param calculationResultIdentifier - the value names that are generated by the computation. Only relevant if the return type U is some kind of map
    * @param function - the computation function
    * @tparam U - type of the result of the computation
    */
  case class FromMapFutureCalculation[U, V](name: String, calculationResultIdentifier: V, function: FutureCalculation[WeaklyTypedMap[String], V, U]) extends FutureCalculation[WeaklyTypedMap[String], V, U] {
    override def apply(msg: WeaklyTypedMap[String])(implicit ec: ExecutionContext): Future[U] = {
      function.apply(msg)
    }
  }

  def booleanFindFirst(name: String, findTrue: Boolean): Calculation[Seq[Boolean], CalculationResult[Double]] =
    BaseBooleanCalculation(name, findFirstValue(findTrue))

  def booleanCount(name: String, findTrue: Boolean): Calculation[Seq[Boolean], CalculationResult[Double]] = {
    BaseBooleanCalculation(name, countValues(findTrue))
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
      metricRow = metricRow.addMetric(addValue)
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
      metricRow = metricRow.addMetric(addValue)
    })
    metricRow
  }

  def resultEitherToMetricRowResponse(metricName: String, result: CalculationResult[Double], params: Map[String, Seq[String]]): MetricRow = result match {
    case Left(e) => computeFailReasonsToMetricRowResponse(e, metricName, params)
    case Right(e) =>
      val addValue: MetricValue[Double] = MetricValue.createAvgSuccessSample(metricName, e)
      MetricRow.empty.copy(params = params).addMetric(addValue)
  }

  def findFirstValue(findTrue: Boolean): SerializableFunction1[Seq[Boolean], CalculationResult[Double]] = new SerializableFunction1[Seq[Boolean], CalculationResult[Double]] {
    override def apply(msg: Seq[Boolean]): Either[Seq[ComputeFailReason], Double] =
      msg.indices.find(index => if (findTrue) msg(index) else !msg(index))
        .map(x => Right(x.toDouble))
        .getOrElse(Left(Seq(ComputeFailReason.NO_RESULTS)))
  }

  def countValues(findTrue: Boolean): SerializableFunction1[Seq[Boolean], CalculationResult[Double]] = new SerializableFunction1[Seq[Boolean], CalculationResult[Double]] {
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

  def booleanPrecision(useTrue: Boolean, k: Int): SerializableFunction1[Seq[Boolean], CalculationResult[Double]] = new SerializableFunction1[Seq[Boolean], CalculationResult[Double]] {
    override def apply(msg: Seq[Boolean]): CalculationResult[Double] = {
      val binarizedSeq = binarizeBooleanSeq(!useTrue, msg)
      IRMetricFunctions.precisionAtK(k, 0.9).apply(binarizedSeq)
    }
  }

}
