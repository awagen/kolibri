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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

object DateUtils {

  val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"
  val DATE_NO_TIME_PATTERN = "yyyy-MM-dd"
  val DATE_TIME_ZONE: DateTimeZone = DateTimeZone.UTC
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat
    .forPattern(DATE_PATTERN)
    .withZone(DATE_TIME_ZONE)

  def timeInMillisToFormattedDate(timeInMillis: Long): String = {
    new DateTime(timeInMillis, DATE_TIME_ZONE).toString(DATE_NO_TIME_PATTERN)
  }

  def timeInMillisToFormattedTime(timeInMillis: Long): String = {
    new DateTime(timeInMillis, DATE_TIME_ZONE).toString(DATE_FORMATTER)
  }

  def timeStringToTimeInMillis(timeStr: String): Long = {
    DateTime.parse(timeStr, DATE_FORMATTER).toInstant.getMillis
  }

}
