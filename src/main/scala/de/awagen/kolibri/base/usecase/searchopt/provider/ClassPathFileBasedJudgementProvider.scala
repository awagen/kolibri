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


package de.awagen.kolibri.base.usecase.searchopt.provider

import de.awagen.kolibri.base.io.reader.FileReaderUtils
import de.awagen.kolibri.base.usecase.searchopt.provider.ClassPathFileBasedJudgementProvider.{JUDGEMENT_FILE_COLUMNS, JUDGEMENT_FILE_COLUMN_DIVIDER, JUDGEMENT_FILE_JUDGEMENT_COLUMN, JUDGEMENT_FILE_PRODUCTID_COLUMN, JUDGEMENT_FILE_SEARCHTERM_COLUMN, JUDGEMENT_LIST_DELIMITER}


object ClassPathFileBasedJudgementProvider {

  val JUDGEMENT_FILE_COLUMN_DIVIDER: String = "\u0000"
  val JUDGEMENT_LIST_DELIMITER: String = "\u0000"
  val JUDGEMENT_FILE_COLUMNS: Int = 3
  val JUDGEMENT_FILE_JUDGEMENT_COLUMN: Int = 2
  val JUDGEMENT_FILE_SEARCHTERM_COLUMN: Int = 0
  val JUDGEMENT_FILE_PRODUCTID_COLUMN: Int = 1

  private[provider] def apply(filepath: String): ClassPathFileBasedJudgementProvider = {
    new ClassPathFileBasedJudgementProvider(filepath)
  }

}

private[provider] class ClassPathFileBasedJudgementProvider(filepath: String) extends JudgementProvider[Double] {

  private val judgementStorage: Map[String, Double] = readJudgementsFromFile(filepath)

  private[this] def readJudgementsFromFile(filepath: String): Map[String, Double] = {
    FileReaderUtils.mappingFromFile[Double](
      () => FileReaderUtils.localResourceSource(filepath),
      JUDGEMENT_LIST_DELIMITER, JUDGEMENT_FILE_COLUMNS,
      x => createKey(x(JUDGEMENT_FILE_SEARCHTERM_COLUMN), x(JUDGEMENT_FILE_PRODUCTID_COLUMN)),
      x => x(JUDGEMENT_FILE_JUDGEMENT_COLUMN).toDouble)
  }

  override def retrieveJudgement(searchTerm: String, productId: String): Option[Double] = {
    judgementStorage.get(createKey(searchTerm, productId))
  }

  def createKey(searchTerm: String, productId: String): String = {
    s"$searchTerm$JUDGEMENT_FILE_COLUMN_DIVIDER$productId"
  }

  def keyToSearchTermAndProductId(key: String): (String, String) = {
    val parts = key.split(JUDGEMENT_FILE_COLUMN_DIVIDER)
    (parts.head, parts(1))
  }

  override def allJudgements: Map[String, Double] = collection.immutable.Map[String, Double]() ++ judgementStorage
}
