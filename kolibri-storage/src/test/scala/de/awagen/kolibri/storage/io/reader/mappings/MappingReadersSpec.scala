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


package de.awagen.kolibri.storage.io.reader.mappings

import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.storage.io.reader.mappings.MappingReaders.{CsvMappingReader, MappingJsonReader}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalResourceDirectoryReader, LocalResourceFileReader, Reader}
import de.awagen.kolibri.storage.testclasses.UnitTestSpec

class MappingReadersSpec extends UnitTestSpec {

  "KeyByFilenameValuePerLineReader" must {

    "correctly read mappings" in {
      // given
      val directoryReader: DataOverviewReader = LocalResourceDirectoryReader(
        "mapping_data",
        _ => true
      )
      val fileReader: Reader[String, Seq[String]] = LocalResourceFileReader(
        "mapping_data",
        None,
        fromClassPath = true
      )
      val reader = MappingReaders.KeyByFilenameValuePerLineReader(
        directoryReader = directoryReader,
        fileReader = fileReader,
        splitLineBy = Some(","),
        fileNameFilter = x => x.endsWith(".txt"),
        fileNameToKeyFunc = x => x.split("\\.")(0)
      )
      // when
      val result: Map[String, IndexedGenerator[Seq[String]]] = reader.read("test1")
      val file1Result = result("file1")
      val file2Result = result("file2")
      result.keys.size mustBe 2
      file1Result.iterator.toSeq mustBe Seq(
        Seq("file1_q1"),
        Seq("file1_q2", "file1_q2_1"),
        Seq("file1_q3"),
        Seq("file1_q4"),
        Seq("file1_q5"),
        Seq("file1_q6"),
        Seq("file1_q7"))
      file2Result.iterator.toSeq mustBe Seq(
        Seq("file2_p1"),
        Seq("file2_p2"),
        Seq("file2_p3"),
        Seq("file2_p4")
      )
    }
  }

  "CsvMappingReader" must {


    "correctly read csv mapping" in {
      // given
      val fileReader: Reader[String, Seq[String]] = LocalResourceFileReader(
        "",
        None,
        fromClassPath = true
      )
      val mappingReader = CsvMappingReader(
        fileReader = fileReader,
        columnSeparator = ",",
        splitValueBy = None,
        keyColumnIndex = 1,
        valueColumnIndex = 2
      )
      // when
      val mapping: Map[String, IndexedGenerator[Seq[String]]] = mappingReader.read("csvKeyValueSample.csv")
      // then
      mapping.keySet mustBe Set("key1", "key2", "key3", "key4", "key5")
      mapping("key1").iterator.toSeq mustBe Seq(Seq("value1"))
      mapping("key2").iterator.toSeq mustBe Seq(Seq("value2"))
      mapping("key3").iterator.toSeq mustBe Seq(Seq("value3"))
      mapping("key4").iterator.toSeq mustBe Seq(Seq("value4"))
      mapping("key5").iterator.toSeq mustBe Seq(Seq("value5"))
    }
  }

  "MappingJsonReader" must {

    "correctly read mapping from json" in {
      // given
      val fileReader: Reader[String, Seq[String]] = LocalResourceFileReader(
        "",
        None,
        fromClassPath = true
      )
      val reader = MappingJsonReader(fileReader)
      // when
      val result: Map[String, IndexedGenerator[String]] = reader.read("mappingJsonSample.json")
      // then
      result.keys mustBe Set("key1", "key2", "key3", "key4", "key5")
      result("key1").iterator.toSeq mustBe Seq("""{"vk1":"vv1"}""")
      result("key2").iterator.toSeq mustBe Seq("""{"vk1":"vv2"}""")
      result("key3").iterator.toSeq mustBe Seq("""{"vk1":"vv3"}""")
      result("key4").iterator.toSeq mustBe Seq("""{"vk1":"vv4"}""")
      result("key5").iterator.toSeq mustBe Seq("""{"vk1":"vv5"}""")

    }

  }

}
