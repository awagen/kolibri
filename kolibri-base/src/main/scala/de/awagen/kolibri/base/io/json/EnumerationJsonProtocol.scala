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

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingResult
import de.awagen.kolibri.base.directives.ResourceType
import de.awagen.kolibri.base.directives.ResourceType.{GeneratorStringResourceType, MAP_STRING_TO_DOUBLE_VALUE, MAP_STRING_TO_STRING_VALUES, MapStringDoubleResourceType, MapStringGeneratorStringResourceType, ResourceType, STRING_VALUES}
import de.awagen.kolibri.base.domain.TaskDataKeys
import de.awagen.kolibri.base.domain.jobdefinitions.ProcessingActorProps.ProcessingActorProps
import de.awagen.kolibri.base.domain.jobdefinitions.RunnableExpectationGenerators.ExpectationGenerators
import de.awagen.kolibri.base.domain.jobdefinitions.TaskDefinitions.TaskDefinitions
import de.awagen.kolibri.base.domain.jobdefinitions.provider.data.DataKeys
import de.awagen.kolibri.base.domain.jobdefinitions.{MapTransformerFlows, ProcessingActorProps, RunnableExpectationGenerators, TaskDefinitions}
import de.awagen.kolibri.base.http.server.routes.DataRoutes.DataFileType
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueType
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.EnumerationProtocol
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.types.JsonStructDefs.StringChoiceStructDef
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue}


object EnumerationJsonProtocol extends DefaultJsonProtocol {

  implicit object valueTypeFormat extends EnumerationProtocol[ValueType.Value] with WithStructDef {
    override def read(json: JsValue): ValueType.Value = {
      json match {
        case JsString(txt) => ValueType.withName(txt.toUpperCase)
        case e => throw DeserializationException(s"Expected a value from ValueType.Value but got value $e")
      }
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      StringChoiceStructDef(Seq(
        ValueType.BODY.toString,
        ValueType.BODY_REPLACE.toString,
        ValueType.HEADER.toString,
        ValueType.HEADER_REPLACE.toString,
        ValueType.URL_PARAMETER.toString,
        ValueType.URL_PARAMETER_REPLACE.toString,
      ))
    }
  }

  implicit object processingResultFormat extends EnumerationProtocol[ProcessingResult.Value] {
    override def read(json: JsValue): ProcessingResult.Value = {
      json match {
        case JsString(txt) => ProcessingResult.withName(txt)
        case e => throw DeserializationException(s"Expected a value from ProcessingResult.Value but got value $e")
      }
    }
  }

  implicit object dataFileTypeFormat extends EnumerationProtocol[DataFileType.Val] {
    override def read(json: JsValue): DataFileType.Val = {
      json match {
        case JsString(txt) => DataFileType.byName(txt)
        case e => throw DeserializationException(s"Expected a value from DataFileType.Val but got value $e")
      }
    }
  }

  implicit object expectationGeneratorFormat extends EnumerationProtocol[ExpectationGenerators] {
    override def read(json: JsValue): ExpectationGenerators = {
      json match {
        case JsString(txt) => RunnableExpectationGenerators.withName(txt).asInstanceOf[ExpectationGenerators]
        case e => throw DeserializationException(s"Expected a value from ExpectationGenerators but got value $e")
      }
    }
  }

  implicit object processingActorPropsFormat extends EnumerationProtocol[ProcessingActorProps] {
    override def read(json: JsValue): ProcessingActorProps = {
      json match {
        case JsString(txt) => ProcessingActorProps.withName(txt).asInstanceOf[ProcessingActorProps]
        case e => throw DeserializationException(s"Expected a value from ProcessingActorProps but got value $e")
      }
    }
  }

  implicit object taskDefinitionsFormat extends EnumerationProtocol[TaskDefinitions] {
    override def read(json: JsValue): TaskDefinitions = {
      json match {
        case JsString(txt) => TaskDefinitions.withName(txt).asInstanceOf[TaskDefinitions]
        case e => throw DeserializationException(s"Expected a value from TaskDefinitions but got value $e")
      }
    }
  }

  implicit object mapTransformerFlowsFormat extends EnumerationProtocol[MapTransformerFlows.Val] {
    override def read(json: JsValue): MapTransformerFlows.Val = {
      json match {
        case JsString(txt) =>
          val enumVal = MapTransformerFlows.withName(txt)
          enumVal.asInstanceOf[MapTransformerFlows.Val]
        case e => throw DeserializationException(s"Expected a value from TransformerFlows but got value $e")
      }
    }
  }

  implicit object actorRunnableSinkTyoeFormat extends EnumerationProtocol[ActorRunnableSinkType] {
    override def read(json: JsValue): ActorRunnableSinkType = {
      json match {
        case JsString(txt) => ActorRunnableSinkType.withName(txt)
        case e => throw DeserializationException(s"Expected a value from ActorRunnableSinkType but got value $e")
      }
    }
  }

  implicit object dataKeysFormat extends EnumerationProtocol[DataKeys.Val[Any]] {
    override def read(json: JsValue): DataKeys.Val[Any] = {
      json match {
        case JsString(txt) => DataKeys.withName(txt).asInstanceOf[DataKeys.Val[Any]]
        case e => throw DeserializationException(s"Expected a value from ActorRunnableSinkType but got value $e")
      }
    }
  }

  implicit object taskDataKeysFormat extends EnumerationProtocol[TaskDataKeys.Val[Any]] {
    override def read(json: JsValue): TaskDataKeys.Val[Any] = {
      json match {
        case JsString(txt) => TaskDataKeys.withName(txt).asInstanceOf[TaskDataKeys.Val[Any]]
        case e => throw DeserializationException(s"Expected a value from TaskDataKeys but got value $e")
      }
    }
  }

  implicit object metricRowTaskDataKeysFormat extends EnumerationProtocol[TaskDataKeys.Val[MetricRow]] {
    override def read(json: JsValue): TaskDataKeys.Val[MetricRow] = {
      json match {
        case JsString(txt) => TaskDataKeys.withName(txt).asInstanceOf[TaskDataKeys.Val[MetricRow]]
        case e => throw DeserializationException(s"Expected a value from TaskDataKeys.Val[MetricRow] but got value $e")
      }
    }
  }

  implicit object resourceTypeFormat extends EnumerationProtocol[ResourceType[_]] {
    override def read(json: JsValue): ResourceType[_] = {
      json match {
        case JsString(txt) => ResourceType.withName(txt).asInstanceOf[ResourceType[_]]
        case e => throw DeserializationException(s"Expected a value from ResourceType[_] but got value $e")
      }
    }
  }

  implicit object resourceTypeMapStringDoubleFormat extends EnumerationProtocol[ResourceType[Map[String, Double]]] with WithStructDef {
    override def read(json: JsValue): ResourceType[Map[String, Double]] = {
      json match {
        case JsString(txt) if ResourceType.withName(txt).isInstanceOf[MapStringDoubleResourceType] =>
          ResourceType.withName(txt).asInstanceOf[ResourceType[Map[String, Double]]]
        case e => throw DeserializationException(s"Expected a value from ResourceType[Map[String, Double]] but got value $e")
      }
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      StringChoiceStructDef(Seq(MAP_STRING_TO_DOUBLE_VALUE.toString()))
    }
  }

  implicit object resourceTypeMapStringGeneratorStringFormat extends EnumerationProtocol[ResourceType[Map[String, IndexedGenerator[String]]]] with WithStructDef {
    override def read(json: JsValue): ResourceType[Map[String, IndexedGenerator[String]]] = {
      json match {
        case JsString(txt) if ResourceType.withName(txt).isInstanceOf[MapStringGeneratorStringResourceType] =>
          ResourceType.withName(txt).asInstanceOf[ResourceType[Map[String, IndexedGenerator[String]]]]
        case e => throw DeserializationException(s"Expected a value from ResourceType[Map[String, IndexedGenerator[String]]] but got value $e")
      }
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      StringChoiceStructDef(Seq(MAP_STRING_TO_STRING_VALUES.toString()))
    }
  }

  implicit object resourceTypeGeneratorStringFormat extends EnumerationProtocol[ResourceType[IndexedGenerator[String]]] with WithStructDef {
    override def read(json: JsValue): ResourceType[IndexedGenerator[String]] = {
      json match {
        case JsString(txt) if ResourceType.withName(txt).isInstanceOf[GeneratorStringResourceType] =>
          ResourceType.withName(txt).asInstanceOf[ResourceType[IndexedGenerator[String]]]
        case e => throw DeserializationException(s"Expected a value from ResourceType[IndexedGenerator[String]] but got value $e")
      }
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      StringChoiceStructDef(Seq(STRING_VALUES.toString()))
    }
  }

}
