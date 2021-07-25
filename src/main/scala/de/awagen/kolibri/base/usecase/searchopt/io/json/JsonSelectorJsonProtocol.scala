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


package de.awagen.kolibri.base.usecase.searchopt.io.json

import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.RecursiveValueSelector
import play.api.libs.json.DefaultReads
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object JsonSelectorJsonProtocol extends DefaultJsonProtocol with DefaultReads {

  implicit val jsonRecursiveStringSelectorFormat: RootJsonFormat[RecursiveValueSelector[String]] =
    jsonFormat(
      (plainSelectorKeys: Seq[String], recursiveSelectorKey: String) => RecursiveValueSelector[String](plainSelectorKeys, recursiveSelectorKey),
      "plainSelectorKeys",
      "recursiveSelectorKey"
    )

  implicit val jsonRecursiveBooleanSelectorFormat: RootJsonFormat[RecursiveValueSelector[Boolean]] =
    jsonFormat(
      (plainSelectorKeys: Seq[String], recursiveSelectorKey: String) => RecursiveValueSelector[Boolean](plainSelectorKeys, recursiveSelectorKey),
      "plainSelectorKeys",
      "recursiveSelectorKey"
    )
}
