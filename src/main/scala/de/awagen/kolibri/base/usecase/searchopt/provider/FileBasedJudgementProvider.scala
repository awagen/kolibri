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

import de.awagen.kolibri.base.config.AppConfig.persistenceModule.persistenceDIModule
import de.awagen.kolibri.base.io.reader.{FileReader, FileReaderUtils}
import de.awagen.kolibri.base.usecase.searchopt.provider.FileBasedJudgementProvider.{JudgementFileFormatConfig, defaultJudgementFileFormatConfig}


object FileBasedJudgementProvider {

  case class JudgementFileFormatConfig(judgement_file_column_divider: String,
                                       judgement_list_delimiter: String,
                                       judgement_file_columns: Int,
                                       judgement_file_judgement_column: Int,
                                       judgement_file_searchterm_column: Int,
                                       judgement_file_productid_column: Int)

  val defaultJudgementFileFormatConfig: JudgementFileFormatConfig = JudgementFileFormatConfig(
    judgement_file_column_divider = "\u0000",
    judgement_list_delimiter = "\u0000",
    judgement_file_columns = 3,
    judgement_file_judgement_column = 2,
    judgement_file_searchterm_column = 0,
    judgement_file_productid_column = 1)

  private[provider] def apply(filepath: String,
                              judgementFileFormatConfig: JudgementFileFormatConfig = defaultJudgementFileFormatConfig): FileBasedJudgementProvider = {
    new FileBasedJudgementProvider(filepath, persistenceDIModule.fileReader)
  }

}

private[provider] class FileBasedJudgementProvider(filepath: String,
                                                   fileReader: FileReader,
                                                   judgementFileFormatConfig: JudgementFileFormatConfig = defaultJudgementFileFormatConfig)
  extends JudgementProvider[Double] {

  private val judgementStorage: Map[String, Double] = readJudgementsFromFile(filepath)

  private[this] def readJudgementsFromFile(filepath: String): Map[String, Double] = {
    FileReaderUtils.mappingFromFile[Double](
      () => fileReader.getSource(filepath),
      judgementFileFormatConfig.judgement_list_delimiter,
      judgementFileFormatConfig.judgement_file_columns,
      x => createKey(x(judgementFileFormatConfig.judgement_file_searchterm_column), x(judgementFileFormatConfig.judgement_file_productid_column)),
      x => x(judgementFileFormatConfig.judgement_file_judgement_column).toDouble)
  }

  override def retrieveJudgement(searchTerm: String, productId: String): Option[Double] = {
    judgementStorage.get(createKey(searchTerm, productId))
  }

  def createKey(searchTerm: String, productId: String): String = {
    s"$searchTerm${judgementFileFormatConfig.judgement_file_column_divider}$productId"
  }

  def keyToSearchTermAndProductId(key: String): (String, String) = {
    val parts = key.split(judgementFileFormatConfig.judgement_file_column_divider)
    (parts.head, parts(1))
  }

  override def allJudgements: Map[String, Double] = collection.immutable.Map[String, Double]() ++ judgementStorage
}
