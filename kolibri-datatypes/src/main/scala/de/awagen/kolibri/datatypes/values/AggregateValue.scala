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

trait AggregateValue[+A] extends KolibriSerializable {

  def numSamples: Int
  def weight: Double
  def value: A
  def weighted(weight: Double): AggregateValue[A]

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[AggregateValue[A]]) {
      false
    }
    else {
      val other = obj.asInstanceOf[AggregateValue[A]]
      this.numSamples == other.numSamples && this.weight == other.weight && this.value == other.value
    }
  }

  def emptyCopy(): AggregateValue[A]

  def add[B >: A](other: AggregateValue[B]): AggregateValue[A]

  def add[B >: A](otherValue: DataPoint[B]): AggregateValue[A]

}
