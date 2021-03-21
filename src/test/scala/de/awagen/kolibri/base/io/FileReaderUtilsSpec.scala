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

package de.awagen.kolibri.base.io

import de.awagen.kolibri.base.io.reader.FileReaderUtils
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.utils.MathUtils

import scala.io.Source


class FileReaderUtilsSpec extends UnitTestSpec {

  "FileReader" must {

    "correctly transform multicolumn file to String to tuple mapping" in {
      //given
      val source: Source = FileReaderUtils.localResourceSource("testcolumns.txt")
      //when
      val data: Map[String, (Int, Float)] = FileReaderUtils.mappingFromFile[(Int, Float)](source, "\\s+", 4, x => s"${x.head}-${x(1)}",
        x => (x(2).toInt, x(3).toFloat))
      //then
      val expectedData = Map(
        "term1-product1" -> (1, 0.19f),
        "term2-product1" -> (6, 0.20f),
        "term3-product1" -> (3, 0.11f),
        "term3-product2" -> (1, 0.21f),
        "term1-product5" -> (9, 0.19f),
        "term4-product10" -> (1, 0.36f)
      )
      data.keySet.size mustBe expectedData.keySet.size
      expectedData.keySet.foreach(
        x => {
          val expected = expectedData(x)
          val actual = data(x)
          expected._1 mustBe actual._1
          MathUtils.equalWithPrecision(expected._2, actual._2, 0.0001f) mustBe true
        }
      )
    }

    "correctly parse single entry per line files" in {
      //given
      val source: Source = FileReaderUtils.localResourceSource("testlines.txt")
      //when
      val entries: Seq[String] = FileReaderUtils.trimmedEntriesByLineFromFile(source)
      //then
      entries mustBe Seq("test1", "test2", "test4", "test6", "test5")
    }

    "correctly parse entries by delimiter from file" in {
      //given
      val source: Source = FileReaderUtils.localResourceSource("testmultientrylines.txt")
      //when
      val entries: Seq[String] = FileReaderUtils.trimmedEntriesByDelimiterFromFile(source, ",")
      //then
      entries mustBe Seq("test1", "test3", "test6", "test7", "test2")
    }
  }

}
