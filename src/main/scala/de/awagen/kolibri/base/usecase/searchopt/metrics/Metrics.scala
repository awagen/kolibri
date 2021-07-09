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

package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.datatypes.reason.ComputeFailReason


object Metrics extends Enumeration {
  type Metrics = Val

  case class Val(name: String, function: Function[Seq[Double], Either[Seq[ComputeFailReason], Double]], firstKUsed: Int) extends super.Val {}

  val DCG_10: Val = Val("DCG_10", MetricFunctions.dcgAtK(10), 10)
  val NDCG_10: Val = Val("NDCG_10", MetricFunctions.ndcgAtK(10), 10)
  val PRECISION_4: Val = Val("PRECISION_4", MetricFunctions.precisionAtK(4, 0.1), 4)
  val ERR: Val = Val("ERR", MetricFunctions.errAtK(10, 3.0), 10)
}

