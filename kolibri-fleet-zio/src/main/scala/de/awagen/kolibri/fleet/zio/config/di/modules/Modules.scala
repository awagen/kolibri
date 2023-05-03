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


package de.awagen.kolibri.fleet.zio.config.di.modules

import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer

import scala.util.matching.Regex

object Modules {

  // ENV_MODULE indicating environment specifics
  trait ENV_MODULE

  // NON_ENV_MODULE indicating a general module, not env-dependent
  trait NON_ENV_MODULE

  trait AWS_MODULE extends ENV_MODULE

  trait GCP_MODULE extends ENV_MODULE

  trait LOCAL_MODULE extends ENV_MODULE

  trait RESOURCE_MODULE extends ENV_MODULE

  trait GENERAL_MODULE extends NON_ENV_MODULE

  trait PersistenceDIModule {

    def writer: Writer[String, String, _]

    def reader: Reader[String, Seq[String]]

    def dataOverviewReader(dataIdentifierFilter: String => Boolean): DataOverviewReader

    def dataOverviewReaderWithRegexFilter(regex: Regex): DataOverviewReader = {
      dataOverviewReader(filename => regex.matches(filename))
    }

    def dataOverviewReaderUnfiltered: DataOverviewReader = {
      dataOverviewReader(_ => true)
    }

    def metricAggregationWriter(subFolder: String,
                                tagToDataIdentifierFunc: Tags.Tag => String): Writer[MetricAggregation[Tags.Tag], Tags.Tag, Any]

  }

}
