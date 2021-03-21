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

package de.awagen.kolibri.datatypes.io

import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, GridOrderedMultiValuesBatch}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.collection.immutable.Seq


/**
  * Trait providing implicit Reads to transform JsValue objects relevant to experiments into Json
  * In case the base is a json string, Json.parse(jsonString) provides the respective JsValue
  */
trait ValueReads {

  /**
    * Reads[RangeValues[Float]] as used when matching JsValue to RangeValues[Float] object
    */
  implicit val rangeValuesFloatReads: Reads[RangeValues[Float]] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "start").read[Float] and
      (JsPath \ "end").read[Float] and
      (JsPath \ "stepSize").read[Float]
    ) (RangeValues.apply(_, _, _, _))


  /**
    * Reads[RangeValues[Double]] as used when matching JsValue to RangeValues[Double] object
    */
  implicit val rangeValuesDoubleReads: Reads[RangeValues[Double]] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "start").read[Double] and
      (JsPath \ "end").read[Double] and
      (JsPath \ "stepSize").read[Double]
    ) (RangeValues.apply(_, _, _, _))


  /**
    * Reads[DistinctStringValues] as used when matching JsValue to DistinctStringValues object
    */
  implicit val distinctStringValuesReads: Reads[DistinctValues[String]] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "values").read[Seq[String]]
    ) (DistinctValues.apply[String] _)


  implicit object orderedValuesReads extends Reads[OrderedValues[Any]] {
    def reads(json: JsValue): JsResult[OrderedValues[Any]] = {
      json match {
        case e: JsValue if e.validate[RangeValues[Float]].isSuccess => json.validate[RangeValues[Float]].asInstanceOf[JsResult[OrderedValues[Any]]]
        case e: JsValue if e.validate[RangeValues[Double]].isSuccess => json.validate[RangeValues[Double]].asInstanceOf[JsResult[OrderedValues[Any]]]
        case _ => json.validate[DistinctValues[String]].asInstanceOf[JsResult[OrderedValues[Any]]]
      }
    }
  }


  implicit def orderValuesSeqReads: Reads[Seq[OrderedValues[Any]]] = {
    case e: JsArray => JsSuccess[Seq[OrderedValues[Any]]](e.value.map(x => x.asOpt[OrderedValues[Any]].get).toSeq)
    case _ => throw new RuntimeException("Seq[OrderedValues] MUST be a Seq")
  }


  /**
    * Reads[GridOrderedMultiValues] as used when matching JsValue to GridOrderedMultiValues object
    * Note that for single-parameter case classes, the map method is needed
    */
  implicit val gridOrderedMultiValuesReads: Reads[GridOrderedMultiValues] = (JsPath \ "params").read[Seq[OrderedValues[Any]]].map(GridOrderedMultiValues)


  /**
    * Reads[GridExperimentBatch] as used when matching JsValue to GridExperimentBatch object
    */
  implicit val gridOrderedMultiValuesBatchReads: Reads[GridOrderedMultiValuesBatch] = (
    (JsPath \ "multiValues").read[GridOrderedMultiValues] and
      (JsPath \ "batchSize").read[Int] and
      (JsPath \ "batchNr").read[Int]
    ) (GridOrderedMultiValuesBatch.apply _)

}
