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

package de.awagen.kolibri.datatypes

import de.awagen.kolibri.datatypes.io.KolibriSerializable

import scala.reflect.runtime.universe._


object ClassTyped {

  def apply[T: TypeTag] = new ClassTyped[T]

}

class ClassTyped[+T: TypeTag] extends KolibriSerializable {

  val classType: Type = implicitly[TypeTag[T]].tpe
  val castFunc: Any => T = x => x.asInstanceOf[T]

}


