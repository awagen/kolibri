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

package de.awagen.kolibri.fleet.zio.config

object EnvVariableKeys extends Enumeration {
  type EnvVariableKeys = Val

  protected case class Val(key: String, default: String) extends super.Val {
    lazy val value: String = sys.env.getOrElse(key, default)
  }

  val PROFILE: Val = Val("PROFILE", "local")
  val HTTP_SERVER_PORT: Val = Val("HTTP_SERVER_PORT", "8080")
}
