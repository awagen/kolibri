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

import de.awagen.kolibri.definitions.usecase.searchopt.metrics.JudgementValidation.{EXIST_JUDGEMENTS, EXIST_RESULTS, JudgementValidation}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MissingValueStrategy.MissingValueStrategy
import de.awagen.kolibri.datatypes.reason.ComputeFailReason


object JudgementValidation extends Enumeration {
  type JudgementValidation = Val

  protected case class Val(reason: ComputeFailReason, function: Function[Seq[Option[Double]], Boolean]) extends super.Val {}

  def atMostFractionMissingJudgements(fraction: Double): Function[Seq[Option[Double]], Boolean] = {
    x => {
      val missing: Int = x.count(y => y.isEmpty)
      val total: Int = x.size
      if (total > 0) missing.asInstanceOf[Double] / total.asInstanceOf[Double] <= fraction else true
    }
  }

  val EXIST_RESULTS: JudgementValidation = Val(ComputeFailReason.NO_RESULTS, x => x.nonEmpty)
  val EXIST_JUDGEMENTS: JudgementValidation = Val(ComputeFailReason("NO_JUDGEMENTS"), x => x.count(y => y.nonEmpty) > 0)
  val JUDGEMENTS_MISSING_AT_MOST_10PERCENT: JudgementValidation = Val(ComputeFailReason("TOO_FEW_JUDGEMENTS"), atMostFractionMissingJudgements(0.1))

}


object JudgementHandlingStrategy {

  val EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS: JudgementHandlingStrategy = JudgementHandlingStrategy(
    Seq(
      EXIST_RESULTS,
      EXIST_JUDGEMENTS),
    MissingValueStrategy.AS_ZEROS)
}

case class JudgementHandlingStrategy(validations: Seq[JudgementValidation], handling: MissingValueStrategy) {

  def validateAndReturnFailed(seq: Seq[Option[Double]]): Seq[JudgementValidation] = {
    validations.filter(x => !x.function.apply(seq))
  }

  def isValid(seq: Seq[Option[Double]]): Boolean = {
    validations.takeWhile(x => x.function.apply(seq)).size == validations.size
  }

  def extractValues(seq: Seq[Option[Double]]): Seq[Double] = {
    handling.function.apply(seq)
  }
}



