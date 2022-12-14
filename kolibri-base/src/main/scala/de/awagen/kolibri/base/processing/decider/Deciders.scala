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


package de.awagen.kolibri.base.processing.decider

import akka.actor.{OneForOneStrategy, SupervisorStrategy}
import akka.stream.Supervision
import org.slf4j.{Logger, LoggerFactory}

object Deciders {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val allResumeDecider: Supervision.Decider = {
    e: Throwable =>
      logger.warn(s"throwable in stream execution: $e - resuming")
      e.printStackTrace()
      Supervision.Resume
  }

  def restartDecider(maxNrOfRetries: Int, loggingEnabled: Boolean): OneForOneStrategy = OneForOneStrategy(maxNrOfRetries = maxNrOfRetries, loggingEnabled = loggingEnabled) {
    case _ => SupervisorStrategy.Restart
  }

}
