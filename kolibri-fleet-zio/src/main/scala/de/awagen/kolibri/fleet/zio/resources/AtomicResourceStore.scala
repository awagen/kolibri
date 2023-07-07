/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.resources

import de.awagen.kolibri.datatypes.AtomicMapPromiseStore
import de.awagen.kolibri.definitions.directives.Resource
import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective


case class AtomicResourceStore() extends AtomicMapPromiseStore[Resource[Any], Any, ResourceDirective[Any]] {

  /**
   * Function to generate value for a passed key. Note that ensuring thread-safety and only single executions
   * is ensured in the above methods, so on extension only need to override this function with the plain
   * generation of the value (e.g file / db access or the like)
   *
   * @param key key value
   * @return generated value
   */
  override def calculateValue(key: ResourceDirective[Any]): Any = key.getResource


  override def retrievalObjectToKeyFunc: ResourceDirective[Any] => Resource[Any] = x => x.resource
}
