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


package de.awagen.kolibri.datatypes.values

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableFunction2, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.Calculations.ResultRecord
import de.awagen.kolibri.datatypes.values.RunningValues.{RunningValue, calcErrorRunningValue}

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

  trait TwoInCalculation[In1, In2, +Out] extends KolibriSerializable {
    def calculation: SerializableFunction2[In1, In2, Seq[ResultRecord[Out]]]

    def names: Set[String]
  }

}

object MetricValueFunctions {

  object AggregationType extends Enumeration {

    type AggregationType = Val[_]

    case class Val[+T](emptyRunningValueSupplier: SerializableSupplier[RunningValue[T]]) extends super.Val {

      def singleSampleToRunningValue(record: ResultRecord[_]): RunningValue[_] = {
        record.value match {
          case Left(failReasons) =>
            val countMap: Map[ComputeFailReason, Int] = failReasonSeqToCountMap(failReasons)
            RunningValues.calcErrorRunningValue(1, countMap)
          case Right(value) =>
            val emptyValue: RunningValue[T] = emptyRunningValueSupplier.apply()
            emptyValue.copy(weight = 1.0, numSamples = 1, value = value)
        }
      }
    }

    def byName(name: String): Val[_] = name.toUpperCase match {
      case "DOUBLE_AVG" => DOUBLE_AVG
      case "MAP_UNWEIGHTED_SUM_VALUE" => MAP_UNWEIGHTED_SUM_VALUE
      case "MAP_WEIGHTED_SUM_VALUE" => MAP_WEIGHTED_SUM_VALUE
      case "NESTED_MAP_UNWEIGHTED_SUM_VALUE" => NESTED_MAP_UNWEIGHTED_SUM_VALUE
      case "NESTED_MAP_WEIGHTED_SUM_VALUE" => NESTED_MAP_WEIGHTED_SUM_VALUE
      case _ => throw new IllegalArgumentException(s"no DataFileType by name '$name' found")
    }

    val DOUBLE_AVG: Val[Double] = Val(() => RunningValues.doubleAvgRunningValue(0.0, 0, 0.0))
    val MAP_AVG_VALUE: Val[Map[String, Double]] = Val(() => RunningValues.mapValueUnweightedSumRunningValue(0.0, 0, Map.empty[String, Double]))
    val MAP_UNWEIGHTED_SUM_VALUE: Val[Map[String, Double]] = Val(() => RunningValues.mapValueUnweightedSumRunningValue(0.0, 0, Map.empty[String, Double]))
    val MAP_WEIGHTED_SUM_VALUE: Val[Map[String, Double]] = Val(() => RunningValues.mapValueWeightedSumRunningValue(0.0, 0, Map.empty[String, Double]))
    val NESTED_MAP_UNWEIGHTED_SUM_VALUE: Val[Map[String, Map[String, Double]]] = Val(() => RunningValues.nestedMapValueUnweightedSumUpRunningValue(0.0, 0, Map.empty[String, Map[String, Double]]))
    val NESTED_MAP_WEIGHTED_SUM_VALUE: Val[Map[String, Map[String, Double]]] = Val(() => RunningValues.nestedMapValueWeightedSumUpRunningValue(0.0, 0, Map.empty[String, Map[String, Double]]))
    val ERROR_MAP_VALUE: Val[Map[ComputeFailReason, Int]] = Val(() => RunningValues.calcErrorRunningValue(0, Map.empty))
  }


  def failReasonSeqToCountMap(failReasons: Seq[ComputeFailReason]): Map[ComputeFailReason, Int] = {
    failReasons.toSet[ComputeFailReason].map(reason => (reason, failReasons.count(fr => fr == reason))).toMap
  }

  def initMetricValueFromValueRunningValue(name: String, runningValue: RunningValue[_]) = {
    MetricValue(
      name = name,
      BiRunningValue(value1 = calcErrorRunningValue(0, Map.empty), value2 = runningValue))
  }

  def resultRecordToDoubleAvgMetricValue(record: ResultRecord[Any]): MetricValue[Double] = {
    record.value match {
      case Left(failReasons) =>
        val countMap: Map[ComputeFailReason, Int] = failReasonSeqToCountMap(failReasons)
        MetricValue.createDoubleAvgFailSample(metricName = record.name, failMap = countMap)
      case Right(value) =>
        MetricValue.createDoubleAvgSuccessSample(record.name, value.asInstanceOf[Double], 1.0)
    }
  }

  def resultRecordToMapCountMetricValue[T](weighted: Boolean)(record: ResultRecord[Any]): MetricValue[Map[T, Double]] = {
    record.value match {
      case Left(failReasons) =>
        val countMap: Map[ComputeFailReason, Int] = failReasonSeqToCountMap(failReasons)
        MetricValue.createMapValueCountFailSample[T](
          metricName = record.name,
          failMap = countMap,
          runningValue = if (weighted) RunningValues.mapValueWeightedSumRunningValue[T](1.0, 1, Map.empty[T, Double]) else RunningValues.mapValueUnweightedSumRunningValue[T](1.0, 1, Map.empty[T, Double])
        )
      case Right(value) =>
        MetricValue.createMapSumValueSuccessSample(
          record.name,
          value.asInstanceOf[Map[T, Double]],
          1.0)
    }
  }

  def resultRecordToNestedMapCountMetricValue[U, V](weighted: Boolean)(record: ResultRecord[Any]): MetricValue[Map[U, Map[V, Double]]] = {
    record.value match {
      case Left(failReasons) =>
        val countMap: Map[ComputeFailReason, Int] = failReasonSeqToCountMap(failReasons)
        MetricValue.createNestedMapSumValueFailSample[U, V](weighted = weighted)(
          metricName = record.name,
          failMap = countMap
        )
      case Right(value) =>
        MetricValue.createNestedMapSumValueSuccessSample(weighted = weighted)(
          record.name,
          value.asInstanceOf[Map[U, Map[V, Double]]],
          1.0)
    }
  }


}
