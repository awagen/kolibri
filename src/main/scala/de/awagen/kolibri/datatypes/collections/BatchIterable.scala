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

package de.awagen.kolibri.datatypes.collections

import de.awagen.kolibri.datatypes.io.KolibriSerializable

/**
  * Trait to allow passing an iterable, and based on some maximal element size (batchSize)
  * iterate through batches of at most batchSize sub-iterators of the original
  *
  * @tparam T
  */
trait BatchIterable[+T] extends Iterable[T] with KolibriSerializable {

  val batchSize: Int

  def iterator: Iterator[T]

}
