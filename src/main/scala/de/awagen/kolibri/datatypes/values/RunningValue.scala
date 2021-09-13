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
import de.awagen.kolibri.datatypes.values.RunningValueAdd.{doubleAvgAdd, errorMapAdd}

object RunningValueAdd extends Enumeration with KolibriSerializable {

  case class RVal[A](addFunc: SerializableFunction2[AggregateValue[A], AggregateValue[A], A]) extends Val with KolibriSerializable

  val doubleAvgAdd: RVal[Double] = RVal[Double](new SerializableFunction2[AggregateValue[Double], AggregateValue[Double], Double] {
    override def apply(v1: AggregateValue[Double], v2: AggregateValue[Double]): Double = {
      val totalCount: Double = v1.count + v2.count
      if (totalCount == 0) 0.0 else (v1.value * v1.count + v2.count * v2.value) / (v1.count + v2.count)

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

}

object RunningValue {

  def doubleAvgRunningValue(count: Double, value: Double): RunningValue[Double] = RunningValue(count, value, doubleAvgAdd.addFunc)

  def calcErrorRunningValue(count: Double, value: Map[ComputeFailReason, Int]): RunningValue[Map[ComputeFailReason, Int]] = RunningValue(count, value, errorMapAdd.addFunc)

  def mapFromFailReasons(as: Seq[ComputeFailReason]): Map[ComputeFailReason, Int] = {
    as.toSet[ComputeFailReason]
      .map(x => x -> as.count(_ == x))
      .toMap
  }
}

case class RunningValue[A](count: Double,
                           value: A,
                           addFunc: SerializableFunction2[AggregateValue[A], AggregateValue[A], A]) extends AggregateValue[A] {

  override def add(other: AggregateValue[A]): AggregateValue[A] = {
    RunningValue(count = this.count + other.count, value = addFunc.apply(this, other), addFunc = addFunc)
  }

  override def add(other: A): AggregateValue[A] = {
    RunningValue(count = this.count + 1, value = addFunc.apply(this, RunningValue(1, other, addFunc)), addFunc = addFunc)
  }
}
