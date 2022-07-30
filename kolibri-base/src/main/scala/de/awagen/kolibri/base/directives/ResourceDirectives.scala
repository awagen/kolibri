/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.base.directives

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.config.AppConfig.filepathToJudgementProvider
import de.awagen.kolibri.base.directives.ExpirePolicy.ExpirePolicy
import de.awagen.kolibri.base.directives.ResourceType.ResourceType
import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.jsValueStringConversion
import de.awagen.kolibri.base.io.reader.FileReaderUtils
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import spray.json.DefaultJsonProtocol.{JsValueFormat, immSeqFormat}
import spray.json.JsValue

import scala.collection.mutable


object ResourceType extends Enumeration {
  type ResourceType[+T] = Val[T]

  protected case class Val[+T](classTyped: ClassTyped[T]) extends super.Val

  val JUDGEMENTS: Val[Map[String, Double]] = Val(ClassTyped[Map[String, Double]])
  val KEY_VALUES_MAPPINGS: Val[Map[String, IndexedGenerator[String]]] = Val(ClassTyped[Map[String, IndexedGenerator[String]]])
}

object ExpirePolicy extends Enumeration {
  type ExpirePolicy = Value

  val ON_JOB_END: Value = Value
}


case class Resource[+T](resourceType: ResourceType[T], identifier: String) extends KolibriSerializable

trait WithResources extends KolibriSerializable {

  private[this] val resourceSet: mutable.Set[Resource[_]] = mutable.Set.empty[Resource[_]]

  def resources: Set[Resource[_]] = resourceSet.toSet

  def addResource(resource: Resource[_]): Unit = {
    resourceSet += resource
  }

}


/**
 * Functions for data loading
 */
object LoadFunctions {

  /**
   * Supplier for loading of judgements from file
   * @param file - file path (relative to base path) to file containing the judgements (format depends on config setting)
   * @return
   */
  def judgementsFromFile(file: String): SerializableSupplier[Map[String, Double]] = new SerializableSupplier[Map[String, Double]] {
    override def apply(): Map[String, Double] = filepathToJudgementProvider(file).allJudgements
  }

  /**
   * Supplier that looks in folder, filters for files with passed suffix, removes suffix and takes
   * the remaining value of the files as keys, and the respective file content per key
   * is parsed to retrieve values (one value per line)
   * @param folder - folder to for files
   * @param fileSuffix - suffix to filter files by and to remove to retrieve the key value (remaining file name)
   * @return
   */
  def mappingsFromFolderWithKeyFromFileNameAndValuesFromContent(folder: String, fileSuffix: String): SerializableSupplier[Map[String, IndexedGenerator[String]]] = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
    override def apply(): Map[String, IndexedGenerator[String]] = {
      FileReaderUtils.extractFilePrefixToLineValuesMapping(folder, fileSuffix, "/")
        .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
    }
  }

  /**
   * Supplier deriving mappings from csv file. If a key appears in several rows, each value
   * is collected.
   * @param file - csv file path (relative to base folder)
   * @param columnDelimiter - delimiter to split columns
   * @param keyColumnIndex - index of the key column (0-based)
   * @param valueColumnIndex - index of the value column (0-based)
   * @return
   */
  def mappingsFromCsvFile(file: String, columnDelimiter: String, keyColumnIndex: Int, valueColumnIndex: Int): SerializableSupplier[Map[String, IndexedGenerator[String]]] = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
    override def apply(): Map[String, IndexedGenerator[String]] = {
      val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
      val keyValuesMapping = FileReaderUtils.multiMappingFromCSVFile[String](
        source = fileReader.getSource(file),
        columnDelimiter = columnDelimiter,
        filterLessColumnsThan =  math.max(keyColumnIndex, valueColumnIndex) + 1,
        valsToKey = x => x(keyColumnIndex),
        columnsToValue = x => x(valueColumnIndex)
      )
      keyValuesMapping.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2.toSeq)))
    }
  }

  /**
   * Supplier reading key-value mappings from json file, assuming the values are in
   * array format
   * @param file - file path to file containing json where value for each key is array
   * @return
   */
  def jsonArrayMappingsFromFile(file: String): SerializableSupplier[Map[String, IndexedGenerator[String]]] = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
    override def apply(): Map[String, IndexedGenerator[String]] = {
      val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
      FileReaderUtils.readJsonMapping(file, fileReader, x => x.convertTo[Seq[JsValue]].map(x => jsValueStringConversion(x)))
        .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
    }
  }

  /**
   * Supplier reading key-value mappings from json file, assuming the values are single values
   * @param file- file path to file containing json where value for each key is single value
   * @return
   */
  def jsonSingleMappingsFromFile(file: String): SerializableSupplier[Map[String, IndexedGenerator[String]]] = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
    override def apply(): Map[String, IndexedGenerator[String]] = {
      val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
      FileReaderUtils.readJsonMapping(file, fileReader, x => jsValueStringConversion(x))
        .map(x => (x._1, Seq(x._2)))
        .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
    }
  }

  /**
   * Given a mapping of keys to filename, create key-value mapping by reading the file for each key,
   * assuming one value per line
   * @param keyToValueJsonMap - mapping of keys to paths to file containing the values for the key (one value per line)
   * @return
   */
  def jsonValuesFilesMapping(keyToValueJsonMap: Map[String, String]): SerializableSupplier[Map[String, IndexedGenerator[String]]] = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
    override def apply(): Map[String, IndexedGenerator[String]] = {
      keyToValueJsonMap.map(x => {
        (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(FileReaderUtils.loadLinesFromFile(x._2, AppConfig.persistenceModule.persistenceDIModule.reader)))
      })
    }
  }

}


/**
 * Resource load instructions
 */
object ResourceDirectives {

  trait ResourceDirective[+T] extends KolibriSerializable {
    def resourceId: String
    def resourceType: ResourceType[T]
    def expirePolicy: ExpirePolicy
    def getResource: T
  }

  case class GetJudgementsResourceDirective(resourceId: String, resourceType: ResourceType[Map[String, Double]], dataSupplier: SerializableSupplier[Map[String, Double]], expirePolicy: ExpirePolicy = ExpirePolicy.ON_JOB_END) extends ResourceDirective[Map[String, Double]] {
    override def getResource: Map[String, Double] = dataSupplier.apply()
  }

  case class GetMappings(resourceId: String, resourceType: ResourceType[Map[String, IndexedGenerator[String]]], dataSupplier: SerializableSupplier[Map[String, IndexedGenerator[String]]], expirePolicy: ExpirePolicy = ExpirePolicy.ON_JOB_END) extends ResourceDirective[Map[String, IndexedGenerator[String]]] {
    override def getResource: Map[String, IndexedGenerator[String]] = dataSupplier.apply()
  }

  def getGetJudgementsResourceDirectiveByFile(file: String): ResourceDirective[Map[String, Double]] = {
    GetJudgementsResourceDirective(s"$file", ResourceType.JUDGEMENTS, LoadFunctions.judgementsFromFile(file))
  }

  def getMappingsFromFolderWithKeyFromFileNameAndValuesFromContent(folder: String, fileSuffix: String): ResourceDirective[Map[String, IndexedGenerator[String]]] = {
    GetMappings(s"$folder", ResourceType.KEY_VALUES_MAPPINGS, LoadFunctions.mappingsFromFolderWithKeyFromFileNameAndValuesFromContent(folder, fileSuffix))
  }

  def getMappingsFromCsvFile(file: String, columnDelimiter: String, keyColumnIndex: Int, valueColumnIndex: Int): ResourceDirective[Map[String, IndexedGenerator[String]]] = {
    GetMappings(s"$file", ResourceType.KEY_VALUES_MAPPINGS, LoadFunctions.mappingsFromCsvFile(file, columnDelimiter, keyColumnIndex, valueColumnIndex))
  }

  def getJsonArrayMappingsFromFile(file: String): ResourceDirective[Map[String, IndexedGenerator[String]]] = {
    GetMappings(s"$file", ResourceType.KEY_VALUES_MAPPINGS, LoadFunctions.jsonArrayMappingsFromFile(file))
  }

  def getJsonSingleMappingsFromFile(file: String): ResourceDirective[Map[String, IndexedGenerator[String]]] = {
    GetMappings(s"$file", ResourceType.KEY_VALUES_MAPPINGS, LoadFunctions.jsonSingleMappingsFromFile(file))
  }

  def getJsonValuesFilesMapping(keyToValueJsonMap: Map[String, String]): ResourceDirective[Map[String, IndexedGenerator[String]]] = {
    GetMappings(s"maphash-${keyToValueJsonMap.hashCode()}", ResourceType.KEY_VALUES_MAPPINGS, LoadFunctions.jsonValuesFilesMapping(keyToValueJsonMap))
  }


}
