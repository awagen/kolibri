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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.io.writer.Writers.FileWriter
import de.awagen.kolibri.base.io.writer.base.LocalDirectoryFileFileWriter
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._

class WriterJsonProtocolSpec extends UnitTestSpec {

  "WriterJsonProtocol" must {

    "correctly parse FileWriter[String, Any] as LocalDirectoryFileWriter" in {
      // given
      import WriterJsonProtocol._
      val json = """{"directory": "/testdir"}""".parseJson
      // when
      val writer: FileWriter[String, Any] = json.convertTo[FileWriter[String, Any]]
      // then
      writer.isInstanceOf[LocalDirectoryFileFileWriter] mustBe true
      writer.asInstanceOf[LocalDirectoryFileFileWriter].directory mustBe "/testdir"
    }

  }

}
