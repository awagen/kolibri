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

package de.awagen.kolibri.base.actors.flows

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.http.client.request.{HttpRequestProvider, RequestTemplate}

import scala.collection.immutable
import scala.util.{Failure, Try}

object FlowTestHelper {

  def hostConnectionPoolFlowStub[V <: HttpRequestProvider, T](requestToResponseTryMap: Map[HttpRequest, Try[HttpResponse]]): Flow[(HttpRequest, V), (Try[HttpResponse], V), _] =
    Flow.fromFunction(
      x => {
        (requestToResponseTryMap.getOrElse(x._1, Failure(new RuntimeException(s"Did not provide Try[HttpResponse] for HttpRequest ${x._1}"))), x._2)
      }
    )

//  def createRequestContext[T](number: Int, params: Map[String, Seq[String]]): RequestTemplate = {
//    new RequestTemplate(contextPath = "", parameters = params) {
//      override def getContextHeaders: immutable.Seq[Nothing] = immutable.Seq.empty
//    }
//  }
//
//  def copyRequestContextWithProcessor(context: RequestTemplate): RequestTemplate = {
//    new RequestTemplate(contextPath = context.contextPath, parameters = context.parameters) {
//      override def getContextHeaders: immutable.Seq[Nothing] = immutable.Seq.empty
//    }
//  }
}
