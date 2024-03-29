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

package de.awagen.kolibri.definitions.utils

import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.reflect.runtime.universe._

object TestHelper {

  def createRequestTemplateWithParams(parameters: Map[String, Seq[String]]): RequestTemplate = {
    RequestTemplate("testpath", parameters, headers = Seq.empty, body = "", httpMethod = "GET")
  }

  case class TestTypedClass[T: TypeTag](name: String) extends ClassTyped[T]

}
