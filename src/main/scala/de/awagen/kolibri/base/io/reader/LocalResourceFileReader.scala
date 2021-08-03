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

package de.awagen.kolibri.base.io.reader

import scala.io.Source

case class LocalResourceFileReader(delimiterAndPosition: Option[(String, Int)],
                                   encoding: String = "UTF-8") extends FileReader {

  override def getSource(fileIdentifier: String): Source = {
    FileReaderUtils.localResourceSource(fileIdentifier, encoding)
  }

  /**
    * if delimiterAndPosition is set, split each line by the delimiter and select the nth element,
    * otherwise just return trimmed lines
    * @param fileIdentifier
    * @return
    */
  override def read(fileIdentifier: String): Seq[String] = {
    val source: Source = getSource(fileIdentifier)
    delimiterAndPosition.fold(FileReaderUtils.trimmedEntriesByLineFromFile(source))(
      x => FileReaderUtils.pickUniquePositionPerLineDeterminedByDelimiter(source, x._1, x._2)
    )
  }
}
