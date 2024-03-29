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

package de.awagen.kolibri.datatypes.reason

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object ComputeFailReason {

  val ZERO_DENOMINATOR: ComputeFailReason = ComputeFailReason("ZERO_DENOMINATOR")
  val NO_RESULTS: ComputeFailReason = ComputeFailReason("NO_RESULTS")
  val FAILED_HISTOGRAM: ComputeFailReason = ComputeFailReason("FAILED_HISTOGRAM")

  def missingDataKeyFailReason(key: String): ComputeFailReason = ComputeFailReason(s"MISSING_DATA_KEY-$key")

}

case class ComputeFailReason(description: String) extends KolibriSerializable
