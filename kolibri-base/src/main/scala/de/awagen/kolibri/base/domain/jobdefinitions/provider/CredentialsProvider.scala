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

package de.awagen.kolibri.base.domain.jobdefinitions.provider

import java.nio.charset.StandardCharsets
import java.util.Base64

case class Credentials(username: String, password: String)

trait CredentialsProvider {

  val AUTH_HEADER_KEY = "Authorization"

  def getCredentials: Credentials

  def getBasicAuthHttpHeader: (String, String) = {
    val credentials = getCredentials
    val authString = Base64.getEncoder
      .encodeToString(s"${credentials.username}:${credentials.password}".getBytes(StandardCharsets.UTF_8))
    (AUTH_HEADER_KEY, s"Basic $authString")
  }
}


case class BaseCredentialsProvider(credentials: Credentials) extends CredentialsProvider {
  override def getCredentials: Credentials = credentials
}