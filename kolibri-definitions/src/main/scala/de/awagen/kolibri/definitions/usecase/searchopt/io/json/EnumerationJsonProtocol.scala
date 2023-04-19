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

package de.awagen.kolibri.definitions.usecase.searchopt.io.json

import de.awagen.kolibri.definitions.usecase.searchopt.metrics.JudgementValidation.{EXIST_JUDGEMENTS, EXIST_RESULTS, JUDGEMENTS_MISSING_AT_MOST_10PERCENT, JudgementValidation}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MissingValueStrategy.{AS_AVG_OF_NON_MISSING, AS_ZEROS, MissingValueStrategy}
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{JudgementValidation, MissingValueStrategy}
import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.EnumerationProtocol
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.types.JsonStructDefs.StringChoiceStructDef
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue}


object EnumerationJsonProtocol extends DefaultJsonProtocol {

  implicit object missingValueStrategyFormat extends EnumerationProtocol[MissingValueStrategy] with WithStructDef {
    override def read(json: JsValue): MissingValueStrategy = {
      json match {
        case JsString(txt) => MissingValueStrategy.withName(txt).asInstanceOf[MissingValueStrategy]
        case e => throw DeserializationException(s"Expected a value from MissingValueStrategy but got value $e")
      }
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      StringChoiceStructDef(Seq(
        AS_ZEROS.toString,
        AS_AVG_OF_NON_MISSING.toString()
      ))
    }
  }

  implicit object judgementValidationFormat extends EnumerationProtocol[JudgementValidation] with WithStructDef {
    override def read(json: JsValue): JudgementValidation = {
      json match {
        case JsString(txt) => JudgementValidation.withName(txt).asInstanceOf[JudgementValidation]
        case e => throw DeserializationException(s"Expected a value from JudgementValidation but got value $e")
      }
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      StringChoiceStructDef(Seq(
        EXIST_RESULTS.toString,
        EXIST_JUDGEMENTS.toString,
        JUDGEMENTS_MISSING_AT_MOST_10PERCENT.toString
      ))
    }
  }

}
