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

import de.awagen.kolibri.base.domain.jobdefinitions.provider.QueryAndParamProvider
import de.awagen.kolibri.base.io.json.FileReaderJsonProtocol.fileReaderFormat
import de.awagen.kolibri.base.io.reader.FileReader
import de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol.OrderedMultiValuesAnyFormat
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object QueryAndParamProviderJsonProtocol extends DefaultJsonProtocol {

  implicit val queryAndParamProviderFormat: RootJsonFormat[QueryAndParamProvider] = jsonFormat(
    (contextPath: String, queryFile: String, queryFileReader: FileReader, parameters: OrderedMultiValues, defaultParameters: Map[String, Seq[String]]) =>
      QueryAndParamProvider.apply(contextPath, queryFile, queryFileReader, parameters, defaultParameters),
    "contextPath",
    "queryFile",
    "queryFileReader",
    "parameters",
    "defaultParameters"
  )

}
