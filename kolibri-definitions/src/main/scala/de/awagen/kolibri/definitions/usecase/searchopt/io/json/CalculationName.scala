/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.definitions.usecase.searchopt.io.json

import de.awagen.kolibri.datatypes.types.JsonStructDefs.StringChoiceStructDef
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}

object CalculationName extends Enumeration with WithStructDef {
  type CalculationName = Val

  case class Val(name: String) extends super.Val

  val IR_METRICS = Val("IR_METRICS")
  val IDENTITY = Val("IDENTITY")
  val FIRST_TRUE = Val("FIRST_TRUE")
  val FIRST_FALSE = Val("FIRST_FALSE")
  val TRUE_COUNT = Val("TRUE_COUNT")
  val FALSE_COUNT = Val("FALSE_COUNT")
  val BINARY_PRECISION_TRUE_AS_YES = Val("BINARY_PRECISION_TRUE_AS_YES")
  val BINARY_PRECISION_FALSE_AS_YES = Val("BINARY_PRECISION_FALSE_AS_YES")
  val STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM = Val("STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM")

  val JACCARD_SIMILARITY = Val("JACCARD_SIMILARITY")

  override def structDef: JsonStructDefs.StructDef[_] =
    StringChoiceStructDef(
      Seq(
        IR_METRICS.name,
        IDENTITY.name,
        FIRST_TRUE.name,
        FIRST_FALSE.name,
        TRUE_COUNT.name,
        FALSE_COUNT.name,
        BINARY_PRECISION_TRUE_AS_YES.name,
        BINARY_PRECISION_FALSE_AS_YES.name,
        STRING_SEQUENCE_VALUE_OCCURRENCE_HISTOGRAM.name
      )
    )
}