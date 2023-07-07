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

package de.awagen.kolibri.definitions.tracking

object RequestTrackingContext {

  def apply(): RequestTrackingContext = new RequestTrackingContext()

}


/**
  * Context used to track request attributes, such as startTime, start of first byte read,
  * full response read end time, status code and request fail reason
  */
class RequestTrackingContext {

  private var timestampStartInMs: Option[Long] = None
  private var timestampResponseReadyInMs: Option[Long] = None
  private var timestampEndResponseReadTimeInMs: Option[Long] = None
  private var responseStatusCode: Option[String] = None
  private var requestFailReason: Option[String] = None

  def getStartTimeInMs: Option[Long] = timestampStartInMs
  def getStartResponseReadTimeInMs: Option[Long] = timestampResponseReadyInMs
  def getEndResponseReadTimeInMs: Option[Long] = timestampEndResponseReadTimeInMs
  def getResponseStatusCode: Option[String] = responseStatusCode
  def getRequestFailReason: Option[String] = requestFailReason


  def withStartTime(startTimeInMillis: Long): RequestTrackingContext = {
    timestampStartInMs = Some(startTimeInMillis)
    this
  }

  def withStartResponseReadTimeInMs(startReadTimeInMillis: Long): RequestTrackingContext = {
    timestampResponseReadyInMs = Some(startReadTimeInMillis)
    this
  }

  def withEndResponseReadTimeInMs(endReadTimeInMillis: Long): RequestTrackingContext = {
    timestampEndResponseReadTimeInMs = Some(endReadTimeInMillis)
    this
  }

  def withStatusCode(code: String): RequestTrackingContext = {
    this.responseStatusCode = Some(code)
    this
  }

  def withRequestFailReason(reason: String): RequestTrackingContext = {
    this.requestFailReason = Some(reason)
    this
  }

  def timeRequestToResponseReady(): Option[Long] = {
    for {
      end <- timestampResponseReadyInMs
      start <- timestampStartInMs
    } yield end - start
  }

  def timeRequestToResponseConsumed: Option[Long] = {
    for {
      end <- timestampEndResponseReadTimeInMs
      start <- timestampStartInMs
    } yield end - start
  }

  def timeResponeReadyToResponseConsumed: Option[Long] = {
    for {
      end <- timestampEndResponseReadTimeInMs
      start <- timestampResponseReadyInMs
    } yield end - start
  }


}
