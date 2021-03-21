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

import de.awagen.kolibri.base.domain.jobdefinitions.JobDefinitions.{OrderedMultiValuesRunnableJobDefinition, OrderedMultiValuesRunnableTaskJobDefinition}
import de.awagen.kolibri.base.io.json.DataProviderJsonProtocol._
import de.awagen.kolibri.base.io.json.EnumerationJsonProtocol._
import de.awagen.kolibri.base.io.json.WriterJsonProtocol._
import spray.json.RootJsonFormat
import SerializableAggregatorSupplierJsonProtocol._


object JobDefinitionsJsonProtocol {

  implicit val orderedMultiValuesRunnableJobDefinitionFormat: RootJsonFormat[OrderedMultiValuesRunnableJobDefinition] = {
    import BatchGeneratorsJsonProtocol.orderedMultiValuesBatchGeneratorFormat
    import spray.json.DefaultJsonProtocol._
    jsonFormat11(OrderedMultiValuesRunnableJobDefinition)
  }

  implicit val orderedMultiValuesRunnableTaskJobDefinitionFormat: RootJsonFormat[OrderedMultiValuesRunnableTaskJobDefinition] = {
    import BatchGeneratorsJsonProtocol.orderedMultiValuesTypedTagBatchGeneratorFormat
    import spray.json.DefaultJsonProtocol._
    jsonFormat9(OrderedMultiValuesRunnableTaskJobDefinition)
  }

}
