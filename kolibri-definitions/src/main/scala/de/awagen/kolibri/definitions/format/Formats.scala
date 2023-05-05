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


package de.awagen.kolibri.definitions.format

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.{CSVParameterBasedMetricDocumentFormat, MetricDocumentFormat}
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

object Formats {

  trait Format[-U, +V] extends SerializableFunction1[U, V]

  type StringFormat[U] = Format[U, String]

  case class MetricDocumentStringFormat(columnSeparator: String) extends StringFormat[MetricDocument[_]] {
    val format: MetricDocumentFormat = CSVParameterBasedMetricDocumentFormat(columnSeparator)

    override def apply(v1: MetricDocument[_]): String = {
      format.metricDocumentToString(v1)
    }
  }

}
