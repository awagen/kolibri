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

object Parameter {

  def listAsQueryString(options: Seq[Parameter]): String = {
    options.to(LazyList).map(x => x.asQueryString()).toArray.mkString("&")
  }

  def listAsParameterMap(options: Seq[Parameter]): Map[String, Seq[String]] = {
    var map = Map.empty[String, Seq[String]]
    options.foreach(x => map += (x.parameterName -> (map.getOrElse[Seq[String]](x.parameterName, Seq.empty) :+ x.value)))
    map
  }

}

case class Parameter(parameterName: String, value: String) {

  def asQueryString(): String = {
    s"$parameterName=$value"
  }


}
