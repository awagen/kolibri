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

package de.awagen.kolibri.fleet.akka.actors.flows

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate

import scala.util.{Failure, Try}

object FlowTestHelper {

  def hostConnectionPoolFlowStub[T](requestToResponseTryMap: Map[HttpRequest, Try[HttpResponse]]): Flow[(HttpRequest, RequestTemplate), (Try[HttpResponse], RequestTemplate), _] =
    Flow.fromFunction(
      x => {
        (requestToResponseTryMap.getOrElse(x._1, Failure(new RuntimeException(s"Did not provide Try[HttpResponse] for HttpRequest ${x._1}"))), x._2)
      }
    )

}
