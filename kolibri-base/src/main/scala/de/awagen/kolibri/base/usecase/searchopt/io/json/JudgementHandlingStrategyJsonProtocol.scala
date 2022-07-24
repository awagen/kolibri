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

package de.awagen.kolibri.base.usecase.searchopt.io.json

import de.awagen.kolibri.base.usecase.searchopt.io.json.EnumerationJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementHandlingStrategy
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{GenericSeqStructDef, NestedFieldSeqStructDef, StringConstantStructDef}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object JudgementHandlingStrategyJsonProtocol extends DefaultJsonProtocol with WithStructDef {

  val VALIDATIONS_KEY = "validations"
  val HANDLING_KEY = "handling"

  implicit val judgementHandlingStrategyFormat: RootJsonFormat[JudgementHandlingStrategy] =
    jsonFormat2(JudgementHandlingStrategy.apply)

  override def structDef: JsonStructDefs.StructDef[_] = {
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(VALIDATIONS_KEY),
          GenericSeqStructDef(
            EnumerationJsonProtocol.judgementValidationFormat.structDef
          ),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(HANDLING_KEY),
          EnumerationJsonProtocol.missingValueStrategyFormat.structDef,
          required = true
        )
      ),
      Seq.empty
    )
  }
}
