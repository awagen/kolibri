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

package de.awagen.kolibri.fleet.akka.config

object EnvVariableKeys extends Enumeration {
  type EnvVariableKeys = Val

  protected case class Val(key: String, default: String) extends super.Val {
    lazy val value: String = sys.env.getOrElse(key, default)
  }

  val CLUSTER_NODE_HOST: Val = Val("CLUSTER_NODE_HOST", "127.0.0.1")
  val CLUSTER_NODE_PORT: Val = Val("CLUSTER_NODE_PORT", "")
  val HTTP_SERVER_INTERFACE: Val = Val("HTTP_SERVER_INTERFACE", "127.0.0.1")
  val HTTP_SERVER_PORT: Val = Val("HTTP_SERVER_PORT", "8000")
  val MANAGEMENT_HOST: Val = Val("MANAGEMENT_HOST", "0.0.0.0")
  val MANAGEMENT_PORT: Val = Val("MANAGEMENT_PORT", "8558")
  val NODE_ROLES: Val = Val("ROLES", "compute")
  val PROFILE: Val = Val("PROFILE", "local")
  val IS_SINGLENODE: Val = Val("SINGLE_NODE", "false")
  val POD_IP: Val = Val("POD_IP", "")
}
