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

import de.awagen.kolibri.datatypes.metrics.aggregation.immutable
import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
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

    /**
     * Writer handling the immutable variant of MetricAggregation.
     * NOTE: right now uses the writer for the mutable variant and does the respective transformations.
     * This is suboptimal and adds a bit of overhead, thus to be changed.
     */
    def immutableMetricAggregationWriter(subFolder: String,
                                         tagToDataIdentifierFunc: Tags.Tag => String): Writer[de.awagen.kolibri.datatypes.metrics.aggregation.immutable.MetricAggregation[Tags.Tag], Tags.Tag, Any] =
      new Writer[de.awagen.kolibri.datatypes.metrics.aggregation.immutable.MetricAggregation[Tags.Tag], Tags.Tag, Any] {
        val mutableMetricAggregationWriter = metricAggregationWriter(subFolder, tagToDataIdentifierFunc)

        override def write(data: immutable.MetricAggregation[Tags.Tag], targetIdentifier: Tags.Tag): Either[Exception, Any] = {
          val mutableAggregation = MetricAggregation[Tags.Tag](
            scala.collection.mutable.Map({
              data.aggregationStateMap.view.mapValues(immutableDoc => {
                MetricDocument[Tags.Tag](immutableDoc.id, scala.collection.mutable.Map(immutableDoc.rows.toSeq:_*))
              }).toSeq:_*
            }).result(),
            data.keyMapFunction
          )
          mutableMetricAggregationWriter.write(mutableAggregation, targetIdentifier)
        }

        override def delete(targetIdentifier: Tags.Tag): Either[Exception, Unit] = {
          mutableMetricAggregationWriter.delete(targetIdentifier)
        }

        override def copyDirectory(dirPath: String, toDirPath: String): Unit =
          mutableMetricAggregationWriter.copyDirectory(dirPath, toDirPath)

        override def moveDirectory(dirPath: String, toDirPath: String): Unit =
          mutableMetricAggregationWriter.moveDirectory(dirPath, toDirPath)
      }

  }

}
