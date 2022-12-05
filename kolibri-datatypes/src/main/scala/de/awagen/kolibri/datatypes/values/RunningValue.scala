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
import de.awagen.kolibri.datatypes.values.RunningValue.RunningValueAdd.{doubleAvgAdd, errorMapAdd, failMapKeepWeightFon, weightMultiplyFunction}


object RunningValue {

  object RunningValueAdd extends Enumeration with KolibriSerializable {

    case class RVal[+A](addFunc: (AggregateValue[Any], AggregateValue[Any]) => A) extends Val with KolibriSerializable

    val doubleAvgAdd: RVal[Double] = RVal[Double](new SerializableFunction2[AggregateValue[Any], AggregateValue[Any], Double] {
      override def apply(v1: AggregateValue[Any], v2: AggregateValue[Any]): Double = {
        val totalWeight: Double = v1.weight + v2.weight
        if (totalWeight == 0) 0.0 else (v1.value.asInstanceOf[Double] * v1.weight + v2.weight * v2.value.asInstanceOf[Double]) / totalWeight
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
 * @param weight - current weight
 * @param numSamples - number of samples that are contained in the aggregation
 * @param value - the current value
 * @param weightFunction - function taking current weight and new weight, providing new weight (used in call of weighted function)
 * @param addFunc - function used to add two AggregateValues
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
