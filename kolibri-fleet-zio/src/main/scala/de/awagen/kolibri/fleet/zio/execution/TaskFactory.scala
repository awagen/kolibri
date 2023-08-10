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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormatHelper.getMetricValueFromTypeAndSample
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, ResultRecord, TwoInCalculation}
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.datatypes.values.RunningValues.RunningValue
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.HttpMethod
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricRowFunctions.throwableToMetricRowResponse
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.http.client.request.RequestTemplateImplicits.RequestTemplateToZIOHttpRequest
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker.INITIAL_DATA_KEY
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import zio.http.Client
import zio.stream.ZStream
import zio.{Chunk, Task, ZIO, durationInt}

import java.util.Objects

object TaskFactory {

  private def getParamsFromRequestTemplate(requestTemplate: RequestTemplate, excludeParams: Seq[String]): Map[String, Seq[String]] = {
    Map(requestTemplate.parameters.toSeq.filter(x => !excludeParams.contains(x._1)): _ *)
  }

  object RequestJsonAndParseValuesTask {

    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    val requestTemplateBuilderModifierKey = NamedClassTyped[RequestTemplateBuilderModifier](INITIAL_DATA_KEY)
    val requestTemplateKey = NamedClassTyped[RequestTemplate](REQUEST_TEMPLATE_STORAGE_KEY.name)

    val HTTPS_URL_PREFIX = "https"
    val HTTP_URL_PREFIX = "http"
    val HTTP_1_1_PROTOCOL_ID = "HTTP/1.1"

  }

  /**
   * Task for executing a request defined by modifier on RequestTemplateBuilder,
   * contextPath and some fixed parameters. Also places the RequestTemplate in the resulting map.
   * Fields are extracted from the response according to the
   * ParsingConfig.
   */
  case class RequestJsonAndParseValuesTask(parsingConfig: ParsingConfig,
                                           taggingConfig: RequestAndParsingResultTaggerConfig,
                                           connectionSupplier: () => Connection,
                                           contextPath: String,
                                           fixedParams: Map[String, Seq[String]],
                                           httpMethod: String = HttpMethod.GET.toString,
                                           // default value of http client utilizes dynamic connection pool
                                           httpClient: Client,
                                           successKeyName: String = "parsedValueMap",
                                           failKeyName: String = "parseFail") extends ZIOTask[WeaklyTypedMap[String]] {

    import RequestJsonAndParseValuesTask._

    override def prerequisiteKeys: Seq[String] = Seq(INITIAL_DATA_KEY)

    override def successKey: String = successKeyName

    override def failKey: String = failKeyName

    private def initRequestTemplateBuilder: RequestTemplateBuilder = new RequestTemplateBuilder()
      .withContextPath(contextPath)
      .withHttpMethod(httpMethod)
      .withProtocol(HTTP_1_1_PROTOCOL_ID)
      .withParams(fixedParams)

    private def composeAndTagRequestTemplateProcessingMessage(map: WeaklyTypedMap[String]): Task[ProcessingMessage[RequestTemplate]] = {
      for {
        // compose the request template
        modifierOpt <- ZIO.attempt(map.get[RequestTemplateBuilderModifier](INITIAL_DATA_KEY))
        _ <- ZIO.when(modifierOpt.isEmpty)(
          ZIO.logWarning(s"Key '$INITIAL_DATA_KEY' is not available in passed map '$map', computation will fail.") *>
            ZIO.logWarning(s"Map only has key-value pairs: '${map.keys.map(x => (x, map.get[Any](x).map(y => y.getClass)))}'")
        )
        modifier <- ZIO.attempt(modifierOpt.get)
        _ <- ZIO.logDebug(s"request modifier: $modifier")
        requestTemplate <- ZIO.attempt(modifier.apply(initRequestTemplateBuilder).build())
        _ <- ZIO.logDebug(s"request template: $requestTemplate")
        // wrap in processing message and tag
        requestTemplateProcessingMessage <- ZIO.attempt(ProcessingMessages.Corn(requestTemplate))
        _ <- ZIO.attempt(taggingConfig.requestTagger.apply(requestTemplateProcessingMessage))
      } yield requestTemplateProcessingMessage
    }

    private def requestEffect(requestTemplatePM: ProcessingMessage[RequestTemplate]): ZIO[Any, Any, ProcessingMessage[WeaklyTypedMap[String]]] = {
      for {
        // prepare the request and parsing effect
        connection <- ZIO.attempt(connectionSupplier.apply())
        basicAuthOpt <- ZIO.attempt(connection.credentialsProvider.map(x => x.getCredentials))
        protocolPrefix <- ZIO.succeed(if (connection.useHttps) HTTPS_URL_PREFIX else HTTP_URL_PREFIX)
        requestAndParseResult <- (for {
          host <- ZIO.succeed(s"$protocolPrefix://${connection.host}:${connection.port}")
          _ <- ZIO.logDebug(s"host: $host")
          res <- requestTemplatePM.data.toZIOHttpRequest(
            host,
            basicAuthOpt,
            httpClient
          )
            .timeout(3 seconds)
            .retryN(AppProperties.config.maxRetriesPerBatchTask)
          data <- ZIO.attempt(res)
            .flatMap(x => x.get.body.asString.map(x => Json.parse(x)))
          _ <- ZIO.logDebug(s"retrieved request response: $data")
          parsed <- ZIO.attempt(parsingConfig.jsValueToTypeTaggedMap.apply(data))
        } yield parsed)
        requestAndParseResultPM <- ZIO.succeed(requestTemplatePM.map(_ => requestAndParseResult))
      } yield requestAndParseResultPM

    }


    override def task(map: WeaklyTypedMap[String]): Task[WeaklyTypedMap[String]] =
      ZIO.logDebug(s"request and parsing task input data: $map, \n value for key '$INITIAL_DATA_KEY': ${map.get[RequestTemplateBuilderModifier](INITIAL_DATA_KEY)}: ") *> {
        val computeEffect: ZIO[Any, Throwable, Option[WeaklyTypedMap[String]]] = for {
          // construct request template and execute request and parse relevant fields
          computeResult <- ZIO.whenCaseZIO(composeAndTagRequestTemplateProcessingMessage(map).either)({
            // in this case we cannot take over any tags, since exception happened earlier
            case Left(throwable) =>
              val errorResult = ProcessingMessages.BadCorn(TaskFailType.FailedByException(throwable))
              ZIO.attempt({
                map.put(failKey, errorResult)
                map
              })
            // in this case we got the processing message with the request template. If processing of request fails
            // we can attach the tags of the request template to the fail result
            case Right(requestTemplatePM) =>
              (for {
                requestResult <- requestEffect(requestTemplatePM)
                // apply tagging
                _ <- ZIO.attempt({
                  // using tagger on a slighly different type, thus needing to create a dummy message which is tagged
                  // and then taking over those tags. Not optimal, adjust that
                  val dummyProcessingMsg = requestResult.map[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)](x => (Right(x), requestTemplatePM.data))
                  taggingConfig.parsingResultTagger.apply(dummyProcessingMsg)
                  requestResult.takeOverTags(dummyProcessingMsg)
                })
                result <- ZIO.attempt({
                  requestResult.data.put(REQUEST_TEMPLATE_STORAGE_KEY.name, requestTemplatePM.data)
                  map.put(successKey, requestResult)
                  map
                })
              } yield result)
                .catchAllCause(cause => {
                  val msg = cause.dieOption.map(t => t.getMessage).getOrElse("")
                  val errorResult = ProcessingMessages.BadCorn(TaskFailType.FailedByException(new RuntimeException(s"request and parsing failed. Msg = '$msg'")))
                  errorResult.takeOverTags(requestTemplatePM)
                  ZIO.attempt({
                    map.put(failKey, errorResult)
                    map
                  })
                })
          })
        } yield computeResult
        computeEffect.map(x => x.getOrElse({
          map.put(failKey, ProcessingMessages.BadCorn(TaskFailType.FailedByException(new RuntimeException("Failed computing request parsing result"))))
          map
        }))
      }
  }

  /**
   * Metrics calculation task.
   * Note that a failure during metric calculation is mapped to a MetricRow with fail entry
   * rather than letting task fail by setting value for the fail-key. The latter only happens
   * in case the mapping to a MetricRow indicating the failure was not successful.
   *
   * @param requestAndParseSuccessKey          - The key in the type tagged map where the parsed results are stored
   * @param requestTemplateKey                 - key within the parsed results where the requestTemplate is stored
   * @param calculations                       - calculations to be executed based on the parsed data
   * @param metricNameToAggregationTypeMapping - for each metric name mapping to its data type to ensure correct handling of aggregations and such
   * @param excludeParamsFromMetricRow         - parameters to be excluded from the final result data structure.
   * @param tagger                             - tagger for the result. To provide tag to be taken into account for per-tag aggregations, add tag with type AGGREGATION
   *                                           (NOTE: the tags from previous steps are taken over, so if e.g tagging by query-param happened before, no need to do it again here)
   * @param successKeyName                     - the key of the processing message containing successfully calculated result
   * @param failKeyName                        - the name of the processing message containing the fail reason
   */
  case class CalculateMetricsTask(requestAndParseSuccessKey: String,
                                  requestTemplateKey: String,
                                  calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                  metricNameToAggregationTypeMapping: Map[String, AggregationType],
                                  excludeParamsFromMetricRow: Seq[String],
                                  tagger: ProcessingMessage[MetricRow] => ProcessingMessage[MetricRow] = identity,
                                  successKeyName: String = "metricsRow",
                                  failKeyName: String = "metricsCalculationFail") extends ZIOTask[MetricRow] {
    override def prerequisiteKeys: Seq[String] = Seq(requestAndParseSuccessKey)

    override def successKey: String = successKeyName

    override def failKey = failKeyName

    private def calculateMetrics(parsedFields: ProcessingMessage[WeaklyTypedMap[String]]): Task[Chunk[MetricValue[Any]]] = {
      ZStream.fromIterable(calculations)
        .mapZIO(x => ZIO.attempt {
          // ResultRecord is only combination of name and Either[Seq[ComputeFailReason], T]
          val values: Seq[ResultRecord[Any]] = x.calculation.apply(parsedFields.data)
          resultRecordsToMetricValues(values, metricNameToAggregationTypeMapping)
        })
        .runCollect
        .map(x => x.flatten)
    }

    private def metricValuesToMetricRow(metricValues: Seq[MetricValue[_]],
                                        metricRowParams: Map[String, Seq[String]]): Task[MetricRow] = ZIO.attempt {
      MetricRow.emptyForParams(params = metricRowParams)
        .addFullMetricsSampleAndIncreaseSampleCount(metricValues: _*)
    }

    /**
     * Note that some calculations pick data out of the WeaklyTypedMap, thus make sure all those keys needed
     * are filled with data (see respective metrics)
     */
    override def task(map: WeaklyTypedMap[String]): Task[WeaklyTypedMap[String]] = {
      val parsedFieldsOpt: Option[ProcessingMessage[WeaklyTypedMap[String]]] = map.get[ProcessingMessage[WeaklyTypedMap[String]]](requestAndParseSuccessKey)
      val requestTemplateOpt: Option[RequestTemplate] = parsedFieldsOpt.flatMap(x => x.data.get[RequestTemplate](requestTemplateKey))
      val metricRowParamsOpt: Option[Map[String, Seq[String]]] = requestTemplateOpt.map(x => getParamsFromRequestTemplate(x, excludeParamsFromMetricRow))
      val computeResult = for {
        _ <- ZIO.logDebug(s"Metrics calculation input: $map")
        parsedFields <- ZIO.attempt(parsedFieldsOpt.get)
        currentTags <- ZIO.attempt(parsedFields.getTagsForType(TagType.AGGREGATION))
        metricRowParams <- ZIO.attempt(metricRowParamsOpt.get)
        singleResults <- calculateMetrics(parsedFields)
        _ <- ZIO.logDebug(s"single results: $singleResults")
        metricRow <- metricValuesToMetricRow(singleResults, metricRowParams)
        _ <- ZIO.logDebug(s"computed metric values for aggregation tags '$currentTags': '$metricRow'")
        processingMessageResult <- ZIO.attempt(tagger.apply(ProcessingMessages.Corn(metricRow).withTags(TagType.AGGREGATION, currentTags)))
      } yield processingMessageResult
      computeResult
        .tap(result => ZIO.logDebug(s"calculated metrics: ${result.data}"))
        .retryN(AppProperties.config.maxRetriesPerBatchTask)
        .map(value => {
          map.put(successKey, value)
          map
        })
        // try to map the throwable to a MetricRow result containing info about the failure
        .catchAll(throwable =>
          ZIO.logError(s"metric calculation failed, exception:\n${throwable.getStackTrace.mkString("\n")}") *>
            wrapThrowableInMetricRowResult(map, throwable, calculations, metricRowParamsOpt.get, requestAndParseSuccessKey)
              // if the mapping to a MetricRow object with the failure information fails,
              // we declare the task as failed
              .catchAll(throwable =>
                ZIO.logWarning(s"metric calculation failed, exception:\n$throwable") *>
                  ZIO.succeed({
                    map.put(failKey, ProcessingMessages.BadCorn(TaskFailType.FailedByException(throwable)))
                    map
                  })
              )
        )
    }

    private def wrapThrowableInMetricRowResult(map: WeaklyTypedMap[String],
                                               throwable: Throwable,
                                               calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                               metricRowParams: Map[String, Seq[String]],
                                               keyForTagTakeOver: String): Task[WeaklyTypedMap[String]] = {
      ZIO.attempt {
        val parsedFieldsOpt: Option[ProcessingMessage[Any]] = map.get[ProcessingMessage[Any]](keyForTagTakeOver)
        // need to add paramNames here to set the fail reasons for each
        val allParamNames: Set[String] = calculations.flatMap(x => x.names).toSet
        // check if we know rhe right running value mapping, otherwise need to ignore the metric
        val validParamNameEmptyRunningValueMap: Map[String, RunningValue[_]] = allParamNames.map(name => {
          (name, metricNameToAggregationTypeMapping.get(name)
            .map(aggType => aggType.emptyRunningValueSupplier.apply())
            .orNull)
        })
          .filter(x => Objects.nonNull(x._2)).toMap
        val metricRow = throwableToMetricRowResponse(throwable, validParamNameEmptyRunningValueMap, metricRowParams)
        val result: ProcessingMessage[MetricRow] = tagger.apply(
          ProcessingMessages.Corn(metricRow)
            .withTags(
              TagType.AGGREGATION,
              parsedFieldsOpt.map(x => x.getTagsForType(TagType.AGGREGATION)).getOrElse(Set.empty)
            ))
        map.put(successKey, result)
        map
      }
    }

  }

  /**
   * Convert sequence of ResultRecords to sequence of MetricValues
   */
  def resultRecordsToMetricValues[T](seq: Seq[ResultRecord[T]], metricNameToAggregationTypeMapping: Map[String, AggregationType]): Seq[MetricValue[T]] = {
    seq.map(value => {
      val metricAggregationType: Option[AggregationType] = metricNameToAggregationTypeMapping.get(value.name)
      // we use all result records for which we have a AggregationType mapping
      metricAggregationType.map(aggregationType => {
        getMetricValueFromTypeAndSample[T](aggregationType.asInstanceOf[AggregationType.Val[T]], value)
      }).orNull
    })
      .filter(x => Objects.nonNull(x))
  }


  /**
   * Task allowing calculations that include two WeaklyTypedMap[String] instances, e.g as in the case of comparing
   * parsed results for two distinct endpoints or the like.
   *
   * @param key1                               - key holding the first input result
   * @param key2                               - key holding the second input result
   * @param calculations                       - calculations using two inputs of type WeaklyTypedMap[String] to compute metrics
   * @param metricNameToAggregationTypeMapping : mapping of metric name to aggregation type
   * @param excludeParamsFromMetricRow         : name of parameters to exclude when composing the MetricRow result
   * @param successKeyName                     : key name under which to store the ProcessingMessage[MetricRow] result in case of
   *                                           successful computation
   * @param failKeyName                        : key name under which to store the fail reasons in case computation was not successful
   */
  case class TwoMapInputCalculation(key1: String,
                                    key2: String,
                                    calculations: Seq[TwoInCalculation[WeaklyTypedMap[String], WeaklyTypedMap[String], Any]],
                                    metricNameToAggregationTypeMapping: Map[String, AggregationType],
                                    excludeParamsFromMetricRow: Seq[String],
                                    successKeyName: String = "twoMapInputMetricsRow",
                                    failKeyName: String = "twoMapInputMetricsCalculationFail") extends ZIOTask[MetricRow] {
    override def prerequisiteKeys: Seq[String] = Seq(key1, key2)

    override def successKey: String = successKeyName

    override def failKey: String = failKeyName

    override def task(map: WeaklyTypedMap[String]): Task[WeaklyTypedMap[String]] = {
      val calculateAndUpdateMapEffect = for {
        map1 <- ZIO.attempt(map.get[ProcessingMessage[WeaklyTypedMap[String]]](key1).get)
        map2 <- ZIO.attempt(map.get[ProcessingMessage[WeaklyTypedMap[String]]](key2).get)
        calcResults <- ZStream.fromIterable(calculations)
          .flatMap(calc => ZStream.fromIterable(calc.calculation.apply(map1.data, map2.data)))
          .runCollect
        requestParams <- ZIO.succeed(map1.data.get[RequestTemplate](REQUEST_TEMPLATE_STORAGE_KEY.name).map(x => {
          getParamsFromRequestTemplate(x, excludeParamsFromMetricRow)
        }).getOrElse(Map.empty))
        metricRow <- ZIO.succeed(MetricRow.emptyForParams(requestParams))
        metricValues <- ZIO.attempt(resultRecordsToMetricValues(calcResults, metricNameToAggregationTypeMapping))
        updatedMetricRow <- ZIO.attempt(metricRow.addFullMetricsSampleAndIncreaseSampleCount(metricValues: _*))
        resultProcessingMessage <- ZIO.succeed(ProcessingMessages.Corn(updatedMetricRow))
        // take over tags from both inputs
        _ <- ZIO.attempt({
          resultProcessingMessage.takeOverTags(map1)
          resultProcessingMessage.takeOverTags(map2)
        })
        updatedMap <- ZIO.attempt({
          map.put(successKey, resultProcessingMessage)
          map
        })
      } yield updatedMap
      calculateAndUpdateMapEffect.catchAll(throwable => {
        ZIO.logError(s"metric calculation failed, exception:\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed({
            map.put(failKey, ProcessingMessages.BadCorn(TaskFailType.FailedByException(throwable)))
            map
          })
      })
    }
  }

  /**
   * Simple task that merges the results of two instances of tye ProcessingMessage[MetricRow] into another one.
   * The merged result is stored as ProcessingMessage[MetricRow] for key of name successKeyName,
   * with preserved tags from both merged instances
   *
   * @param key1           - key of the first ProcessingMessage[MetricRow], containing the first MetricRow to merge
   * @param key2           - key of the first ProcessingMessage[MetricRow], containing the second MetricRow to merge
   * @param successKeyName - name of the key to store the merged ProcessingMessage[MetricRow] under
   * @param failKeyName    - name of key to store fail reasons under in case of failed task
   */
  case class MergeTwoMetricRows(key1: String,
                                key2: String,
                                successKeyName: String = "mergeTwoRowsResult",
                                failKeyName: String = "failedToMergeRows") extends ZIOTask[MetricRow] {
    override def prerequisiteKeys: Seq[String] = Seq(key1, key2)

    override def successKey: String = successKeyName

    override def failKey: String = failKeyName

    // TODO: define prefixes per metric origin for merging the metrics
    override def task(map: WeaklyTypedMap[String]): Task[WeaklyTypedMap[String]] = {
      val mergeEffect = for {
        row1 <- ZIO.attempt(map.get[ProcessingMessage[MetricRow]](key1).get)
        row2 <- ZIO.attempt(map.get[ProcessingMessage[MetricRow]](key2).get)
        mergedRow <- ZStream.fromIterable(row2.data.metricValues)
          .runFold(row1.data)((oldRow, newMetric) => {
            oldRow.addMetricDontChangeCountStore(newMetric)
          })
        processingMessageResult <- ZIO.succeed(ProcessingMessages.Corn(mergedRow))
        _ <- ZIO.attempt({
          processingMessageResult.takeOverTags(row1)
          processingMessageResult.takeOverTags(row2)
        })
        updatedMap <- ZIO.attempt({
          map.put(successKey, processingMessageResult)
          map
        })
      } yield updatedMap
      mergeEffect.catchAll(throwable => {
        ZIO.logError(s"metricRow merging failed, exception:\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed({
            map.put(failKey, ProcessingMessages.BadCorn(TaskFailType.FailedByException(throwable)))
            map
          })
      })
    }
  }


}
