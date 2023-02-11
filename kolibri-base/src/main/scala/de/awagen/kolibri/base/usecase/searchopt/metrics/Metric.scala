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

import de.awagen.kolibri.base.io.json.MetricFunctionJsonProtocol
import de.awagen.kolibri.base.io.json.MetricFunctionJsonProtocol.MetricFunction
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}

object Metric extends WithStructDef {

  val NAME_KEY = "name"
  val FUNCTION_KEY = "function"

  override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
    Seq(
      FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
      FieldDef(StringConstantStructDef(FUNCTION_KEY), MetricFunctionJsonProtocol.structDef, required = true),
    ),
    Seq.empty
  )
}

case class Metric(name: String, function: MetricFunction)
