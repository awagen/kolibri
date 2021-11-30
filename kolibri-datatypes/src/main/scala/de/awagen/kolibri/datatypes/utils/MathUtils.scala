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

package de.awagen.kolibri.datatypes.utils

object MathUtils extends Numeric.ExtraImplicits with Ordering.ExtraImplicits {

  def equalWithPrecision[T](x: T, y: T, precision: T)(implicit ev: Numeric[T]): Boolean = {
    if ((x - y).abs < precision) true else false
  }

  def equalWithPrecision[T](x: Seq[T], y: Seq[T], precision: T)(implicit ev: Numeric[T]): Boolean = {
    if (x.size != y.size) return false
    x.indices.forall(i => equalWithPrecision(x(i), y(i), precision))
  }

  def seqEqualWithPrecision[T](x: Seq[Seq[T]], y: Seq[Seq[T]], precision: T)(implicit ev: Numeric[T]): Boolean = {
    if (x.size != y.size) return false
    if (!x.indices.forall(i => x(i).size == y(i).size)) return false
    x.indices.forall(i => x(i).indices.forall(j => equalWithPrecision(x(i)(j), y(i)(j), precision)))
  }

}
