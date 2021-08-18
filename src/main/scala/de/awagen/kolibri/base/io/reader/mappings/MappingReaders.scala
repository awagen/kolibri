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


package de.awagen.kolibri.base.io.reader.mappings

import de.awagen.kolibri.base.io.reader.{DirectoryReader, FileReader}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}

import scala.collection.mutable
import spray.json._

object MappingReaders {

  trait MappingReader[+U] {

    def read(resourceIdentifier: String): Map[String, U]

  }

  trait GeneratorMappingReader[+U] extends MappingReader[IndexedGenerator[U]]

  /**
    * Read files in directory and use fileNameToKeyFunc applied on the filename
    * to create the respective key, while values are given in the respective file per
    * line. Optionally splits by splitLineBy, if None uses line as is as value
    *
    * @param directoryReader   - directory reader to scan directory
    * @param fileReader        - file reader to read the content of the found files
    * @param splitLineBy       - Optional string to split lines by
    * @param fileNameFilter    - filter for file names to selectively only select certain files
    * @param fileNameToKeyFunc - the key from file name generation function
    */
  case class KeyByFilenameValuePerLineReader(directoryReader: DirectoryReader,
                                             fileReader: FileReader,
                                             splitLineBy: Option[String],
                                             fileNameFilter: String => Boolean,
                                             fileNameToKeyFunc: String => String) extends GeneratorMappingReader[Seq[String]] {
    override def read(resourceIdentifier: String): Map[String, IndexedGenerator[Seq[String]]] = {
      val filteredFiles: Seq[String] = directoryReader.listFiles(resourceIdentifier, fileNameFilter)
      val map: mutable.Map[String, IndexedGenerator[Seq[String]]] = mutable.Map.empty
      filteredFiles.foreach(absoluteFilePath => {
        val values: Seq[Seq[String]] = fileReader.read(absoluteFilePath)
          .map(x => x.trim)
          .filter(x => x.nonEmpty)
          .map(line => splitLineBy.map(splitBy => line.split(splitBy).toSeq).getOrElse(Seq(line)))
        val key: String = fileNameToKeyFunc.apply(absoluteFilePath.split("/").last)
        map(key) = ByFunctionNrLimitedIndexedGenerator.createFromSeq(values)
      })
      Map(map.toSeq: _*)
    }
  }

  /**
    * Read Csv-like file.
    * Split line by column separator,
    * optionally split value field by splitValueBy,
    * otherwise take full column as single value.
    * Keys for the mapping are picked from column of indec keyColumnIndex
    * and value from valueColumnIndex
    *
    * @param fileReader       - The file reader
    * @param columnSeparator  - column separator
    * @param splitValueBy     - optional string to split extracted values by
    * @param keyColumnIndex   - column index to retrieve key
    * @param valueColumnIndex - colum index to retrieve value
    */
  case class CsvMappingReader(fileReader: FileReader,
                              columnSeparator: String,
                              splitValueBy: Option[String],
                              keyColumnIndex: Int,
                              valueColumnIndex: Int) extends GeneratorMappingReader[Seq[String]] {
    override def read(resourceIdentifier: String): Map[String, IndexedGenerator[Seq[String]]] = {
      val map: mutable.Map[String, IndexedGenerator[Seq[String]]] = mutable.Map.empty
      fileReader.read(resourceIdentifier)
        .filter(line => line.nonEmpty)
        .map(line => line.split(columnSeparator))
        .filter(columns => columns.length >= math.max(keyColumnIndex, valueColumnIndex) + 1)
        .foreach(columns => {
          val key: String = columns(keyColumnIndex)
          val valueStr = columns(valueColumnIndex)
          val value: Seq[String] = splitValueBy
            .map(splitBy => valueStr.split(splitBy).toSeq)
            .getOrElse(Seq(valueStr))
          if (value.nonEmpty) map(key) = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(value))
        })
      Map(map.toSeq: _*)
    }
  }

  /**
    * Assuming file with content being a json.
    * As keys the keys of the json are used, and values are the respective
    * mapped json values transformed to string.
    *
    * @param fileReader - Reader used to read file contents
    */
  case class MappingJsonReader(fileReader: FileReader) extends GeneratorMappingReader[String] {
    override def read(resourceIdentifier: String): Map[String, IndexedGenerator[String]] = {
      val fileContent: JsValue = fileReader.read(resourceIdentifier).mkString("\n").parseJson
      fileContent match {
        case spray.json.JsObject(fields) =>
          fields.map(kv => (kv._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(kv._2.toString))))
        case _ => Map.empty
      }
    }
  }

}