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


package de.awagen.kolibri.definitions.format

import scala.util.matching.Regex

object RegexUtils {

  def findParamValueInString(param: String,
                             leftDelimiter: String = "",
                             rightDelimiter: String = "[),]",
                             string: String,
                             defaultValue: String): String = {
    val regex: Regex = s".*$leftDelimiter$param=(.*)$rightDelimiter.*".r
    val foundMatch: Option[Regex.Match] = regex.findFirstMatchIn(string)
    foundMatch.map(m => {
      if (m.groupCount < 1) defaultValue
      else m.group(1)
    }).getOrElse(defaultValue)
  }
}
