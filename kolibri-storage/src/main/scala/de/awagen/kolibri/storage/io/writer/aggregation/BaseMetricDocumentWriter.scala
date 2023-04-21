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
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import org.slf4j.{Logger, LoggerFactory}


case class BaseMetricDocumentWriter(writer: Writer[String, String, Any],
                                    formats: Seq[MetricDocumentFormat],
                                    subFolder: String,
                                    pathSeparator: String = "/",
                                    keyToFilenameFunc: Tag => String = x => x.toString
                                   ) extends Writer[MetricDocument[Tag], Tag, Any] {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def keyToResourceName(key: Tag): String = keyToFilenameFunc.apply(key)

  override def write(data: MetricDocument[Tag], targetIdentifier: Tag): Either[Exception, Any] = {
    if (data.id != targetIdentifier) Left(new RuntimeException(s"tragetIdentifier '$targetIdentifier' does not match" +
      s"tag '${data.id}' in document '$data'"))
    else {
      val writeResults: Seq[Either[Exception, Any]] = formats.map(format => {
        val filename = s"$subFolder$pathSeparator${keyToFilenameFunc.apply(targetIdentifier)}.${format.identifier}"
        logger.info(s"Trying to write result to: $filename")
        try {
          val doc = format.metricDocumentToString(data)
          val writeResult: Either[Exception, Any] = writer.write(doc, filename)
          logger.info(s"Finished write result to: $filename")
          writeResult
        }
        catch {
          case e: Exception =>
            logger.warn(s"Failed write result to: $filename")
            e.printStackTrace()
            Left(e)
        }
      })
      writeResults.find(x => x.isLeft).getOrElse(Right(true))
    }
  }

  // TODO: implement
  override def delete(targetIdentifier: Tag): Either[Exception, Any] = ???
}
