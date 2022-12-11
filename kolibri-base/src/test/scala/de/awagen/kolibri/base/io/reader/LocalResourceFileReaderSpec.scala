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

import de.awagen.kolibri.base.processing.execution.functions.FileUtils.regexDirectoryReader
import de.awagen.kolibri.base.testclasses.UnitTestSpec

class LocalResourceFileReaderSpec extends UnitTestSpec {

  "LocalResourceFileReader" must {

    "correctly read entries" in {
      val fileReader: LocalResourceFileReader = LocalResourceFileReader(basePath = "", delimiterAndPosition = None, fromClassPath = true)
      val queries: Seq[String] = fileReader.read("data/queryterms.txt")
      queries mustBe Seq("schuh", "spiegel", "uhr", "hose", "jeans", "tv")
    }

    "correctly read entries and pick nth after split" in {
      val fileReader: LocalResourceFileReader = LocalResourceFileReader(basePath = "", delimiterAndPosition = Some(("\\s+", 1)), fromClassPath = true)
      val queries: Seq[String] = fileReader.read("data/queryterms_column2.txt")
      queries mustBe Seq("term1", "term2", "term3", "term4", "term5")
    }

    "correctly find files in directory" in {
      val folder = "data/example_query_results_csv"
      val directoryReader: DataOverviewReader = regexDirectoryReader(".*"r)
      val files = directoryReader.listResources(folder, _ => true)
      files.length mustBe 24
    }

  }

}
