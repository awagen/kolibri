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

import de.awagen.kolibri.base.testclasses.UnitTestSpec

class LocalResourceDirectoryReaderSpec extends UnitTestSpec {

  "LocalResourceDirectoryReader" must {

    "correctly list files" in {
      val dirReader = LocalResourceDirectoryReader("empty_files/")
      val files: Seq[String] = dirReader.listResources("")
      files mustBe Seq("empty1.json", "empty2.csv", "empty3.txt")
    }

    "correctly list filered files" in {
      val jsonDirectoryReader = LocalResourceDirectoryReader("empty_files/", baseFilenameFilter = x => x.endsWith(".json"))
      val jsonFiles = jsonDirectoryReader.listResources("")
      val csvDirectoryReader = LocalResourceDirectoryReader("empty_files/", baseFilenameFilter = x => x.endsWith(".csv"))
      val csvFiles = csvDirectoryReader.listResources("")
      val txtDirectoryReader = LocalResourceDirectoryReader("empty_files/", baseFilenameFilter = x => x.endsWith(".txt"))
      val txtFiles = txtDirectoryReader.listResources("")
      jsonFiles mustBe Seq("empty1.json")
      csvFiles mustBe Seq("empty2.csv")
      txtFiles mustBe Seq("empty3.txt")
    }

  }

}
