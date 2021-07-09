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

import de.awagen.kolibri.base.usecase.searchopt.metrics.{MetricsCalculation, MetricsEvaluation}
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import JudgementProviderFactoryJsonProtocol.JudgementProviderFactoryDoubleFormat
import MetricsCalculationJsonProtocol.metricsCalculationFormat
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object MetricsEvaluationJsonProtocol extends DefaultJsonProtocol {

  implicit val metricsEvaluationFormat: RootJsonFormat[MetricsEvaluation] = jsonFormat(
    (judgementProviderFactory: JudgementProviderFactory[Double], metricsCalculation: MetricsCalculation) => MetricsEvaluation
      .apply(judgementProviderFactory, metricsCalculation),
    "judgementProviderFactory",
    "metricsCalculation"
  )

}
