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
import de.awagen.kolibri.datatypes.values.RunningValue.RunningValueAdd.{doubleAvgAdd, doubleAvgWeightFunction, errorMapAdd, failMapWeightFon}


object RunningValue {

  object RunningValueAdd extends Enumeration with KolibriSerializable {

    case class RVal[A](addFunc: SerializableFunction2[AggregateValue[A], AggregateValue[A], A]) extends Val with KolibriSerializable

    val doubleAvgAdd: RVal[Double] = RVal[Double](new SerializableFunction2[AggregateValue[Double], AggregateValue[Double], Double] {
      override def apply(v1: AggregateValue[Double], v2: AggregateValue[Double]): Double = {
        val totalWeight: Double = v1.weight + v2.weight
        if (totalWeight == 0) 0.0 else (v1.value * v1.weight + v2.weight * v2.value) / totalWeight
      }
    })

    val errorMapAdd: RVal[Map[ComputeFailReason, Int]] = {
      val func: SerializableFunction2[AggregateValue[Map[ComputeFailReason, Int]], AggregateValue[Map[ComputeFailReason, Int]], Map[ComputeFailReason, Int]] = new SerializableFunction2[AggregateValue[Map[ComputeFailReason, Int]], AggregateValue[Map[ComputeFailReason, Int]], Map[ComputeFailReason, Int]] {
        override def apply(x: AggregateValue[Map[ComputeFailReason, Int]], y: AggregateValue[Map[ComputeFailReason, Int]]): Map[ComputeFailReason, Int] = {
          val allKeys = x.value.keySet ++ y.value.keySet
          allKeys.map(k => k -> (x.value.getOrElse(k, 0) + y.value.getOrElse(k, 0))).toMap
        }
      }
      RVal(func)
    }

    val doubleAvgWeightFunction: SerializableFunction2[AggregateValue[Double], Double, DataPoint[Double]] = new SerializableFunction2[AggregateValue[Double], Double, DataPoint[Double]] {
      override def apply(v1: AggregateValue[Double], v2: Double): DataPoint[Double] = {
        val newValue: Double = v1.value  / v2
        DataSample(v1.weight * v2, newValue)
      }
    }

    val failMapWeightFon: (AggregateValue[Map[ComputeFailReason, Int]], Double) => DataPoint[Map[ComputeFailReason, Int]] = new SerializableFunction2[AggregateValue[Map[ComputeFailReason, Int]], Double,  DataPoint[Map[ComputeFailReason, Int]]] {
      override def apply(v1: AggregateValue[Map[ComputeFailReason, Int]], v2: Double): DataPoint[Map[ComputeFailReason, Int]] = DataSample(v1.weight, v1.value)
    }
  }

  def doubleAvgRunningValue(weightedCount: Double, count: Int, value: Double): RunningValue[Double] =
    RunningValue(weightedCount, count, value, doubleAvgWeightFunction, doubleAvgAdd.addFunc)

  def calcErrorRunningValue(count: Int, value: Map[ComputeFailReason, Int]): RunningValue[Map[ComputeFailReason, Int]] =
    RunningValue(count, count, value, failMapWeightFon, errorMapAdd.addFunc)

  def mapFromFailReasons(as: Seq[ComputeFailReason]): Map[ComputeFailReason, Int] = {
    as.toSet[ComputeFailReason]
      .map(x => x -> as.count(_ == x))
      .toMap
  }
}

case class RunningValue[A](weight: Double,
                           numSamples: Int,
                           value: A,
                           weightFunction: (AggregateValue[A], Double) => DataPoint[A],
                           addFunc: (AggregateValue[A], AggregateValue[A]) => A) extends AggregateValue[A] {

  override def add(other: DataPoint[A]): AggregateValue[A] = {
    this.add(RunningValue(other.weight, 1, other.data, weightFunction, addFunc))
  }

  override def add(other: AggregateValue[A]): AggregateValue[A] = {
    RunningValue(weight + other.weight, numSamples = this.numSamples + other.numSamples, value = addFunc.apply(this, other), weightFunction, addFunc = addFunc)
  }

  override def weighted(weight: Double): AggregateValue[A] = {
    val weightedValue: DataPoint[A] = weightFunction.apply(this, weight)
    RunningValue(weightedValue.weight, numSamples, weightedValue.data, weightFunction, addFunc)
  }
}
