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

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._
import DataOverviewReaderJsonProtocol._
import de.awagen.kolibri.storage.io.reader.{AwsS3DirectoryReader, DataOverviewReader, LocalDirectoryReader, LocalResourceDirectoryReader}

class DataOverviewReaderJsonProtocolSpec extends UnitTestSpec {

  val localResourceDirectoryReader: JsValue =
    """
      |{
      |"type": "CLASSPATH_REGEX_FILEFILTER",
      |"baseDir": "data/",
      |"regex": ".*judgements.txt",
      |"encoding": "UTF-8"
      |}
      |""".stripMargin.parseJson

  val localReader: JsValue =
    """
      |{
      |"type": "LOCAL_READER",
      |"baseDir": "/ups",
      |"regex": ".*",
      |"encoding": "UTF-8"
      |}
      |""".stripMargin.parseJson

  val awsS3DirectoryReader: JsValue =
    """
      |{
      |"type": "AWS_READER",
      |"bucketName": "bucket",
      |"dirPath": "path1/path2",
      |"region": "US_EAST_1",
      |"delimiter": "\t",
      |"regex": ".*"
      |}
      |""".stripMargin.parseJson

  "parse LocalResourceDirectoryReader" in {
    val reader = localResourceDirectoryReader.convertTo[DataOverviewReader]
    reader.isInstanceOf[LocalResourceDirectoryReader] mustBe true
  }

  "parse LocalDirectoryReader" in {
    val reader = localReader.convertTo[DataOverviewReader]
    reader.isInstanceOf[LocalDirectoryReader] mustBe true
  }

  "parse AwsS3DirectoryReader" in {
    val reader = awsS3DirectoryReader.convertTo[DataOverviewReader]
    reader.isInstanceOf[AwsS3DirectoryReader] mustBe true
  }

}
