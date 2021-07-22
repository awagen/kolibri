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


package de.awagen.kolibri.base.processing.consume

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.processing.classifier.Mapper.FilteringMapper
import de.awagen.kolibri.datatypes.types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator


/**
  * Grouping the settings used by aggregator. Here the assumption is made that the aggregator keeps track of the aggregation state,
  * might filter and/or map received single results and/or received partial aggregations and finally might filter and/or
  * map the result before sending to another actor
  *
  * @param filteringSingleElementMapperForAggregator - mapper/filter on the single results received
  * @param filterAggregationMapperForAggregator      - mapper/filter on the received partial aggregations
  * @param filteringMapperForResultSending           - mapper/filter on the final aggregation to be sent to another receiving actor
  * @param aggregatorSupplier                        - the supplier of an aggregator
  * @param expectationSupplier                       - the supplier of an expectation on the received elements. The ExecutionExpectation can contain
  *                                                  any kind of expectation, e.g on the nr of received elements, on the allowed overall aggregation time,
  *                                                  allowed time between received elements and such
  * @tparam U - type of the single elements that might be received
  * @tparam V - type of partial aggregations that might be received
  */
case class AggregatorConfig[U, V <: WithCount](filteringSingleElementMapperForAggregator: FilteringMapper[ProcessingMessage[U], ProcessingMessage[U]],
                                  filterAggregationMapperForAggregator: FilteringMapper[V, V],
                                  filteringMapperForResultSending: FilteringMapper[V, V],
                                  aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V])
