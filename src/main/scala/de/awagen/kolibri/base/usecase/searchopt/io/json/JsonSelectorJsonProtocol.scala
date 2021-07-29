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

import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors._
import play.api.libs.json.DefaultReads
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, RootJsonFormat, enrichAny}


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

  implicit val jsonPlainPathFormat: RootJsonFormat[PlainPathSelector] = jsonFormat(
    (plainSelectorKeys: Seq[String]) => PlainPathSelector(plainSelectorKeys),
    "plainSelectorKeys")

  implicit object JsValueSelectorFormat extends JsonFormat[JsValueSeqSelector] {
    override def read(json: JsValue): JsValueSeqSelector = json match {
      case spray.json.JsObject(fields) if fields.contains("type") => fields("type").convertTo[String] match {
        // single recursive selector, e.g recursively on json root without any selectors before
        case "SINGLEREC" =>
          val path: String = fields("path").convertTo[String]
          val selectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(path)
          if (selectorKeys.size > 1) {
            throw new RuntimeException("value selector single recursive, yet selectorKeys have more than one element")
          }
          RecursiveSelector(selectorKeys.head)
        // some plain path selectors followed by recursive selector at the end
        case "PLAINREC" =>
          val path: String = fields("path").convertTo[String]
          val selectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(path)
          PlainAndRecursiveSelector(selectorKeys.last, selectorKeys.slice(0, selectorKeys.length - 1): _*)
        // recursive selector (may contain plain path) then mapped to some plain selection (each element from recursive selection)
        case "RECPLAIN" =>
          val recSelectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(fields("recPath").convertTo[String])
          val plainSelectorKeys: Seq[String] = JsonSelectors.findPlainPathKeys(fields("plainPath").convertTo[String])
          val recursive = PlainAndRecursiveSelector(recSelectorKeys.last, recSelectorKeys.slice(0, recSelectorKeys.length - 1): _*)
          val plain = PlainPathSelector(plainSelectorKeys)
          RecursiveAndPlainSelector(recursive, plain)
        // recursive selector (may contain plain path) then flatMapped to another recursive selector (each element from the first recursive selection,
        // e.g mapping the Seq[JsValue] elements)
        case "RECREC" =>
          val recSelectorKeys1: Seq[String] = JsonSelectors.findRecursivePathKeys(fields("recPath1").convertTo[String])
          val recSelectorKeys2: Seq[String] = JsonSelectors.findRecursivePathKeys(fields("recPath2").convertTo[String])
          val recursive1 = PlainAndRecursiveSelector(recSelectorKeys1.last, recSelectorKeys1.slice(0, recSelectorKeys1.length - 1): _*)
          val recursive2 = PlainAndRecursiveSelector(recSelectorKeys2.last, recSelectorKeys2.slice(0, recSelectorKeys2.length - 1): _*)
          RecursiveAndRecursiveSelector(recursive1, recursive2)
      }
      case e => throw DeserializationException(s"Expected a value of type NamedClassType[T] but got value $e")
    }

    // TODO
    override def write(obj: JsValueSeqSelector): JsValue = {
      s"""{}""".toJson
    }
  }

}
