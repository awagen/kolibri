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

import de.awagen.kolibri.base.usecase.searchopt.provider.{FileBasedJudgementProviderFactory, JudgementProviderFactory}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}


object JudgementProviderFactoryJsonProtocol extends DefaultJsonProtocol {

  implicit object JudgementProviderFactoryDoubleFormat extends JsonFormat[JudgementProviderFactory[Double]] {
    override def read(json: JsValue): JudgementProviderFactory[Double] = json match {
      case spray.json.JsObject(fields) if fields.contains("type") => fields("type").convertTo[String] match {
        case "FILE_BASED" =>
          FileBasedJudgementProviderFactory(fields("filename").convertTo[String])
        case e => throw DeserializationException(s"Expected a valid type for JudgementProviderFactory but got value $e")
      }
      case e => throw DeserializationException(s"Expected a value from JudgementProviderFactory but got value $e")
    }

    override def write(obj: JudgementProviderFactory[Double]): JsValue = JsString(obj.toString)
  }

}
