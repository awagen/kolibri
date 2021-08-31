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

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.io.reader.FileReader
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, JsonReader, enrichAny}

import scala.collection.mutable
import scala.io.Source
import IndexedGeneratorJsonProtocol._

object MappingSupplierJsonProtocol extends DefaultJsonProtocol {

  val TYPE_FIELD = "type"
  val VALUE_FIELD = "value"
  val FILE_PATH_FIELD = "filePath"
  val COLUMN_SEPARATOR_FIELD = "columnSeparator"
  val FROM_INDEX_FIELD = "fromIndex"
  val TO_INDEX_FIELD = "toIndex"
  val IGNORE_FIRST_LINE_FIELD = "ignoreFirstLine"
  val DIRECTORY_FIELD = "directory"
  val FILES_SUFFIX_FIELD = "filesSuffix"
  val PARAM_NAMES_TO_DIR_MAP_FIELD = "paramNamesToDirMap"
  val PARAM_NAME_FIELD = "paramName"
  val ROW_SEPARATOR_FIELD = "rowSeparator"

  val IGNORE_LINE_STARTING_WITH = "#"

  val FROM_JSON_TYPE = "FROM_JSON_MAP"
  val FROM_DIRECTORY_FILES_IDENTITY = "FROM_DIRECTORY_IDENTITY"
  val FROM_CSV_TYPE = "FROM_CSV"
  val FROM_DIRECTORY_FILES = "FROM_DIRECTORY"

  /**
    * Given json fields, extract plain key-value mapping from csv file
    *
    * @param fields
    * @return
    */
  def extractPlainMappingFromCsvFile(fields: Map[String, JsValue]): Map[String, String] = {
    val fileReader: FileReader = AppConfig.persistenceModule.persistenceDIModule.fileReader
    val filePath = fields(FILE_PATH_FIELD).convertTo[String]
    val columnSeparator = fields(COLUMN_SEPARATOR_FIELD).convertTo[String]
    val fromIndex = fields(FROM_INDEX_FIELD).convertTo[Int]
    val toIndex = fields(TO_INDEX_FIELD).convertTo[Int]
    val ignoreFirstLine: Boolean = fields.get(IGNORE_FIRST_LINE_FIELD).exists(x => x.convertTo[Boolean])
    val source: Source = fileReader.getSource(filePath)
    var lines: Seq[String] = source.getLines().toSeq
    if (lines.nonEmpty && ignoreFirstLine) lines = lines.slice(1, lines.size)
    lines
      .filter(line => !line.startsWith(IGNORE_LINE_STARTING_WITH))
      .map(line => line.split(columnSeparator))
      .map(x => (x(fromIndex), x(toIndex))).toMap
  }

  def extractMappedMappingFromJson[T](fields: Map[String, JsValue])(implicit evidence: JsonReader[Map[String, Map[String, IndexedGenerator[T]]]]): Map[String, Map[String, IndexedGenerator[T]]] = {
    fields(VALUE_FIELD).convertTo[Map[String, Map[String, IndexedGenerator[T]]]]
  }

  /**
    * given directory, generates keys from filenames and assigns for each key the key itself as value for the paramName
    * given by field "paramName". Useful e.g to map keys to themselves and generating a modifier from it
    * NOTE: suboptimal, not quite straightforward
    *
    * @param fields
    * @return
    */
  def extractIdentityParamMappingFromFilenames[T](fields: Map[String, JsValue], valueMapFunc: String => T): Map[String, Map[String, IndexedGenerator[T]]] = {
    val directory = fields(DIRECTORY_FIELD).convertTo[String]
    val filesSuffix = fields(FILES_SUFFIX_FIELD).convertTo[String]
    val paramName = fields(PARAM_NAME_FIELD).convertTo[String]
    val directoryReader = AppConfig.persistenceModule.persistenceDIModule.directoryReader(x => x.endsWith(filesSuffix))
    val keyToParamMap: mutable.Map[String, Map[String, IndexedGenerator[T]]] = mutable.Map.empty
    directoryReader.listFiles(directory, _ => true)
      .map(file => file.split("/").last.stripSuffix(filesSuffix))
      .foreach(key => {
        val valueGen = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(valueMapFunc.apply(key)))
        keyToParamMap(key) = Map(paramName -> valueGen)
      })
    keyToParamMap.toMap
  }

  /**
    * For map mapping paramName to directories, for each paramName look into respective directory,
    * and generate keys by removing suffix from filename, then extract from file the values for the paramName and key
    * and put this in map (level1-key: file key, level2-key: paramName)
    *
    * @param fields
    * @param lineToValueFunc
    * @param normFunc
    * @tparam T
    * @return
    */
  def extractParamMapsFromDirs[T](fields: Map[String, JsValue], lineToValueFunc: String => T, normFunc: T => T): Map[String, Map[String, IndexedGenerator[T]]] = {
    val paramNamesToDirMap = fields(PARAM_NAMES_TO_DIR_MAP_FIELD).convertTo[Map[String, String]]
    val filesSuffix = fields(FILES_SUFFIX_FIELD).convertTo[String]
    val directoryReader = AppConfig.persistenceModule.persistenceDIModule.directoryReader(x => x.endsWith(filesSuffix))
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.fileReader
    val keyToParamMap: mutable.Map[String, mutable.Map[String, IndexedGenerator[T]]] = mutable.Map.empty
    paramNamesToDirMap.foreach(x => {
      val paramName = x._1
      // for single parameterName, collect all files. The filenames with suffix stripped off make the keys
      val files: Seq[String] = directoryReader.listFiles(x._2, _ => true)
      files.foreach(file => {
        val key: String = file.split("/").last.stripSuffix(filesSuffix)
        val values: IndexedGenerator[T] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
          fileReader.read(file)
            .map(x => x.trim)
            .filter(x => x.nonEmpty)
            .map(x => lineToValueFunc.apply(x))
            .map(x => normFunc.apply(x)))
        if (!keyToParamMap.contains(key)) keyToParamMap(key) = mutable.Map.empty
        keyToParamMap(key)(paramName) = values
      })
    })
    keyToParamMap.view.mapValues(values => values.toMap).toMap
  }

  def extractParamMapsFromDirs(fields: Map[String, JsValue]): Map[String, Map[String, IndexedGenerator[Seq[String]]]] = {
    val rowSeparator = fields(ROW_SEPARATOR_FIELD).convertTo[String]
    extractParamMapsFromDirs[Seq[String]](fields, x => x.split(rowSeparator), x => x.map(x => x.trim))
  }

  implicit object StringToStringJsonProtocol extends JsonFormat[() => Map[String, String]] {
    override def read(json: JsValue): () => Map[String, String] = {
      json match {
        case spray.json.JsObject(fields) => fields(TYPE_FIELD).convertTo[String] match {
          case FROM_JSON_TYPE => () => fields(VALUE_FIELD).convertTo[Map[String, String]]
          case FROM_CSV_TYPE =>
            new SerializableSupplier[Map[String, String]] {
              private[this] var value: Option[Map[String, String]] = None

              override def apply(): Map[String, String] = {
                if (value.isDefined) value.get
                else {
                  val mapping: Map[String, String] = extractPlainMappingFromCsvFile(fields)
                  value = Some(mapping)
                  mapping
                }
              }
            }
          case e => throw DeserializationException(s"Expected valid type for type Map[String, String]  but got value $e")
        }
        case e => throw DeserializationException(s"Expected a value of type Map[String, String]  but got value $e")
      }
    }

    override def write(obj: () => Map[String, String]): JsValue = """{}""".toJson
  }

  implicit object MappedParamMapJsonProtocol extends JsonFormat[() => Map[String, Map[String, IndexedGenerator[Seq[String]]]]] {
    override def read(json: JsValue): () => Map[String, Map[String, IndexedGenerator[Seq[String]]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_FIELD).convertTo[String] match {
        case FROM_DIRECTORY_FILES_IDENTITY =>
          () => extractIdentityParamMappingFromFilenames[Seq[String]](fields = fields, valueMapFunc = x => Seq(x))
        // extract mapping directly from json
        case FROM_JSON_TYPE =>
          () => extractMappedMappingFromJson[Seq[String]](fields)
        // given a mapping of parameter name and directory, picks all files, generates top-level key
        // by removing suffix from filename, and for each top-level key reads the respective file
        // picking one value per line (in case of multivalued settings, multiple values separated
        // by rowSeparator)
        case FROM_DIRECTORY_FILES =>
          () => extractParamMapsFromDirs(fields)
      }
    }

    override def write(obj: () => Map[String, Map[String, IndexedGenerator[Seq[String]]]]): JsValue = """{}""".toJson
  }

  implicit object MappedSingleValueMapJsonProtocol extends JsonFormat[() => Map[String, Map[String, IndexedGenerator[String]]]] {
    override def read(json: JsValue): () => Map[String, Map[String, IndexedGenerator[String]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_FIELD).convertTo[String] match {
        case FROM_DIRECTORY_FILES_IDENTITY =>
          () => extractIdentityParamMappingFromFilenames[String](fields = fields, valueMapFunc = identity)
        // extract mapping directly from json
        case FROM_JSON_TYPE =>
          () => extractMappedMappingFromJson[String](fields)
        // given a mapping of parameter name and directory, picks all files, generates top-level key
        // by removing suffix from filename, and for each top-level key reads the respective file
        // picking one value per line
        case FROM_DIRECTORY_FILES =>
          () => extractParamMapsFromDirs[String](fields, identity, x => x.trim)
      }
    }

    override def write(obj: () => Map[String, Map[String, IndexedGenerator[String]]]): JsValue = """{}""".toJson
  }

}
