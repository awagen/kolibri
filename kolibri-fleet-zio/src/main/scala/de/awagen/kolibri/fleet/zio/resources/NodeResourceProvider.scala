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
import de.awagen.kolibri.definitions.directives.RetrievalDirective.RetrievalDirective
import de.awagen.kolibri.definitions.resources.{ResourceNotFound, ResourceProvider, RetrievalError, RetrievalFailedByException}

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, SECONDS}


class NodeResourceProvider(resourceStore: AtomicMapPromiseStore[Resource[Any], Any, ResourceDirective[Any]],
                            waitTimeInSeconds: Int) extends ResourceProvider {

  def getResource[T](directive: RetrievalDirective[T]): Either[RetrievalError[T], T] = {
    resourceStore.get(directive.resource)
      .map(x => {
        try {
          Right(Await.result(x.future, FiniteDuration(waitTimeInSeconds, SECONDS)).asInstanceOf[T])
        }
        catch {
          case e: Exception => Left(RetrievalError(directive, RetrievalFailedByException(e)))
        }
      })
      .getOrElse(Left(RetrievalError(directive, ResourceNotFound)))
  }

}
