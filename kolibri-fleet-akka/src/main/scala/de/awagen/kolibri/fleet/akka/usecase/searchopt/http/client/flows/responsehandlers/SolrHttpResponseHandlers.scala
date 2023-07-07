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

package de.awagen.kolibri.fleet.akka.usecase.searchopt.http.client.flows.responsehandlers

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.fleet.akka.http.client.response.HttpResponseHandlers
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}


object SolrHttpResponseHandlers {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def httpResponseToTypeTaggedMapParseFunc(validationFunc: JsValue => Boolean,
                                           parseFunc: SerializableFunction1[JsValue, WeaklyTypedMap[String]])(implicit actorSystem: ActorSystem, mat: Materializer,
                                                                                                    ec: ExecutionContext): HttpResponse => Future[Either[Throwable, WeaklyTypedMap[String]]] = {
    x =>
      logger.debug(s"received http response entity: ${x.entity}")
      HttpResponseHandlers.doJSValueParse[WeaklyTypedMap[String]](
        response = x,
        isValidFunc = validationFunc,
        parseFunc = parseFunc)
  }

}
