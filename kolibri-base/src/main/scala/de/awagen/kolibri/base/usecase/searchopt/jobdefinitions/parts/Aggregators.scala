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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.datatypes.functions.GeneralSerializableFunctions.identity
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator}

object Aggregators {

  /**
    * A supplier of aggregator to be used within one batch execution, collecitng single ProcessingMessage[MetricRow].
    * This aggregator would be utilized on the executing nodes to gather all data points corresponding to a single batch.
    *
    * @return
    */
  def singleBatchAggregatorSupplier: () => Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]] = () => {
    new TagKeyMetricAggregationPerClassAggregator(identity, ignoreIdDiff = false)
  }

  /**
    * A supplier of aggregator to be used for an overall aggregation of the results of the single batches.
    * While the aggregator can take single ProcessingMessage[MetricRow], it would usually collect aggregations
    * of type MetricAggregation[Tag] (e.g wrapped within AggregationState).
    * Thus the scope of it is the overall aggregation per job, and would usually reside in central position where all
    * the batch execution results are retrieved (e.g JobManager)
    *
    * @return
    */
  def fullJobToSingleTagAggregatorSupplier: () => Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]] = () => {
    new TagKeyMetricAggregationPerClassAggregator(new SerializableFunction1[Tag, Tag] {
      override def apply(v1: Tag): Tag = StringTag("ALL")
    }, ignoreIdDiff = true)
  }

}
