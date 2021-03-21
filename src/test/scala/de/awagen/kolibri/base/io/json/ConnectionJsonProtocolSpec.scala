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

import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.provider.{BaseCredentialsProvider, Credentials}
import de.awagen.kolibri.base.io.json.ConnectionJsonProtocol._
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._


class ConnectionJsonProtocolSpec extends UnitTestSpec {

  "ConnectionJsonProtocol" must {

    "parse Connection" in {
      // given
      val json: JsValue =
        """{"host": "testhost", "port": 80, "useHttps": true, "credentialsProvider": {"username": "jo", "password": "hey"}}""".parseJson
      // when
      val connection: Connection = json.convertTo[Connection]
      val expectedConnection = Connection(
        host = "testhost",
        port = 80,
        useHttps = true,
        credentialsProvider = Some(BaseCredentialsProvider(
          credentials = Credentials(
            username = "jo",
            password = "hey"
          )
        ))
      )
      // then
      connection mustBe expectedConnection
    }
  }

}
