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


package de.awagen.kolibri.datatypes.io.json

import org.slf4j.{Logger, LoggerFactory}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

object TimeStampJsonProtocol extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val dateFormatPatternString: String = "yyyy-MM-dd hh:mm:ss.SSS"
  val dateFormat: SimpleDateFormat = new SimpleDateFormat(dateFormatPatternString)

  def convertStringToTimestamp(str: String): Timestamp = {
    val parsedDate: Date = dateFormat.parse(str)
    new Timestamp(parsedDate.getTime)
  }

  implicit object TimeStampFormat extends JsonFormat[Timestamp] {
    override def read(json: JsValue): Timestamp = {
      try {
        convertStringToTimestamp(json.convertTo[String])
      }
      catch {
        case _: Exception =>
          logger.warn(s"Expected a value in timestamp format '$dateFormatPatternString' but got value '$json', " +
            s" falling back to current time stamp")
          new Timestamp(new Date().getTime)
      }
    }

    override def write(obj: Timestamp): JsValue = {
      JsString(obj.toString)
    }
  }


}
