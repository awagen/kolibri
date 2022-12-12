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

package de.awagen.kolibri.datatypes.metrics.aggregation.writer

import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType

trait MetricDocumentFormat {

  def identifier: String

  def metricDocumentToString(ma: MetricDocument[_]): String

  def metricNameToTypeMappingFromContent(content: String): Map[String, AggregationType]

  def contentToMetricDocumentAndMetricTypeMapping(content: String): MetricDocument[Tag]
}


