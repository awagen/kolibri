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

import akka.util.ByteString
import de.awagen.kolibri.datatypes.utils.StringUtils.{PATTERN_ALPHANUMERIC, extractFirstValueForFieldFromString, extractValuesForFieldFromStringOrdered}

object ByteStringUtils {

  def containsField(byteString: ByteString, field: String): Boolean = {
    byteString.containsSlice(ByteString(s""""$field":"""))
  }

  def extractValuesForFieldFromByteStringOrdered(byteString: ByteString, field: String): Seq[String] = {
    if (!containsField(byteString, field)) return Seq.empty
    extractValuesForFieldFromStringOrdered(byteString.decodeString("UTF-8"), field, PATTERN_ALPHANUMERIC)
  }

  def extractFirstValueForFieldFromByteString(byteString: ByteString, field: String): Option[String] = {
    if (!containsField(byteString, field)) return None
    extractFirstValueForFieldFromString(byteString.decodeString("UTF-8"), field, PATTERN_ALPHANUMERIC)
  }


}
