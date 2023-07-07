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

import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.definitions.directives.{Resource, RetrievalDirective}
import de.awagen.kolibri.definitions.resources.{RetrievalError, RetrievalFailedByException}
import org.slf4j.{Logger, LoggerFactory}
import zio.ZIO


/**
 * ZIO-specific example for a resource provider.
 * Use if something specific to ZIO is needed. Otherwise use the normal NodeResourceProvider
 * and wrap the calls accordingly.
 */
object ZIONodeResourceProvider extends ZIOResourceProvider {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * use to load global node resources. Especially useful for expensive data collection calls.
   */
  val globalNodeResourceStore: ZIOAtomicMapPromiseStore[Resource[Any], Any, ResourceDirective[Any]] =
    ZIOAtomicMapPromiseStore(AtomicResourceStore(), 2)

  /**
   * Function to retrieve a resource.
   * Note that before resources can be retrieved, they need to be loaded by using the retrieveValue function of
   * above resource store. Here we assume this has already happened at some point (e.g initial job setup)
   * so that we can just utilize the get functions of the resource store.
   */
  override def getResource[T](directive: RetrievalDirective.RetrievalDirective[T]): ZIO[Any, RetrievalError[T], T] = {
    globalNodeResourceStore.get(directive.resource).mapError(e => {
      RetrievalError(directive, RetrievalFailedByException(e))
    }).map(x => x.get.asInstanceOf[T])

  }
}
