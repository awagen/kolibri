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

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}

object OrderedMultiValuesJsonProtocol extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit object OrderedMultiValuesAnyFormat extends JsonFormat[OrderedMultiValues] {
    override def read(json: JsValue): OrderedMultiValues = {
      try {
        de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol.OrderedMultiValuesAnyFormat.read(json)
      }
      catch {
        case _: DeserializationException => json match {
          case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
            case "FROM_FILES_LINES" =>
              val paramNameToFile = fields("values").convertTo[Map[String, String]]
              var params: Seq[OrderedValues[String]] = Seq.empty
              val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
              paramNameToFile.foreach(x => {
                val name: String = x._1
                val values: Seq[String] = fileReader.read(x._2).map(x => x.trim).filter(x => x.nonEmpty)
                logger.debug(s"found ${values.size} values for param $name, values: $values")
                params = params :+ DistinctValues[String](name, values)
              })
              logger.debug(s"params size=${params.size}, values=$params")
              GridOrderedMultiValues(params)
            case "FROM_VALUES" =>
              val paramNameToValues = fields("values").convertTo[Map[String, Seq[String]]]
              var params: Seq[OrderedValues[String]] = Seq.empty
              paramNameToValues.foreach(x => {
                val name: String = x._1
                val values: Seq[String] = x._2.map(x => x.trim).filter(x => x.nonEmpty)
                logger.debug(s"found ${values.size} values for param $name, values: $values")
                params = params :+ DistinctValues[String](name, values)
              })
              logger.debug(s"params size=${params.size}, values=$params")
              GridOrderedMultiValues(params)
          }
        }
      }
    }

    override def write(obj: OrderedMultiValues): JsValue = """{}""".toJson
  }

}
