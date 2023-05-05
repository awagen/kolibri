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

import de.awagen.kolibri.datatypes.values.aggregation.immutable.AggregateValue


/**
  * Running value of two distinct types, e.g can be used to record occurring errors and successful computation values
  * in a single record, e.g in case your computation returns Either[SomeFailType, SomeComputationValue] or similar
  * settings where two values are in some way connected. AggregateValue keeps the count of samples aggregated and
  * the current value of the aggregation
 *
  * @param value1 - first aggregate value
  * @param value2 - second aggregate value
  * @tparam A - type of the aggregate for value1
  * @tparam B - type of the aggregate for value2
  */
case class BiRunningValue[+A, +B](value1: AggregateValue[A], value2: AggregateValue[B]) {

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[BiRunningValue[A, B]]) {
      false
    }
    else {
      val other = obj.asInstanceOf[BiRunningValue[A, B]]
      this.value1 == other.value1 && this.value2 == other.value2
    }
  }

  def add[C >: A, D >: B](other: BiRunningValue[C, D]): BiRunningValue[A, B] = BiRunningValue(value1.add(other.value1), value2.add(other.value2))

  def addFirst[C >: A](other: AggregateValue[C]): BiRunningValue[A, B] = BiRunningValue(value1.add(other), value2)

  def addFirst[C >: A](other: DataPoint[C]): BiRunningValue[A, B] = BiRunningValue(value1.add(other), value2)

  def addSecond[D >: B](other: AggregateValue[D]): BiRunningValue[A, B] = BiRunningValue(value1, value2.add(other))

  def addSecond[D >: B](other: DataPoint[D]): BiRunningValue[A, B] = BiRunningValue(value1, value2.add(other))

  def addAll[C >: A, D >: B](others: BiRunningValue[C, D]*): BiRunningValue[A, B] = {
    var newValue1: AggregateValue[A] = value1
    var newValue2: AggregateValue[B] = value2
    others.foreach(x => {
      newValue1 = newValue1.add(x.value1)
      newValue2 = newValue2.add(x.value2)
    })
    BiRunningValue(newValue1, newValue2)
  }

  def addEither[C >: A, D >: B](either: Either[DataPoint[C], DataPoint[D]]): BiRunningValue[A, B] = either match {
    case Left(a) => addFirst(a)
    case Right(b) => addSecond(b)
  }

}
