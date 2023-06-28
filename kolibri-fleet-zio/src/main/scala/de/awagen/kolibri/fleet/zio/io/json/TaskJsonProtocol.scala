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


package de.awagen.kolibri.fleet.zio.io.json

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.types.FieldDefinitions._
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.HttpMethod
import de.awagen.kolibri.definitions.http.HttpMethod.HttpMethod
import de.awagen.kolibri.definitions.io.json.ConnectionJsonProtocol.connectionFormat
import de.awagen.kolibri.definitions.io.json.EnumerationJsonProtocol.httpMethodFormat
import de.awagen.kolibri.definitions.io.json.TaggingConfigurationsJsonProtocol
import de.awagen.kolibri.definitions.io.json.TaggingConfigurationsJsonProtocol.requestAndParsingResultTaggerConfigFormat
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.ParsingConfigJsonProtocol
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.ParsingConfigJsonProtocol.parsingConfigJsonFormat
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask
import de.awagen.kolibri.fleet.zio.execution.ZIOTask
import de.awagen.kolibri.fleet.zio.io.json.EnumerationJsonProtocol.requestModeFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.requests.RequestMode
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.requests.RequestMode.RequestMode
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat}

object TaskJsonProtocol extends DefaultJsonProtocol {

  private[json] val TYPE_KEY = "type"
  private[json] val REQUEST_AND_PARSE_VALUES_TASK_TYPE = "REQUEST_PARSE"
  private[json] val PARSING_CONFIG_KEY = "parsingConfig"
  private[json] val REQUEST_AND_PARSING_RESULT_TAGGER_KEY = "taggingConfig"
  private[json] val CONNECTIONS_KEY = "connections"
  private[json] val CONTEXT_PATH_KEY = "contextPath"
  private[json] val FIXED_PARAMS_KEY = "fixedParams"
  private[json] val HTTP_METHOD_KEY = "httpMethod"
  private[json] val SUCCESS_KEY_NAME_KEY = "successKeyName"
  private[json] val FAIL_KEY_NAME_KEY = "failKeyName"
  private[json] val REQUEST_MODE_KEY = "requestMode"


  implicit object SeqTypedMapZIOTaskFormat extends JsonFormat[Seq[ZIOTask[WeaklyTypedMap[String]]]] with WithStructDef {
    override def read(json: JsValue): Seq[ZIOTask[WeaklyTypedMap[String]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case REQUEST_AND_PARSE_VALUES_TASK_TYPE =>
          val parsingConfig: ParsingConfig = fields(PARSING_CONFIG_KEY).convertTo[ParsingConfig]
          val taggingConfig: RequestAndParsingResultTaggerConfig = fields(REQUEST_AND_PARSING_RESULT_TAGGER_KEY).convertTo[RequestAndParsingResultTaggerConfig]
          val connections: Seq[Connection] = fields(CONNECTIONS_KEY).convertTo[Seq[Connection]]
          val requestMode: RequestMode = fields(REQUEST_MODE_KEY).convertTo[RequestMode]
          val contextPath = fields(CONTEXT_PATH_KEY).convertTo[String]
          val fixedParams: Map[String, Seq[String]] = fields(FIXED_PARAMS_KEY).convertTo[Map[String, Seq[String]]]
          val httpMethod: HttpMethod = fields.get(HTTP_METHOD_KEY).map(x => x.convertTo[HttpMethod])
            .getOrElse(HttpMethod.GET)
          val successKeyName: String = fields.get(SUCCESS_KEY_NAME_KEY).map(x => x.convertTo[String])
            .getOrElse("parsedValueMap")
          val failKeyName: String = fields.get(FAIL_KEY_NAME_KEY).map(x => x.convertTo[String])
            .getOrElse("parseFail")
          requestMode match {
            // in case of request all connections mode,
            // we request all connections separately and store results for each
            case RequestMode.REQUEST_ALL_CONNECTIONS =>
              connections.indices.map(x => {
                RequestJsonAndParseValuesTask(
                  parsingConfig = parsingConfig,
                  taggingConfig = taggingConfig,
                  connectionSupplier = () => connections(x),
                  contextPath = contextPath,
                  fixedParams = fixedParams,
                  httpMethod = httpMethod.toString,
                  successKeyName = s"$successKeyName-${x + 1}",
                  failKeyName = s"$failKeyName-${x + 1}"
                )
              })
            // in case of distribute load mode, we distribute load over all connections,
            // thus only retrieve a single result for each request from any of the nodes
            case RequestMode.DISTRIBUTE_LOAD =>
              val random = new util.Random()
              val connectionSupplier = () => {
                val connectionIndex = random.between(0, connections.length)
                connections(connectionIndex)
              }
              Seq(
                RequestJsonAndParseValuesTask(
                  parsingConfig = parsingConfig,
                  taggingConfig = taggingConfig,
                  connectionSupplier = connectionSupplier,
                  contextPath = contextPath,
                  fixedParams = fixedParams,
                  httpMethod = httpMethod.toString,
                  successKeyName = successKeyName,
                  failKeyName = failKeyName
                )
              )

          }

      }
    }

    // TODO: implement
    override def write(obj: Seq[ZIOTask[WeaklyTypedMap[String]]]): JsValue = ???

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(
              Seq(REQUEST_AND_PARSE_VALUES_TASK_TYPE)
            ),
            required = true,
            description = "Name of the job"
          )
        ),
        Seq(
          ConditionalFields(TYPE_KEY, Map(
            REQUEST_AND_PARSE_VALUES_TASK_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(PARSING_CONFIG_KEY),
                ParsingConfigJsonProtocol.structDef,
                required = true,
                description = "Definition of data to parse out of the (json) response"
              ),
              FieldDef(
                StringConstantStructDef(REQUEST_AND_PARSING_RESULT_TAGGER_KEY),
                TaggingConfigurationsJsonProtocol.RequestAndParsingResultTaggerConfigFormat.structDef,
                required = true,
                description = "Definition of tagger based on request settings and result of requesting and parsing."
              ),
              FieldDef(
                StringConstantStructDef(CONNECTIONS_KEY),
                GenericSeqStructDef(
                  AppConfig.JsonFormats.connectionFormatStruct.structDef
                ),
                required = true,
                description = "Connections to send requests to. Whether all of them are queried or load is distributed among them or another mode depends on the RequestMode set."
              ),
              FieldDef(
                StringConstantStructDef(REQUEST_MODE_KEY),
                StringChoiceStructDef(
                  Seq(RequestMode.REQUEST_ALL_CONNECTIONS.toString, RequestMode.DISTRIBUTE_LOAD.toString)
                ),
                required = true,
                description = "Request mode allows selection of how requests are sent to the given connections. In REQUEST_ALL_CONNECTIONS mode, " +
                  " every connection is requested separately and results are stored separately. In DISTRIBUTE_LOAD " +
                  " load is distributed among the given connections and a single response is stored."
              ),
              FieldDef(
                StringConstantStructDef(CONTEXT_PATH_KEY),
                StringStructDef,
                required = true,
                description = "The context path when composing the request."
              ),
              FieldDef(
                StringConstantStructDef(FIXED_PARAMS_KEY),
                MapStructDef(StringStructDef, StringSeqStructDef),
                required = true,
                description = "List of url parameters, where each parameter can have multiple values."
              ),
              FieldDef(
                StringConstantStructDef(HTTP_METHOD_KEY),
                StringChoiceStructDef(Seq(HttpMethod.GET.toString, HttpMethod.PUT.toString, HttpMethod.POST.toString)),
                required = false,
                description = "The http method to be used. If not set, GET will be used."
              ),
              FieldDef(
                StringConstantStructDef(SUCCESS_KEY_NAME_KEY),
                RegexStructDef(".+".r),
                required = false,
                description = "The success key name to store computed result under if compute is successful. Note that in case" +
                  " of RequestMode REQUEST_ALL_CONNECTIONS this will be used as prefix and '-[index]' is appended, where [index] stands for 1-based increasing index." +
                  " If not set, default will be used."
              ),
              FieldDef(
                StringConstantStructDef(FAIL_KEY_NAME_KEY),
                RegexStructDef(".+".r),
                required = false,
                description = "The fail key name to store computed TaskFailType under if compute is NOT successful. Note that in case" +
                  " of RequestMode REQUEST_ALL_CONNECTIONS this will be used as prefix and '-[index]' is appended, where [index] stands for 1-based increasing index." +
                  "  If not set, default will be used."
              )
            )
          ))
        )
      )
    }
  }

}
