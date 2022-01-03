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

package de.awagen.kolibri.datatypes.multivalues

/**
 * Trait adding batchSize, batchNr and batchStartElement (shift of indices relative to the original OrderedMultiValues,
 * specifying at which element of the original the batch starts) to OrderedMultiValues
 */
trait OrderedMultiValuesBatch extends OrderedMultiValues  {

  val batchSize: Int
  val batchNr: Int
  val batchStartElement: Int

  override def originalValueIndexOf(n: Int): Int = {
    batchStartElement + n
  }

}
