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

import de.awagen.kolibri.base.io.json.MetricJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.io.json.JudgementHandlingStrategyJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{JudgementHandlingStrategy, Metric, MetricsCalculation}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{GenericSeqStructDef, NestedFieldSeqStructDef, StringConstantStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object MetricsCalculationJsonProtocol extends DefaultJsonProtocol with WithStructDef {

  val METRICS_KEY = "metrics"
  val JUDGEMENT_HANDLING_KEY = "judgementHandling"

  implicit val metricsCalculationFormat: RootJsonFormat[MetricsCalculation] = jsonFormat(
    (metrics: Seq[Metric], judgementHandling: JudgementHandlingStrategy) => MetricsCalculation.apply(metrics, judgementHandling),
    METRICS_KEY,
    JUDGEMENT_HANDLING_KEY
  )

  override def structDef: JsonStructDefs.StructDef[_] =
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(METRICS_KEY),
          GenericSeqStructDef(Metric.structDef),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(JUDGEMENT_HANDLING_KEY),
          JudgementHandlingStrategyJsonProtocol.structDef,
          required = true
        )
      ),
      Seq.empty
    )

}
