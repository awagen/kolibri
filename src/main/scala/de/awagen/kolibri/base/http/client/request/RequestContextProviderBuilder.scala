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

package de.awagen.kolibri.base.http.client.request

import de.awagen.kolibri.base.http.client.request
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.OrderedValues


class RequestContextProviderBuilder(val groupId: String) {

  private[this] var parameters: Option[OrderedMultiValues] = None
  private[this] var defaultParams: Map[String, Seq[String]] = Map.empty
  private[this] var contextPath: String = ""

  def build: RequestContextProvider = {
    var allParams = GridOrderedMultiValues(Seq.empty[OrderedValues[Any]])
    parameters foreach[Unit] (x => allParams = allParams.addValues(x, prepend = false))
    request.RequestContextProvider(groupId, contextPath, defaultParams, allParams)
  }

  def withContextPath(path: String): RequestContextProviderBuilder = {
    contextPath = path
    this
  }

  def withDefaultParams(params: Map[String,Seq[String]]): RequestContextProviderBuilder = {
    defaultParams = params
    this
  }

  def withParameters(params: OrderedMultiValues): RequestContextProviderBuilder = {
    parameters = Some(params)
    this
  }

}
