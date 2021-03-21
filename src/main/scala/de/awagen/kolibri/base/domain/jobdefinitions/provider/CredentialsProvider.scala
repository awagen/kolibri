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

import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}

case class Credentials(username: String, password: String)

trait CredentialsProvider {

  def getCredentials: Credentials

  def getBasicAuthHttpHeader: Authorization = {
    val credentials = getCredentials
    headers.Authorization(BasicHttpCredentials(credentials.username, credentials.password))
  }
}


case class BaseCredentialsProvider(credentials: Credentials) extends CredentialsProvider {
  override def getCredentials: Credentials = credentials
}