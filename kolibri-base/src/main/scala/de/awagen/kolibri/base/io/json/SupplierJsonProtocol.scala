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

import de.awagen.kolibri.base.cluster.ClusterNode
import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.config.AppConfig.filepathToJudgementProvider
import de.awagen.kolibri.base.directives.RetrievalDirective.Retrieve
import de.awagen.kolibri.base.directives.{Resource, ResourceType}
import de.awagen.kolibri.base.io.json.OrderedValuesJsonProtocol.OrderedValuesStringFormat
import de.awagen.kolibri.base.io.reader.FileReaderUtils
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.{CachedSupplier, SerializableSupplier}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.OrderedValues
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import scala.io.Source

object SupplierJsonProtocol extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val TYPE_KEY = "type"
  val VALUE_KEY = "value"
  val FILE_KEY = "file"
  val FROM_JSON_TYPE = "FROM_JSON"
  val FROM_JSON_FILE_TYPE = "FROM_JSON_FILE"

  val NAME_KEY = "name"
  val VALUES_KEY = "values"
  val IDENTIFIER_KEY = "identifier"
  val COLUMN_DELIMITER_KEY = "column_delimiter"
  val KEY_COLUMN_INDEX_KEY = "key_column_index"
  val VALUE_COLUMN_INDEX_KEY = "value_column_index"
  val DIRECTORY_KEY = "directory"
  val FILES_SUFFIX_KEY = "files_suffix"
  val PARAMETER_VALUES_TYPE = "PARAMETER_VALUES_TYPE"
  val VALUES_FROM_NODE_STORAGE_TYPE = "VALUES_FROM_NODE_STORAGE"
  val FROM_ORDERED_VALUES_TYPE = "FROM_ORDERED_VALUES_TYPE"
  val JSON_VALUES_MAPPING_TYPE = "JSON_VALUES_MAPPING_TYPE"
  val JSON_VALUES_FILES_MAPPING_TYPE = "JSON_VALUES_FILES_MAPPING_TYPE"
  val JSON_SINGLE_MAPPINGS_TYPE = "JSON_SINGLE_MAPPINGS_TYPE"
  val JSON_ARRAY_MAPPINGS_TYPE = "JSON_ARRAY_MAPPINGS_TYPE"
  val CSV_MAPPINGS_TYPE = "CSV_MAPPING_TYPE"
  val FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE = "FILE_PREFIX_TO_FILE_LINES_TYPE"

  def jsValueStringConversion(jsValue: JsValue): String = jsValue match {
    case e: JsString => e.convertTo[String]
    case e => e.toString()
  }

  implicit object StringSeqMappingFormat extends JsonFormat[() => Map[String, Seq[String]]] with WithStructDef {
    override def read(json: JsValue): () => Map[String, Seq[String]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_JSON_TYPE =>
          new SerializableSupplier[Map[String, Seq[String]]]() {
            override def apply(): Map[String, Seq[String]] = fields(VALUE_KEY).convertTo[Map[String, Seq[String]]]
          }
        case FROM_JSON_FILE_TYPE =>
          val file = fields(FILE_KEY).convertTo[String]
          val persistenceModule = AppConfig.persistenceModule
          val source: Source = persistenceModule.persistenceDIModule.reader.getSource(file)
          val mapping = source.getLines().mkString("\n").parseJson.convertTo[Map[String, Seq[String]]]
          new SerializableSupplier[Map[String, Seq[String]]]() {
            override def apply(): Map[String, Seq[String]] = mapping
          }
      }
      case e => throw DeserializationException(s"Expected a valid value vor () => Map[String, Seq[String]] but got value $e")
    }

    override def write(obj: () => Map[String, Seq[String]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              FROM_JSON_TYPE,
              FROM_JSON_FILE_TYPE
            )),
            required = true
          )
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_JSON_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(VALUE_KEY),
                  MapStructDef(StringStructDef, StringSeqStructDef),
                  required = true
                )
              ),
              FROM_JSON_FILE_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(FILE_KEY),
                  StringStructDef,
                  required = true
                )
              )
            )
          )
        )
      )
    }
  }

  /**
   * Json protocol for retrieval of suppliers of IndexedGenerator[String]
   */
  implicit object GeneratorStringFormat extends JsonFormat[SerializableSupplier[IndexedGenerator[String]]] with WithStructDef {
    override def read(json: JsValue): SerializableSupplier[IndexedGenerator[String]] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_ORDERED_VALUES_TYPE =>
          val values = fields(VALUES_KEY).convertTo[OrderedValues[String]]
          val supplier = new SerializableSupplier[IndexedGenerator[String]] {
            override def apply(): IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(values.getAll)
          }
          CachedSupplier(supplier)
        case PARAMETER_VALUES_TYPE =>
          val values = fields(VALUES_KEY).convertTo[Seq[String]]
          val supplier = new SerializableSupplier[IndexedGenerator[String]] {
            override def apply(): IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(values)
          }
          CachedSupplier(supplier)
        case VALUES_FROM_NODE_STORAGE_TYPE =>
          val identifier: String = fields(IDENTIFIER_KEY).convertTo[String]
          val resource: Resource[IndexedGenerator[String]] = Resource(ResourceType.STRING_VALUES, identifier)
          val supplier = new SerializableSupplier[IndexedGenerator[String]] {
            override def apply(): IndexedGenerator[String] = {
              ClusterNode.getResource(Retrieve(resource)) match {
                case Left(retrievalError) =>
                  throw new RuntimeException(s"failed on execution of RetrievalDirective '${retrievalError.directive}', cause: '${retrievalError.cause}'")
                case Right(value) => value
              }
            }
          }
          CachedSupplier(supplier)
      }
    }

    override def write(obj: SerializableSupplier[IndexedGenerator[String]]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(Seq(
            FROM_ORDERED_VALUES_TYPE,
            PARAMETER_VALUES_TYPE,
            VALUES_FROM_NODE_STORAGE_TYPE
          )),
          required = true)
      ),
      Seq(
        ConditionalFields(
          TYPE_KEY,
          Map(
            FROM_ORDERED_VALUES_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                OrderedValuesStringFormat.structDef,
                required = true
              )
            ),
            PARAMETER_VALUES_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringSeqStructDef,
                required = true
              )
            ),
            VALUES_FROM_NODE_STORAGE_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(IDENTIFIER_KEY),
                StringStructDef,
                required = true
              )
            )
          )
        )
      )
    )
  }

  implicit object MapStringDoubleFormat extends JsonFormat[SerializableSupplier[Map[String, Double]]] with WithStructDef {
    val JUDGEMENTS_FROM_FILE_TYPE = "JUDGEMENTS_FROM_FILE"

    override def read(json: JsValue): SerializableSupplier[Map[String, Double]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case JUDGEMENTS_FROM_FILE_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          val supplier = new SerializableSupplier[Map[String, Double]] {
            override def apply(): Map[String, Double] = {
              filepathToJudgementProvider(file).allJudgements
            }
          }
          CachedSupplier(supplier)
      }

    }

    override def write(obj: SerializableSupplier[Map[String, Double]]): JsValue = """{}""".toJson

    override def structDef: StructDef[_] = ???
  }

  /**
   * Format for suppliers of mappings, defined by the values (Seq[String]) that hold for a given key
   */
  implicit object MapStringToGeneratorStringFormat extends JsonFormat[SerializableSupplier[Map[String, IndexedGenerator[String]]]] with WithStructDef {
    override def read(json: JsValue): SerializableSupplier[Map[String, IndexedGenerator[String]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        // this case assumes passing of the key values to valid values for the parameter
        case JSON_VALUES_MAPPING_TYPE =>
          val mappings = fields(VALUES_KEY).convertTo[Map[String, Seq[String]]]
          val supplier = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
            override def apply(): Map[String, IndexedGenerator[String]] = {
              mappings.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
            }
          }
          CachedSupplier(supplier)
        // this case assumes a json for the values key, containing mapping
        // of key values to files containing the valid values for the key
        case JSON_VALUES_FILES_MAPPING_TYPE =>
          val fileMappings = fields(VALUES_KEY).convertTo[Map[String, String]]
          val supplier = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
            override def apply(): Map[String, IndexedGenerator[String]] = {
              fileMappings.map(x => {
                (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(FileReaderUtils.loadLinesFromFile(x._2, AppConfig.persistenceModule.persistenceDIModule.reader)))
              })
            }
          }
          CachedSupplier(supplier)
        // assumes a json with full key value mappings under the values key
        case JSON_SINGLE_MAPPINGS_TYPE =>
          val mappingsJsonFile = fields(VALUES_KEY).convertTo[String]
          val supplier = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
            override def apply(): Map[String, IndexedGenerator[String]] = {
              val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
              FileReaderUtils.readJsonMapping(mappingsJsonFile, fileReader, x => jsValueStringConversion(x))
                .map(x => (x._1, Seq(x._2)))
                .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
            }
          }
          CachedSupplier(supplier)
        case JSON_ARRAY_MAPPINGS_TYPE =>
          try {
            val mappingsJsonFile = fields(VALUES_KEY).convertTo[String]
            val supplier = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
              val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader

              override def apply(): Map[String, IndexedGenerator[String]] = {
                FileReaderUtils.readJsonMapping(mappingsJsonFile, fileReader, x => x.convertTo[Seq[JsValue]].map(x => jsValueStringConversion(x)))
                  .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
              }
            }
            CachedSupplier(supplier)
          }
          catch {
            case e: Throwable => logger.error("failed reading json file of format 'JSON_ARRAY_MAPPINGS_TYPE'", e)
              throw e
          }
        // reading file prefixes in folder, and creating mapping for each where prefix is key and values are the values
        // given in the file, one value per line
        case FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE =>
          val directory = fields(DIRECTORY_KEY).convertTo[String]
          val filesSuffix: String = fields(FILES_SUFFIX_KEY).convertTo[String]
          val supplier = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
            override def apply(): Map[String, IndexedGenerator[String]] = {
              FileReaderUtils.extractFilePrefixToLineValuesMapping(directory, filesSuffix, "/")
                .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
            }
          }
          CachedSupplier(supplier)
        // picking mappings from csv with a key column and a value column. If multiple distinct values are contained
        // for a key, they will be preserved as distinct values in the generator per key value
        case CSV_MAPPINGS_TYPE =>
          val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
          val mappingsCsvFile = fields(VALUES_KEY).convertTo[String]
          val columnDelimiter = fields(COLUMN_DELIMITER_KEY).convertTo[String]
          val keyColumnIndex = fields(KEY_COLUMN_INDEX_KEY).convertTo[Int]
          val valueColumnIndex = fields(VALUE_COLUMN_INDEX_KEY).convertTo[Int]
          val keyValuesMapping = FileReaderUtils.multiMappingFromCSVFile[String](
            source = fileReader.getSource(mappingsCsvFile),
            columnDelimiter = columnDelimiter,
            filterLessColumnsThan = math.max(keyColumnIndex, valueColumnIndex) + 1,
            valsToKey = x => x(keyColumnIndex),
            columnsToValue = x => x(valueColumnIndex))
          val supplier = new SerializableSupplier[Map[String, IndexedGenerator[String]]] {
            override def apply(): Map[String, IndexedGenerator[String]] = {
              keyValuesMapping.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2.toSeq)))
            }
          }
          CachedSupplier(supplier)
      }
    }

    override def write(obj: SerializableSupplier[Map[String, IndexedGenerator[String]]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              JSON_VALUES_MAPPING_TYPE,
              JSON_VALUES_FILES_MAPPING_TYPE,
              JSON_SINGLE_MAPPINGS_TYPE,
              JSON_ARRAY_MAPPINGS_TYPE,
              FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE,
              CSV_MAPPINGS_TYPE
            )),
            required = true
          )
        ),
        Seq(
          ConditionalFields(TYPE_KEY, Map(
            JSON_VALUES_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                MapStructDef(
                  StringStructDef,
                  StringSeqStructDef
                ),
                required = true
              )
            ),
            JSON_VALUES_FILES_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                MapStructDef(
                  StringStructDef,
                  StringStructDef
                ),
                required = true
              )
            ),
            JSON_SINGLE_MAPPINGS_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringStructDef,
                required = true
              )
            ),
            JSON_ARRAY_MAPPINGS_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringStructDef,
                required = true
              )
            ),
            FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(DIRECTORY_KEY),
                StringStructDef,
                required = true
              ),
              FieldDef(
                StringConstantStructDef(FILES_SUFFIX_KEY),
                StringStructDef,
                required = true
              )
            ),
            CSV_MAPPINGS_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringStructDef,
                required = true
              ),
              FieldDef(
                StringConstantStructDef(COLUMN_DELIMITER_KEY),
                StringStructDef,
                required = true
              ),
              FieldDef(
                StringConstantStructDef(KEY_COLUMN_INDEX_KEY),
                IntMinMaxStructDef(0, Int.MaxValue),
                required = true
              ),
              FieldDef(
                StringConstantStructDef(VALUE_COLUMN_INDEX_KEY),
                IntMinMaxStructDef(0, Int.MaxValue),
                required = true
              )
            )
          ))
        )
      )
    }
  }


}
