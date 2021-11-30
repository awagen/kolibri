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


package de.awagen.kolibri.base.io.writer

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object Writers {

  /**
    * Generic writer trait
    *
    * @tparam U - data to be persisted
    * @tparam V - targetIdentifier, e.g filename for FileWriter
    * @tparam W - return value on successful write completion
    */
  trait Writer[U, V, +W] extends KolibriSerializable {

    def write(data: U, targetIdentifier: V): Either[Exception, W]

  }

  /**
    * Writer where targetIdentifier is giving the filename to write to
    *
    * @tparam T - data to be persisted
    * @tparam U - return value on successful write completion
    */
  trait FileWriter[T, +U] extends Writer[T, String, U]

}
