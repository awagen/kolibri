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

package de.awagen.kolibri.storage.io.writer.aggregation

import de.awagen.kolibri.storage.io.writer.Writers.Writer
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import scala.collection.mutable


case class BaseMetricAggregationWriter(writer: Writer[MetricDocument[Tag], Tag, Any]) extends Writer[MetricAggregation[Tag], Tag, Any] {

  override def write(data: MetricAggregation[Tag], targetIdentifier: Tag): Either[Exception, Any] = {
    val aggMap: mutable.Map[Tag, MetricDocument[Tag]] = data.aggregationStateMap

    val results: Seq[Either[Exception, _]] = aggMap.keys.to(LazyList).map(x => {
      writer.write(aggMap(x), x)
    })
    val failed: Seq[Exception] = results.filter(x => x.isLeft)
      .map(x => x.left.getOrElse(new RuntimeException("failed persisting data for tag")))
    if (failed.nonEmpty) {
      Left(new RuntimeException(s"$failed"))
    } else {
      Right(true)
    }

  }

  // TODO: implement
  override def delete(targetIdentifier: Tag): Either[Exception, Any] = ???
}
