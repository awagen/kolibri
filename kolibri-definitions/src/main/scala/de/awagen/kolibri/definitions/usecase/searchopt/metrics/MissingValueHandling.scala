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

package de.awagen.kolibri.definitions.usecase.searchopt.metrics

object MissingValueStrategy extends Enumeration {
  type MissingValueStrategy = Val

  protected case class Val(function: Function[Seq[Option[Double]], Seq[Double]]) extends super.Val {}

  val AS_ZEROS: MissingValueStrategy = Val(MissingValueFunctions.treatMissingAsConstant(0.0D))
  val AS_AVG_OF_NON_MISSING: MissingValueStrategy = Val(MissingValueFunctions.treatMissingAsAvgOfNonMissing)

}

object MissingValueFunctions {

  def treatMissingAsAvgOfNonMissing: Function[Seq[Option[Double]], Seq[Double]] = {
    x => {
      val summed: Double = x.filter(y => y.nonEmpty).map(y => y.get).sum
      val totalValues = x.count(y => y.nonEmpty)
      val avg = if (totalValues > 0) summed / totalValues else 0
      x.map(y => y.getOrElse(avg))
    }
  }

  def treatMissingAsConstant(constant: Double): Function[Seq[Option[Double]], Seq[Double]] = {
    x => x.map(y => y.getOrElse(constant))
  }

}