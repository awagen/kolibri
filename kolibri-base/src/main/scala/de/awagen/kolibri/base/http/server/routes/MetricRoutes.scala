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


package de.awagen.kolibri.base.http.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher0, Route}
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{IntJsonFormat, JsValueFormat, RootJsObjectFormat, StringJsonFormat, immSeqFormat, mapFormat, vectorFormat}
import spray.json._

object MetricRoutes {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val METRICS_PATH_PREFIX = "irmetrics"
  val ALL_PATH = "all"
  val REDUCED_TO_FULL_JSON_PATH = "reducedToFullJson"
  val TYPE_FIELD = "type"
  val K_FIELD = "k"
  val K_TYPE_FIELD = "k_type"
  val THRESHOLD_FIELD = "threshold"
  val THRESHOLD_TYPE_FIELD = "threshold_type"
  val NDCG_VALUE = "NDCG"
  val PRECISION_VALUE = "PRECISION"
  val ERR_VALUE = "ERR"
  val INT_TYPE = "INT"
  val FLOAT_TYPE = "FLOAT"
  val NAME_FIELD = "name"
  val FUNCTION_FIELD = "function"
  val TYPE_SUFFIX = "_type"

  /**
   * Endpoint listing all available information retrieval metrics in reduced format.
   * A separate endpoint (see below) transforms this into the json definitions
   * needed by the job definition.
   * Note that calculations can also be flexibly defined based on fields extracted
   * from responses via json selectors (see job definitions for more details)
   *
   * @param system
   * @return
   */
  def getAvailableIRMetrics(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = METRICS_PATH_PREFIX / ALL_PATH
    corsHandler(
      path(matcher) {
        // reduced definition just containing type and parameters to set
        val ndcgJson = new JsObject(Map(TYPE_FIELD -> JsString(NDCG_VALUE), K_FIELD -> JsNumber(2), K_TYPE_FIELD -> JsString(INT_TYPE)))
        val precisionJson = new JsObject(Map(TYPE_FIELD -> JsString(PRECISION_VALUE), K_FIELD -> JsNumber(2), K_TYPE_FIELD -> JsString(INT_TYPE), THRESHOLD_FIELD -> JsNumber(0.1), THRESHOLD_TYPE_FIELD -> JsString(FLOAT_TYPE)))
        val errJson = new JsObject(Map(TYPE_FIELD -> JsString(ERR_VALUE), K_FIELD -> JsNumber(2), K_TYPE_FIELD -> JsString(INT_TYPE)))
        complete(StatusCodes.OK, Seq(ndcgJson, precisionJson, errJson).toJson.toString())
      }
    )
  }

  /**
   * being passed an array of reduced metric representations, wraps the filtered jsons
   * (removing non-needed fields that only serve informational purposes such as defining type
   * information for values) as value of "function" key and adds a value for "name" key,
   * as required by the job definition
   *
   * @param system
   * @return
   */
  def getIRMetricJsonsFromReducedJsons(implicit system: ActorSystem): Route = {
    val matcher: PathMatcher0 = METRICS_PATH_PREFIX / REDUCED_TO_FULL_JSON_PATH
    corsHandler(
      path(matcher) {
        post {
          entity(as[String]) { json => {
            val values: JsArray = json.parseJson.asInstanceOf[JsArray]
            val metricArrayJson: JsValue = values.elements.map(metric => {
              val fields: Map[String, JsValue] = metric.asJsObject.fields
              val filteredJson: JsValue = fields.toSeq
                .filter(x => !x._1.endsWith(TYPE_SUFFIX))
                .toMap.toJson
              val typeValue = fields(TYPE_FIELD).convertTo[String]
              val k = fields(K_FIELD).convertTo[Int]
              val thresholdOpt = fields.get(THRESHOLD_FIELD)
              var metricName = s"$typeValue@k=$k"
              thresholdOpt.foreach(t => metricName = s"$metricName@t=$t")
              new JsObject(Map(NAME_FIELD -> JsString(metricName), FUNCTION_FIELD -> filteredJson))
            }).toJson
            complete(StatusCodes.OK, metricArrayJson.toString())
          }
          }
        }
      }
    )
  }

}
