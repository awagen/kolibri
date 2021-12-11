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

package de.awagen.kolibri.base.usecase.searchopt.provider

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.datatypes.AtomicMapPromiseStore
import org.slf4j.{Logger, LoggerFactory}

object FileBasedJudgementRepository extends AtomicMapPromiseStore[String, JudgementProvider[Double]] {

  private[this] val logger: Logger = LoggerFactory.getLogger(FileBasedJudgementRepository.toString)

  override def calculateValue(key: String): JudgementProvider[Double] = {
    logger.debug("calculateValue")
    AppConfig.filepathToJudgementProvider.apply(key)
  }
}
