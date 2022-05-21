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


package de.awagen.kolibri.base.usecase.searchopt.io.json

object CalculationName extends Enumeration {
  type CalculationName = Val

  case class Val(name: String) extends super.Val

  val IDENTITY = Val("IDENTITY")
  val FIRST_TRUE = Val("FIRST_TRUE")
  val FIRST_FALSE = Val("FIRST_FALSE")
  val TRUE_COUNT = Val("TRUE_COUNT")
  val FALSE_COUNT = Val("FALSE_COUNT")
  val BINARY_PRECISION_TRUE_AS_YES = Val("BINARY_PRECISION_TRUE_AS_YES")
  val BINARY_PRECISION_FALSE_AS_YES = Val("BINARY_PRECISION_FALSE_AS_YES")
}