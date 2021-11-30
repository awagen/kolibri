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

case class DistinctValues[+T](name: String, values: Seq[T]) extends OrderedValues[T] {

  val seq: Seq[T] = values.distinct
  val totalValueCount: Int = seq.size

  override def getNthZeroBased(n: Int): Option[T] = {
    if (n >= 0 && n < seq.size) Some(seq(n)) else None
  }

  override def getNFromPositionZeroBased(position: Int, n: Int): Seq[T] = seq.slice(position, position + n)

  override def getAll: Seq[T] = seq

}
