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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.provider.WeightProviders.{ConstantWeightProvider, FileBasedStringIdentifierWeightProvider, WeightProvider}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.storage.io.reader.Reader
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat, enrichAny}

object WeightProviderJsonProtocol extends DefaultJsonProtocol {

  val TYPE_CONSTANT = "CONSTANT"
  val TYPE_FROM_PER_QUERY_FILE = "FROM_PER_QUERY_FILE"
  val PARAM_FILEPATH = "filePath"
  val PARAM_TYPE = "type"
  val PARAM_WEIGHT = "weight"
  val PARAM_QUERY_PARAM = "queryParam"
  val PARAM_REMOVE_PREFIX = "removePrefix"
  val PARAM_REMOVE_SUFFIX = "removeSuffix"
  val PARAM_COLUMN_DELIMITER = "columnDelimiter"
  val PARAM_KEY_COLUMN = "keyColumn"
  val PARAM_WEIGHT_COLUMN = "weightColumn"
  val PARAM_DEFAULT_VALUE = "defaultValue"

  case class StringWeightProviderFormat(reader: Reader[String, Seq[String]]) extends RootJsonFormat[WeightProvider[String]] {
    override def read(json: JsValue): WeightProvider[String] = json match {
      case spray.json.JsObject(fields) => fields(PARAM_TYPE).convertTo[String] match {
        case TYPE_CONSTANT =>
          ConstantWeightProvider(fields(PARAM_WEIGHT).convertTo[Double])
        case TYPE_FROM_PER_QUERY_FILE =>
          FileBasedStringIdentifierWeightProvider(
            reader,
            fields(PARAM_FILEPATH).convertTo[String],
            x => x.stripPrefix(fields(PARAM_REMOVE_PREFIX).convertTo[String])
              .stripSuffix(fields(PARAM_REMOVE_SUFFIX).convertTo[String]),
            fields(PARAM_COLUMN_DELIMITER).convertTo[String],
            fields(PARAM_KEY_COLUMN).convertTo[Int],
            fields(PARAM_WEIGHT_COLUMN).convertTo[Int],
            fields(PARAM_DEFAULT_VALUE).convertTo[Double]
          )
        case _ =>
          throw new IllegalArgumentException(s"Could not parse WeightProvider from value: ${json}")
      }
    }

    override def write(obj: WeightProvider[String]): JsValue = """{}""".toJson

  }

  object StringWeightProviderFormat extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(PARAM_TYPE),
            StringChoiceStructDef(Seq(
              TYPE_CONSTANT,
              TYPE_FROM_PER_QUERY_FILE
            )),
            required = true
          )
        ),
        Seq(
          ConditionalFields(
            PARAM_TYPE,
            Map(
              TYPE_CONSTANT -> Seq(
                FieldDef(
                  StringConstantStructDef(PARAM_WEIGHT),
                  DoubleStructDef,
                  required = true
                )
              ),
              TYPE_FROM_PER_QUERY_FILE -> Seq(
                FieldDef(
                  StringConstantStructDef(PARAM_FILEPATH),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(PARAM_REMOVE_PREFIX),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(PARAM_REMOVE_SUFFIX),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(PARAM_COLUMN_DELIMITER),
                  RegexStructDef(".+".r),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(PARAM_KEY_COLUMN),
                  IntMinMaxStructDef(0, 1000),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(PARAM_WEIGHT_COLUMN),
                  IntMinMaxStructDef(0, 1000),
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(PARAM_DEFAULT_VALUE),
                  DoubleStructDef,
                  required = true
                )
              )
            )
          )
        )
      )
    }
  }

}
