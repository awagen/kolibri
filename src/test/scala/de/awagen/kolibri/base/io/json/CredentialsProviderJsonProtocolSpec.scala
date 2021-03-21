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

import de.awagen.kolibri.base.domain.jobdefinitions.provider.{BaseCredentialsProvider, Credentials, CredentialsProvider}
import de.awagen.kolibri.base.io.json.CredentialsProviderJsonProtocol._
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._


class CredentialsProviderJsonProtocolSpec extends UnitTestSpec {

  "CredentialsProviderJsonProtocol" must {

    "parse credentials provider" in {
      // given
      val json: JsValue =
        """{"username": "jo", "password": "hey"}""".parseJson
      val expectedValue: BaseCredentialsProvider = BaseCredentialsProvider(
        credentials = Credentials(
          username = "jo",
          password = "hey"
        )
      )
      // when
      val provider: CredentialsProvider = json.convertTo[CredentialsProvider]
      // then
      provider mustBe expectedValue
    }

  }

}
