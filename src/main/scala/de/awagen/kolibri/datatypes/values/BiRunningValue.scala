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

case class BiRunningValue[A, B](value1: AggregateValue[A], value2: AggregateValue[B]) {
  def add(other: BiRunningValue[A, B]): BiRunningValue[A, B] = BiRunningValue(value1.add(other.value1), value2.add(other.value2))

  def addFirst(other: AggregateValue[A]): BiRunningValue[A, B] = BiRunningValue(value1.add(other), value2)

  def addFirst(other: A): BiRunningValue[A, B] = BiRunningValue(value1.add(other), value2)

  def addSecond(other: AggregateValue[B]): BiRunningValue[A, B] = BiRunningValue(value1, value2.add(other))

  def addSecond(other: B): BiRunningValue[A, B] = BiRunningValue(value1, value2.add(other))

  def addAll(others: BiRunningValue[A, B]*): BiRunningValue[A, B] = {
    var newValue1: AggregateValue[A] = value1
    var newValue2: AggregateValue[B] = value2
    others.foreach(x => {
      newValue1 = newValue1.add(x.value1)
      newValue2 = newValue2.add(x.value2)
    })
    BiRunningValue(newValue1, newValue2)
  }


  def addEither(either: Either[A, B]): BiRunningValue[A, B] = either match {
    case Left(a) => addFirst(a)
    case Right(b) => addSecond(b)
  }

}
