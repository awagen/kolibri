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

import scala.Fractional.Implicits._
import scala.math.Fractional

object RangeValues {

  def equalWithPrecision[T:Fractional](x: T, y: T, precision: T)(implicit ev:Numeric[T]): Boolean = {
    ev.lteq(ev.abs(ev.minus(x,y)),precision)
  }

  def mult[T](a: T, b: T)(implicit ev:Numeric[T]): T =
    ev.times(a,b)
}

case class RangeValues[T:Fractional](name: String, start:T, end:T, stepSize:T)(implicit v: Numeric[T])
  extends OrderedValues[T] {

  val totalValueCount: Int = ((end - start) / stepSize).toInt + 1

  override def getNthZeroBased(n: Int): Option[T] = {
    assert(n >= 0, s"index of requested element must be greater or equal 0, but is $n")
    if (n > totalValueCount - 1) return None
    Some(start + stepSize * v.fromInt(n))
  }

  override def getNFromPositionZeroBased(position: Int, n: Int): Seq[T] = {
    assert(position >= 0, s"start index must be greater or equal 0, but is $position")
    assert(n >= 0, s"number of requested elements must be greater or equal 0, but is $n")
    if (position > totalValueCount - 1) return Seq.empty
    (position until (position + n)).flatMap(x => getNthZeroBased(x)).toList
  }

  override def getAll: Seq[T] = {
    (0 until totalValueCount).flatMap(x => getNthZeroBased(x)).toList
  }
}
