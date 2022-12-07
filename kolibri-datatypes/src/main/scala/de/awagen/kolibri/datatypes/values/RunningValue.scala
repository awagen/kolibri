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
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction2
import de.awagen.kolibri.datatypes.values.AggregationUtils.{divideNumericMapValues, filterValuesByInstanceOfCheck, sumUpNestedNumericValueMaps, numericValueMapAggregateValueSumUp}
import de.awagen.kolibri.datatypes.values.RunningValue.RunningValueAdd.{doubleAvgAdd, errorMapAdd, failMapKeepWeightFon, valueMapAdd, valueMapAvg, valueNestedMapAdd, weightMultiplyFunction}
import org.slf4j.{Logger, LoggerFactory}


object RunningValue {

  object RunningValueAdd extends Enumeration with KolibriSerializable {

    val logger: Logger = LoggerFactory.getLogger(RunningValue.getClass)

    case class RVal[+A](addFunc: (AggregateValue[Any], AggregateValue[Any]) => A) extends Val with KolibriSerializable

    val doubleAvgAdd: RVal[Double] = RVal[Double](new SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Double] {
      override def apply(v1: AggregateValue[Any], v2: AggregateValue[Any]): Double = {
        val totalWeight: Double = v1.weight + v2.weight
        if (totalWeight == 0) 0.0 else (v1.value.asInstanceOf[Double] * v1.weight + v2.weight * v2.value.asInstanceOf[Double]) / totalWeight
      }
    })

    /**
     * Summing up the values per key of the inner maps
     */
    def valueNestedMapAdd[U, V](weighted: Boolean): RVal[Map[U, Map[V, Double]]] = RVal[Map[U, Map[V, Double]]](new SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Map[U, Map[V, Double]]] {
      override def apply(v1: AggregateValue[Any], v2: AggregateValue[Any]): Map[U, Map[V, Double]] = {
        val validMergeValues: Seq[AggregateValue[Map[U, Map[V, Double]]]] = filterValuesByInstanceOfCheck[Map[U, Map[V, Double]]](v1, v2)
        if (validMergeValues.size < 2) {
          logger.warn(s"Not all values match the required type for merge, value1: '${v1.value.getClass.getName}', " +
            s"value2: '${v2.value.getClass.getName}'")
        }
        sumUpNestedNumericValueMaps(0, weighted, validMergeValues: _*)
      }
    })

    /**
     * Summing up the values per key
     *
     * @return
     */
    def valueMapAdd[T](weighted: Boolean): RVal[Map[T, Double]] = RVal[Map[T, Double]](new SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Map[T, Double]] {
      override def apply(v1: AggregateValue[Any], v2: AggregateValue[Any]): Map[T, Double] = {
        val validMergeValues: Seq[AggregateValue[Map[T, Double]]] = filterValuesByInstanceOfCheck[Map[T, Double]](v1, v2)
        if (validMergeValues.size < 2) {
          logger.warn(s"Not all values match the required type for merge, value1: '${v1.value.getClass.getName}', " +
            s"value2: '${v2.value.getClass.getName}'")
        }
        numericValueMapAggregateValueSumUp(0, weighted, validMergeValues: _*)
      }
    })

    /**
     * Averaging the values per key
     *
     * @return
     */
    def valueMapAvg[T]: RVal[Map[T, Double]] = RVal[Map[T, Double]](new SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Map[T, Double]] {
      override def apply(v1: AggregateValue[Any], v2: AggregateValue[Any]): Map[T, Double] = {
        val totalWeight: Double = Seq(v1, v2).map(x => x.weight).sum
        val summedUp: Map[T, Double] = valueMapAdd[T](weighted = true).addFunc.apply(v1, v2)
        divideNumericMapValues[T, Double](totalWeight, summedUp)
      }
    })

    val errorMapAdd: RVal[Map[ComputeFailReason, Int]] = {
      val func: SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Map[ComputeFailReason, Int]] = new SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Map[ComputeFailReason, Int]] {
        override def apply(x: AggregateValue[Any], y: AggregateValue[Any]): Map[ComputeFailReason, Int] = {
          val allKeys = x.value.asInstanceOf[Map[ComputeFailReason, Int]].keySet ++ y.value.asInstanceOf[Map[ComputeFailReason, Int]].keySet
          allKeys.map(k => k -> (x.value.asInstanceOf[Map[ComputeFailReason, Int]].getOrElse(k, 0) + y.value.asInstanceOf[Map[ComputeFailReason, Int]].getOrElse(k, 0))).toMap
        }
      }
      RVal(func)
    }

    val weightMultiplyFunction: (Double, Double) => Double = new SerializableFunction2[Double, Double, Double] {
      override def apply(v1: Double, v2: Double): Double = {
        v1 * v2
      }
    }

    val failMapKeepWeightFon: (Double, Double) => Double = new SerializableFunction2[Double, Double, Double] {
      override def apply(v1: Double, v2: Double): Double = v1
    }
  }

  /**
   * Running value that sums up the (weighted!) values of the created map values
   */
  def mapValueWeightedSumUpRunningValue[T](weightedCount: Double, count: Int, value: Map[T, Double]): RunningValue[Map[T, Double]] = {
    RunningValue(weightedCount, count, value, weightMultiplyFunction, valueMapAdd(weighted = true).addFunc)
  }

  /**
   * Running value that sums up the (unweighted, meaning weight = 1.0 per sample) values of the created map values
   */
  def mapValueUnweightedSumUpRunningValue[T](weightedCount: Double, count: Int, value: Map[T, Double]): RunningValue[Map[T, Double]] = {
    RunningValue(weightedCount, count, value, weightMultiplyFunction, valueMapAdd(weighted = false).addFunc)
  }

  /**
   * Running value that sums up the values (weighted!) of the created map values
   */
  def nestedMapValueWeightedSumUpRunningValue[U, V](weightedCount: Double, count: Int, value: Map[U, Map[V, Double]]): RunningValue[Map[U, Map[V, Double]]] = {
    RunningValue(weightedCount, count, value, weightMultiplyFunction, valueNestedMapAdd(weighted = true).addFunc)
  }

  /**
   * Running value that sums up the values (unweighted, meaning weight = 1.0 per sample) of the created map values
   */
  def nestedMapValueUnweightedSumUpRunningValue[U, V](weightedCount: Double, count: Int, value: Map[U, Map[V, Double]]): RunningValue[Map[U, Map[V, Double]]] = {
    RunningValue(weightedCount, count, value, weightMultiplyFunction, valueNestedMapAdd(weighted = false).addFunc)
  }

  /**
   * Running value that averages the values of the created map values
   */
  def mapValueAvgRunningValue[T](weightedCount: Double, count: Int, value: Map[T, Double]): RunningValue[Map[T, Double]] = {
    RunningValue(weightedCount, count, value, weightMultiplyFunction, valueMapAvg.addFunc)
  }

  def doubleAvgRunningValue(weightedCount: Double, count: Int, value: Double): RunningValue[Double] =
    RunningValue(weightedCount, count, value, weightMultiplyFunction, doubleAvgAdd.addFunc)


  def calcErrorRunningValue(count: Int, value: Map[ComputeFailReason, Int]): RunningValue[Map[ComputeFailReason, Int]] =
    RunningValue(count, count, value, failMapKeepWeightFon, errorMapAdd.addFunc)

  def mapFromFailReasons(as: Seq[ComputeFailReason]): Map[ComputeFailReason, Int] = {
    as.toSet[ComputeFailReason]
      .map(x => x -> as.count(_ == x))
      .toMap
  }
}

/**
 *
 * @param weight         - current weight
 * @param numSamples     - number of samples that are contained in the aggregation
 * @param value          - the current value
 * @param weightFunction - function taking current weight and new weight, providing new weight (used in call of weighted function)
 * @param addFunc        - function used to add two AggregateValues
 * @tparam A - type of the value
 */
case class RunningValue[+A](weight: Double,
                            numSamples: Int,
                            value: A,
                            weightFunction: (Double, Double) => Double,
                            addFunc: (AggregateValue[_], AggregateValue[_]) => A) extends AggregateValue[A] {

  override def add[B >: A](other: DataPoint[B]): AggregateValue[A] = {
    this.add(RunningValue(other.weight, 1, other.data, weightFunction, addFunc))
  }

  override def add[B >: A](other: AggregateValue[B]): AggregateValue[A] = {
    RunningValue(weight + other.weight, numSamples = this.numSamples + other.numSamples, value = addFunc.apply(this, other), weightFunction, addFunc = addFunc)
  }

  override def weighted(weight: Double): AggregateValue[A] = {
    RunningValue(weightFunction.apply(this.weight, weight), numSamples, this.value, weightFunction, addFunc)
  }
}
