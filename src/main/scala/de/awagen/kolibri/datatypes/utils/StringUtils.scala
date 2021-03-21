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

package de.awagen.kolibri.datatypes.utils

import scala.util.matching.Regex

object StringUtils {

  val PATTERN_NUMERIC = "[0-9]+"
  val PATTERN_ALPHANUMERIC = "[A-Za-z0-9]+"
  val PATTERN_ALPHA = "[A-Za-z]+"
  val PATTERN_DATE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"
  val PATTERN_FLOAT = "\\d+.\\d+"

  def createRegexForFieldAndPattern(field: String, pattern: String): Regex = {
    s"""\\s*"$field":"{0,1}\\s*($pattern)"{0,1}""".r
  }

  def extractField(string: String, field: String, pattern: String): Option[(String, String)] = {
    val regex = createRegexForFieldAndPattern(field, pattern)
    string match {
      case regex(group) => Some((s"$field", s"$group"))
      case _ => None
    }
  }

  def extractValuesForFieldFromStringOrdered(string: String, field: String, pattern: String): Seq[String] = {
    val regex = createRegexForFieldAndPattern(field, pattern)
    regex.findAllIn(string).to(LazyList).flatMap {
      case regex(group) => Some(s"$group")
      case _ => None
    }.toList
  }

  def extractFirstValueForFieldFromString(string: String, field: String, pattern: String): Option[String] = {
    val regex = createRegexForFieldAndPattern(field, pattern)
    regex.findFirstMatchIn(string) match {
      case Some(i) => Some(i.group(1))
      case _ => None
    }
  }
}
