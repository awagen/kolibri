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

package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.io.json.ReaderJsonProtocol._
import de.awagen.kolibri.base.io.reader.{Reader, LocalResourceFileReader}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._

class ReaderJsonProtocolSpec extends UnitTestSpec {

  "QueryProviderJsonProtocol" must {

    "parse QueryProvider json" in {
      // given
      val json = """{"type": "LOCAL_FILE_READER", "delimiter": "/", "position": 1, "encoding": "UTF-8", "fromClasspath": true}""".parseJson
      val expectedValue: Reader[String, Seq[String]] = LocalResourceFileReader(
        delimiterAndPosition = Some(("/", 1)),
        fromClassPath = true,
        encoding = "UTF-8"
      )
      // when
      val result = json.convertTo[Reader[String, Seq[String]]]
      // then
      result mustBe expectedValue
    }

  }

}
