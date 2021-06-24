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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpMethods, HttpProtocols, HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import akka.util.Timeout
import de.awagen.kolibri.base.actors.flows.GenericRequestFlows.{Host, getHttpConnectionPoolFlow}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig.{config, logger}
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.{Batch, JobMsgFactory}
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.base.io.writer.aggregation.{BaseMetricAggregationWriter, BaseMetricDocumentWriter}
import de.awagen.kolibri.base.io.writer.base.LocalDirectoryFileFileWriter
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{CombinedModifier, RequestParameterModifier, RequestTemplateBuilderModifier}
import de.awagen.kolibri.base.processing.modifiers.{Modifier, RequestTemplateBuilderModifiers}
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.RequestProcessingFlows
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.responsehandlers.SolrHttpResponseHandlers
import de.awagen.kolibri.base.usecase.searchopt.metrics.Metrics._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{JudgementHandlingStrategy, MetricsCalculation, MetricsEvaluation}
import de.awagen.kolibri.base.usecase.searchopt.provider.{ClassPathFileBasedJudgementProviderFactory, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.collections.generators.{BatchByGeneratorIndexedGenerator, ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.tagging.{TagType, Tags}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator}
import de.awagen.kolibri.datatypes.values.{DistinctValues, MetricValue, RangeValues}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SearchJobDefinitions {

  // define the changes to the request
  val urlModifierGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = Seq(
    ByFunctionNrLimitedIndexedGenerator(20,
      genFunc = x => Some(RequestParameterModifier(params = immutable.Map[String, Seq[String]]("q" -> Seq(s"q$x")),
        replace = false)))
  )
  val requestParameterGrid: OrderedMultiValues = GridOrderedMultiValues.apply(Seq(
    DistinctValues[String]("a", Range(0, 10, 1).map(x => s"q$x")),
    RangeValues[Float](name = "rangeParam1", start = 0.0F, end = 100.0F, stepSize = 1.0F)
  ))
  val paramGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = requestParameterGrid.values.map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(x.getAll.map(y => RequestTemplateBuilderModifiers.RequestParameterModifier(params = immutable.Map(x.name -> Seq(y.toString)), replace = true))))
  val allModifierGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = urlModifierGenerators ++ paramGenerators

  // create batches by query single query values (the generator at index 0)
  // every batch is an IndexedGenerator over Modifiers on RequestTemplateBuilder
  def batchGenerator(batchByIndex: Int): Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] => IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]] =
    modifierGenerators => {
      val generator: IndexedGenerator[IndexedGenerator[CombinedModifier[RequestTemplateBuilder]]] = BatchByGeneratorIndexedGenerator(modifierGenerators, batchByIndex)
        .mapGen(x => x.mapGen(y => CombinedModifier(y)))
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(Range(0, generator.size).map(index => Batch(index, generator.get(index).get)))
    }

  // the supplier of aggregators needed to aggregate ProcessingMessage[MetricRow]
  def metricRowAggregatorSupplier: () => Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]] = () => {
    new TagKeyMetricAggregationPerClassAggregator()
  }

  // provider of judgements of type Double
  val judgementProviderFactory: JudgementProviderFactory[Double] = ClassPathFileBasedJudgementProviderFactory("data/test_judgements.txt")

  // defining which metrics to calculate and how to handle missing judgements
  def metricsCalculation: MetricsCalculation = MetricsCalculation(
    metrics = Seq(NDCG_10, PRECISION_4, ERR),
    judgementHandling = JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS)

  // metricsEvaluation groups judgement provision and metric calculation
  val metricsEvaluation: MetricsEvaluation = MetricsEvaluation(judgementProviderFactory, metricsCalculation)

  // TODO: in creation of the requesting flow make sure
  // useHttps is taken into account (e.g only use https mode
  // is useHttps = true)
  val connections: Seq[Connection] = Seq(
    //    Connection(host = "127.0.0.1", port = 80, useHttps = false, credentialsProvider = None)
    Connection(host = "172.33.1.4", port = 80, useHttps = false, credentialsProvider = None)
    //    Connection(host = "fastapi", port = 80, useHttps = false, credentialsProvider = None)
  )

  // some fixed parameters
  val fixedParams: Map[String, Seq[String]] = Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("v3"))
  // val fixedParams: Map[String, Seq[String]] = Map.empty
  // the base RequestTeplateBuilder to be modified by the specific modifications in each batch
  val requestTemplateBuilderSupplier: () => RequestTemplateBuilder = () => {
    new RequestTemplateBuilder()
      .withContextPath("search")
      .withProtocol(HttpProtocols.`HTTP/1.1`)
      .withHttpMethod(HttpMethods.GET)
      .withParams(fixedParams)
  }

  // transforming single modifier to ProcessingMessage[RequestTemplate], e.g the actual unit to process
  val modifierToProcessingMessage: Modifier[RequestTemplateBuilder] => ProcessingMessage[RequestTemplate] = v1 => {
    val requestTemplateBuilder: RequestTemplateBuilder = requestTemplateBuilderSupplier.apply()
    val requestTemplate: RequestTemplate = v1.apply(requestTemplateBuilder).build()
    Corn(requestTemplate)
      // tag the processing element
      .withTags(TagType.AGGREGATION,
        Set(Tags.ParameterSingleValueTag(
          Map("q" -> requestTemplate.getParameter("q").map(y => y.mkString("-")).getOrElse(""))
        ))
      )
  }

  // actual flow of transforming modifiers into ProcessingMessage[RequestTemplate] (with query tag)
  val processingFlow: Flow[Modifier[RequestTemplateBuilder], ProcessingMessage[RequestTemplate], NotUsed] = Flow.fromFunction[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate]](modifierToProcessingMessage)

  val processingMsgToRequestTuple: Flow[ProcessingMessage[RequestTemplate], (HttpRequest, ProcessingMessage[RequestTemplate]), NotUsed] =
    Flow.fromFunction(x => (x.data.getRequest, x))

  // mapping of connection to actual flow of tuple (HttpRequest, ProcessingMessage[RequestTemplate]) to
  // (Try[HttpResponse], ProcessingMessage[RequestTemplate])
  def connectionFunc(implicit actorSystem: ActorSystem): Connection => Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] = v1 => {
    //    getHttpsConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
    getHttpConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
  }

  // mapping all set connections to their flow of request execution
  def connectionFlows(implicit actorSystem: ActorSystem): Seq[Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _]] = connections.map(connectionFunc)


  def requestingFlow(throughputActor: Option[ActorRef])(implicit as: ActorSystem, ec: ExecutionContext): Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)]], NotUsed] = RequestProcessingFlows.requestAndParsingFlow(
    throughputActor,
    "q",
    "jobId",
    connections,
    connectionFunc,
    SolrHttpResponseHandlers.httpToProductIdSeqFuture(_ => true)
  )

  def throwableToMetricRowResponse(e: Throwable): MetricRow = {
    val metricRow = MetricRow(params = Map.empty[String, Seq[String]], metrics = Map.empty[String, MetricValue[Double]])
    val failMetricName: String = TaskFailType.FailedByException(e).toString
    val addValue: MetricValue[Double] = MetricValue.createAvgFailSample(metricName = failMetricName, failMap = Map[ComputeFailReason, Int](ComputeFailReason(s"exception:${e.getClass.toString}") -> 1))
    metricRow.addMetric(addValue)
  }

  // full request flow of tagging the resulting request template and then async requesting and response parsing
  def fullProcessingFlow(throughputActor: Option[ActorRef],
                         judgementProviderFactory: JudgementProviderFactory[Double],
                         metricsCalculation: MetricsCalculation)
                        (implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): Flow[RequestTemplateBuilderModifier, ProcessingMessage[MetricRow], NotUsed] = {
    val partialFlow: Flow[RequestTemplateBuilderModifier, ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], NotUsed] = processingFlow.via(Flow.fromGraph(requestingFlow(throughputActor)))

    partialFlow.mapAsyncUnordered[ProcessingMessage[MetricRow]](config.requestParallelism)(x => {
      x.data._1 match {
        case e@Left(_) =>
          val metricRow = throwableToMetricRowResponse(e.value)
          val result: ProcessingMessage[MetricRow] = Corn(metricRow)
          val originalTags: Set[Tag] = x.getTagsForType(TagType.AGGREGATION)
          result.addTags(TagType.AGGREGATION, originalTags)
          // TODO: temporal test tagging, remove and put at appropriate position
          Future.successful(result.withTags(AGGREGATION, Set(StringTag("ALL"), StringTag(s"q=${x.data._2.parameters("q").head}"))))
        case e@Right(_) =>
          judgementProviderFactory.getJudgements.future
            .map(y => {
              val judgements: Seq[Option[Double]] = y.retrieveJudgements(x.data._2.parameters("q").head, e.value)
              logger.debug(s"retrieved judgements: $judgements")
              // TODO: need to set the parameters of the request here, otherwise its just one overall aggregated value per tag,
              // irrespective of distinct tried parameter settings
              val metricRow: MetricRow = metricsCalculation.calculateAll(immutable.Map(x.data._2.parameters.toSeq.filter(x => x._1 != "q"): _*), judgements)
              logger.debug(s"calculated metrics: $metricRow")
              // TODO: we should place default tag, otherwise final aggregation will
              // not contain anything :)
              Corn(metricRow).withTags(AGGREGATION, Set(StringTag("ALL"), StringTag(s"q=${x.data._2.parameters("q").head}")))
            })
            .recover(throwable => {
              logger.warn(s"failed retrieving judgements: $throwable")
              val metricRow = throwableToMetricRowResponse(throwable)
              val result: ProcessingMessage[MetricRow] = Corn(metricRow)
              val originalTags: Set[Tag] = x.getTagsForType(TagType.AGGREGATION)
              result.addTags(TagType.AGGREGATION, originalTags)
              result.withTags(AGGREGATION, Set(StringTag("ALL"), StringTag(s"q=${x.data._2.parameters("q").head}")))
            })
      }
    })
  }


  val expectationGen: Int => ExecutionExpectation = v1 => {
    BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(ClassifyingCountExpectation(classifier = Map("finishResponse" -> {
        case Corn(e) if e.isInstanceOf[MetricRow] => true
        case _ => false
      }), expectedClassCounts = Map("finishResponse" -> v1))),
      fulfillAnyForFail = Seq(StopExpectation(v1, {
        _ => false
      }, x => x._2 > 0),
        TimeExpectation(600 minutes))
    )
  }

  val fileWriter: FileWriter[String, Any] = LocalDirectoryFileFileWriter(directory = "/app/data")

  val documentWriter: Writer[MetricDocument[Tag], Tag, Any] = BaseMetricDocumentWriter(
    writer = fileWriter,
    format = CSVParameterBasedMetricDocumentFormat(columnSeparator = "\t"),
    subFolder = "testJobId",
    keyToFilenameFunc = x => x.toString)
  val writer: Writer[MetricAggregation[Tag], Tag, Any] = BaseMetricAggregationWriter(writer = documentWriter)

  // define the message to supervisor which will generate ProcessJobCmd
  // TODO: we can only initialize this within some actor with a given actorcontext, so if we define this within routes
  // the respective actor wont be there yet to provide context, thus wrap this here to be able to create route without
  def processActorRunnableJobCmd(implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] =
    JobMsgFactory.createActorRunnableJobCmd[Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]], RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]](
      jobId = "testJobId",
      data = allModifierGenerators,
      dataBatchGenerator = batchGenerator(batchByIndex = 0),
      transformerFlow = fullProcessingFlow(
        throughputActor = Option.empty[ActorRef],
        judgementProviderFactory = judgementProviderFactory,
        metricsCalculation = metricsCalculation),
      processingActorProps = None, // need to change this
      perBatchExpectationGenerator = expectationGen,
      perBatchAndOverallAggregatorSupplier = metricRowAggregatorSupplier,
      writer = writer,
      returnType = ActorRunnableSinkType.REPORT_TO_ACTOR_SINK,
      allowedTimePerElementInMillis = 10000,
      allowedTimePerBatchInSeconds = 60000,
      allowedTimeForJobInSeconds = 36000
    )

}

