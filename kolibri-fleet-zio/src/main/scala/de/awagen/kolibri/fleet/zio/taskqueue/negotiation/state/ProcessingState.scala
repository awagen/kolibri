/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus.ProcessingStatus
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, DateTimeFormatterBuilder}


object ProcessingStatus extends Enumeration {
  type ProcessingStatus = Value
  val QUEUED: Value = Value
  val IN_PROGRESS: Value = Value
  val DONE: Value = Value
}

object ProcessingStateUtils {
  val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern(DATE_PATTERN)

  def timeInMillisToFormattedTime(timeInMillis: Long): String = {
    new DateTime(timeInMillis).toString(DATE_FORMATTER)
  }

}

case class ProcessingState(jobId: String,
                           batchNr: Int,
                           status: ProcessingStatus,
                           numItemsTotal: Int,
                           numItemsProcessed: Int,
                           processingNode: String,
                           lastUpdate: String) {



}
