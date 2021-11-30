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

import de.awagen.kolibri.datatypes.multivalues.GridOrderedMultiValues
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import play.api.libs.json.JsValue

object ValuesIO extends ValueReads with BaseIO {

  /**
    * Transform JsValue object to GridExperiment as given by the above implicit Reads[GridExperiment]
    *
    * @param jsValue
    * @return
    */
  def jsValueToGridOrderedMultiValues(jsValue: JsValue): GridOrderedMultiValues = {
    jsValue.validate[GridOrderedMultiValues].get
  }

  /**
    * Transform JsValue object to OrderedValues as given by the above implicit Reads[OrderedValues]
    *
    * @param jsValue
    * @return
    */
  def jsValueToOrderedValues(jsValue: JsValue): OrderedValues[Any] = {
    jsValue.validate[OrderedValues[Any]].get
  }

  def jsValueToDistinctStringValues(jsValue: JsValue): DistinctValues[String] = {
    jsValue.validate[DistinctValues[String]].get
  }

  def jsValueToFloatRangeValues(jsValue: JsValue): RangeValues[Float] = {
    jsValue.validate[RangeValues[Float]].get
  }

  def jsValueToDoubleRangeValues(jsValue: JsValue): RangeValues[Double] = {
    jsValue.validate[RangeValues[Double]].get
  }

}
