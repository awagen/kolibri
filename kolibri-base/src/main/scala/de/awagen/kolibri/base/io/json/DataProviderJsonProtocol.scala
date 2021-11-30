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

import de.awagen.kolibri.base.domain.jobdefinitions.provider.data.DataProviders.OrderedMultiValuesProvider
import de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol._
import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.RootJsonFormat

object DataProviderJsonProtocol {

  implicit val orderedMultiValuesFormat: RootJsonFormat[OrderedMultiValuesProvider] = jsonFormat1(OrderedMultiValuesProvider)

}
