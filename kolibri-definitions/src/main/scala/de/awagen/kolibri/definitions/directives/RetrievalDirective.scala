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


package de.awagen.kolibri.definitions.directives

import de.awagen.kolibri.datatypes.io.KolibriSerializable


object RetrievalDirective {

  /**
   * Specifies how and which subset of data from ResourceDirective to load
   */
  trait RetrievalDirective[+T] extends KolibriSerializable {
    def resource: Resource[T]
  }

  case class Retrieve[+T](resource: Resource[T]) extends RetrievalDirective[T]

}
