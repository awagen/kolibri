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
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.JsonSelectorPathRegularExpressions.{recursiveAndPlainSelectorKeysToSelector, recursiveAndRecursiveSelectorKeysToSelector}
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors._
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{NamedAndTypedSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.namedTypesFormat
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{ConditionalFields, NestedFieldSeqStructDef, RegexStructDef, SeqRegexStructDef, StringChoiceStructDef, StringConstantStructDef}
import de.awagen.kolibri.datatypes.types.JsonTypeCast.JsonTypeCast
import play.api.libs.json.DefaultReads
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, RootJsonFormat, enrichAny}

import scala.collection.immutable


object JsonSelectorJsonProtocol extends DefaultJsonProtocol with DefaultReads {

  val PLAIN_SELECTOR_SINGLE_TYPE = "SINGLE_KEY"
  val PLAIN_SELECTOR_PLAIN_PATH_TYPE = "PLAIN_PATH"

  val SINGLEREC_TYPE = "SINGLEREC"
  val PLAINREC_TYPE = "PLAINREC"
  val RECPLAIN_TYPE = "RECPLAIN"
  val RECREC_TYPE = "RECREC"

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

  implicit val jsonSingleKeyFormat: RootJsonFormat[SingleKeySelector] = jsonFormat(
    (key: String) => SingleKeySelector(key),
    "key")

  implicit object PlainSelectorFormat extends JsonFormat[PlainSelector] with WithStructDef {
    val TYPE_KEY = "type"
    val KEY_KEY = "key"
    val KEYS_KEY = "keys"

    override def read(json: JsValue): PlainSelector = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case PLAIN_SELECTOR_SINGLE_TYPE =>
          SingleKeySelector(fields(KEY_KEY).convertTo[String])
        case PLAIN_SELECTOR_PLAIN_PATH_TYPE =>
          PlainPathSelector(fields(KEYS_KEY).convertTo[Seq[String]])
      }
    }

    // TODO
    override def write(obj: PlainSelector): JsValue = """{}""".toJson

    // TODO: make regexes more specific
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              PLAIN_SELECTOR_SINGLE_TYPE,
              PLAIN_SELECTOR_PLAIN_PATH_TYPE
            )),
            required = true)
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            immutable.Map(
              PLAIN_SELECTOR_SINGLE_TYPE -> Seq(
                FieldDef(StringConstantStructDef(KEY_KEY), RegexStructDef(".*".r), required = true)
              ),
              PLAIN_SELECTOR_PLAIN_PATH_TYPE -> Seq(
                FieldDef(StringConstantStructDef(KEYS_KEY), SeqRegexStructDef(".*".r), required = true)
              )
            )
          )
        )
      )
    }
  }


  implicit object JsValueSeqSelectorFormat extends JsonFormat[JsValueSeqSelector] with WithStructDef {
    val TYPE_KEY = "type"
    val PATH_KEY = "path"
    val REC_PATH_KEY = "recPath"
    val REC_PATH_1_KEY = "recPath1"
    val REC_PATH_2_KEY = "recPath2"
    val PLAIN_PATH_KEY = "plainPath"

    override def read(json: JsValue): JsValueSeqSelector = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        // single recursive selector, e.g recursively on json root without any selectors before
        case SINGLEREC_TYPE =>
          val path: String = fields(PATH_KEY).convertTo[String]
          val selectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(path)
          if (selectorKeys.size > 1) {
            throw new RuntimeException("value selector single recursive, yet selectorKeys have more than one element")
          }
          RecursiveSelector(selectorKeys.head)
        // some plain path selectors followed by recursive selector at the end
        case PLAINREC_TYPE =>
          val path: String = fields(PATH_KEY).convertTo[String]
          val selectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(path)
          PlainAndRecursiveSelector(selectorKeys.last, selectorKeys.slice(0, selectorKeys.length - 1): _*)
        // recursive selector (may contain plain path) then mapped to some plain selection (each element from recursive selection)
        case RECPLAIN_TYPE =>
          val recSelectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(fields(REC_PATH_KEY).convertTo[String])
          val plainSelectorKeys: Seq[String] = JsonSelectors.findPlainPathKeys(fields(PLAIN_PATH_KEY).convertTo[String])
          recursiveAndPlainSelectorKeysToSelector(recSelectorKeys, plainSelectorKeys)
        // recursive selector (may contain plain path) then flatMapped to another recursive selector (each element from the first recursive selection,
        // e.g mapping the Seq[JsValue] elements)
        case RECREC_TYPE =>
          val recSelectorKeys1: Seq[String] = JsonSelectors.findRecursivePathKeys(fields(REC_PATH_1_KEY).convertTo[String])
          val recSelectorKeys2: Seq[String] = JsonSelectors.findRecursivePathKeys(fields(REC_PATH_2_KEY).convertTo[String])
          recursiveAndRecursiveSelectorKeysToSelector(recSelectorKeys1, recSelectorKeys2)
      }
      case e => throw DeserializationException(s"Expected a value of type NamedClassType[T] but got value $e")
    }

    // TODO
    override def write(obj: JsValueSeqSelector): JsValue = {
      """{}""".toJson
    }

    // TODO: improve the wildcard regexes to suite actual valid paths
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              SINGLEREC_TYPE,
              PLAINREC_TYPE,
              RECPLAIN_TYPE,
              RECREC_TYPE
            )),
            required = true)
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            immutable.Map(
              SINGLEREC_TYPE -> Seq(
                FieldDef(StringConstantStructDef(PATH_KEY), RegexStructDef(".*".r), required = true)
              ),
              PLAINREC_TYPE -> Seq(
                FieldDef(StringConstantStructDef(PATH_KEY), RegexStructDef(".*".r), required = true)
              ),
              RECPLAIN_TYPE -> Seq(
                FieldDef(StringConstantStructDef(REC_PATH_KEY), RegexStructDef(".*".r), required = true),
                FieldDef(StringConstantStructDef(PLAIN_PATH_KEY), RegexStructDef(".*".r), required = true)
              ),
              RECREC_TYPE -> Seq(
                FieldDef(StringConstantStructDef(REC_PATH_1_KEY), RegexStructDef(".*".r), required = true),
                FieldDef(StringConstantStructDef(REC_PATH_2_KEY), RegexStructDef(".*".r), required = true)
              )
            )
          )
        )
      )
    }
  }

  implicit object SelectorFormat extends JsonFormat[Selector[Any]] with WithStructDef {
    val SELECTOR_KEY = "selector"

    override def read(json: JsValue): Selector[Any] = json match {
      case spray.json.JsObject(fields) if fields.contains(SELECTOR_KEY) => fields(SELECTOR_KEY).convertTo[String] match {
        case e => JsonSelectors.JsonSelectorPathRegularExpressions.pathToSelector(e).get
      }
    }

    // TODO
    override def write(obj: Selector[Any]): JsValue = """{}""".toJson

    // TODO: make regex more specific
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(SELECTOR_KEY), RegexStructDef(".*".r), required = true)
        ),
        Seq.empty
      )
    }
  }

  implicit object NamedAndTypedSelectorFormat extends JsonFormat[NamedAndTypedSelector[Any]] with WithStructDef {
    val SELECTOR_KEY = "selector"
    val NAME_KEY = "name"
    val CAST_TYPE_KEY = "castType"
    override def read(json: JsValue): NamedAndTypedSelector[Any] = json match {
      case spray.json.JsObject(fields) =>
        val selectorPath = fields(SELECTOR_KEY).convertTo[String]
        val name = fields(NAME_KEY).convertTo[String]
        val castType = fields(CAST_TYPE_KEY).convertTo[JsonTypeCast]
        val selector: Option[Selector[_]] = JsonSelectors.JsonSelectorPathRegularExpressions.pathToSelector(selectorPath)
        val namedSelectorOpt: Option[NamedAndTypedSelector[Any]] = selector.flatMap({
          case e: PlainSelector =>
            Some(TypedJsonSingleValueSelector(name, e, castType))
          case e: JsValueSeqSelector =>
            Some(TypedJsonSeqSelector(name, e, castType))
          case _ => None
        })
        if (namedSelectorOpt.isEmpty) throw new RuntimeException(s"could not parse json '$json' to named selector")
        namedSelectorOpt.get
      case _ => throw new RuntimeException(s"could not parse json '$json' to named selector")
    }

    // TODO
    override def write(obj: NamedAndTypedSelector[Any]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(SELECTOR_KEY), RegexStructDef(".*".r), required = true),
          FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
          FieldDef(
            StringConstantStructDef(CAST_TYPE_KEY),
            de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.namedTypesFormat.structDef,
            required = true
          ),
        ),
        Seq.empty
      )
    }
  }

}
