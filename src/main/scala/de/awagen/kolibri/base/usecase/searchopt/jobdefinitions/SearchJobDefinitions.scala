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
import de.awagen.kolibri.base.actors.flows.GenericRequestFlows.{Host, getHttpsConnectionPoolFlow}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.{Batch, JobMsgFactory}
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.base.io.writer.aggregation.{BaseMetricAggregationWriter, BaseMetricDocumentWriter}
import de.awagen.kolibri.base.io.writer.base.LocalDirectoryFileFileWriter
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{CombinedModifier, RequestTemplateBuilderModifier}
import de.awagen.kolibri.base.processing.modifiers.{Modifier, RequestTemplateBuilderModifiers}
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.RequestProcessingFlows
import de.awagen.kolibri.base.usecase.searchopt.http.client.flows.responsehandlers.SolrHttpResponseHandlers
import de.awagen.kolibri.base.usecase.searchopt.metrics.Metrics._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{JudgementHandlingStrategy, MetricsCalculation, MetricsEvaluation}
import de.awagen.kolibri.base.usecase.searchopt.provider.{ClassPathFileBasedJudgementProviderFactory, JudgementProvider, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.collections.generators.{BatchByGeneratorIndexedGenerator, ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.CSVParameterBasedMetricDocumentFormat
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.stores.{MetricDocument, MetricRow}
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.tagging.{TagType, Tags}
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SearchJobDefinitions {

  def metricRowAggregatorSupplier: SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]] = {
    new SerializableSupplier[Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]]] {
      override def get(): Aggregator[ProcessingMessage[MetricRow], MetricAggregation[Tag]] = new TagKeyMetricAggregationPerClassAggregator()
    }
  }

  def classPathjudgementProviderFactory(filename: String): JudgementProviderFactory[Double] = ClassPathFileBasedJudgementProviderFactory(filename)

  val judgementProviderFactory: JudgementProviderFactory[Double] = classPathjudgementProviderFactory("data/test_judgements.txt")

  def metricsCalculation: MetricsCalculation = MetricsCalculation(
    metrics = Seq(NDCG_10, PRECISION_4, ERR),
    judgementHandling = JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS)

  def metricsEvaluation(judgementProviderFactory: JudgementProviderFactory[Double], metricsCalculation: MetricsCalculation) =
    MetricsEvaluation(judgementProviderFactory, metricsCalculation: MetricsCalculation)

  val metricsEvaluations: MetricsEvaluation = metricsEvaluation(judgementProviderFactory, metricsCalculation)

  val connections: Seq[Connection] = Seq(
    Connection(host = "localhost", port = 80, useHttps = false, credentialsProvider = None)
  )

  // in dataGrid need distinction between normal values, headers, body and so on to correctly modify the request context
  val intToQuery: SerializableFunction1[Int, String] = new SerializableFunction1[Int, String] {
    override def apply(v1: Int): String = s"q$v1"
  }
  val requestParameterGrid: OrderedMultiValues = GridOrderedMultiValues.apply(Seq(
    DistinctValues[String]("q", Range(0, 100, 1).map(intToQuery)),
    RangeValues[Float](name = "rangeParam1", start = 0.0F, end = 10.0F, stepSize = 1.0F)
  ))
  // OrderedMultiValues to generators of parameter modifiers
  def anyToModifier(paramName: String): SerializableFunction1[Any, Modifier[RequestTemplateBuilder]] = new SerializableFunction1[Any, Modifier[RequestTemplateBuilder]]{
    override def apply(v1: Any): Modifier[RequestTemplateBuilder] = {
      RequestTemplateBuilderModifiers.RequestParameterModifier(params = immutable.Map(paramName -> Seq(v1.toString)), replace = false)
    }
  }
  val orderedValuesToGenerator: SerializableFunction1[OrderedValues[Any], IndexedGenerator[RequestTemplateBuilderModifier]] = new SerializableFunction1[OrderedValues[Any], IndexedGenerator[RequestTemplateBuilderModifier]]{
    override def apply(v1: OrderedValues[Any]): IndexedGenerator[RequestTemplateBuilderModifier] = {
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(v1.getAll.map(anyToModifier(v1.name)))
    }
  }
  val paramGenerators: Seq[IndexedGenerator[RequestTemplateBuilderModifier]] = requestParameterGrid.values.map(orderedValuesToGenerator)

  val batchSize: Int = 1000
  val modifierSeqToCombined: SerializableFunction1[Seq[Modifier[RequestTemplateBuilder]], Modifier[RequestTemplateBuilder]] = new SerializableFunction1[Seq[Modifier[RequestTemplateBuilder]], Modifier[RequestTemplateBuilder]] {
    override def apply(v1: Seq[Modifier[RequestTemplateBuilder]]): Modifier[RequestTemplateBuilder] = CombinedModifier(v1)
  }
  val modSeqToBatch: SerializableFunction1[IndexedGenerator[Seq[RequestTemplateBuilderModifier]], IndexedGenerator[RequestTemplateBuilderModifier]] = new SerializableFunction1[IndexedGenerator[Seq[RequestTemplateBuilderModifier]], IndexedGenerator[RequestTemplateBuilderModifier]]{
    override def apply(v1: IndexedGenerator[Seq[RequestTemplateBuilderModifier]]): IndexedGenerator[RequestTemplateBuilderModifier] = {
      v1.mapGen(modifierSeqToCombined)
    }
  }
  val batchByGeneratorByIndex: IndexedGenerator[IndexedGenerator[RequestTemplateBuilderModifier]] = BatchByGeneratorIndexedGenerator(paramGenerators, 0)
    .mapGen(modSeqToBatch)

  // some fixed parameters
  val fixedParams: Map[String, Seq[String]] = Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("v3"))
  val requestTemplateBuilderSupplier: SerializableSupplier[RequestTemplateBuilder] = new SerializableSupplier[RequestTemplateBuilder] {
    override def get(): RequestTemplateBuilder = new RequestTemplateBuilder()
      .withContextPath("search")
      .withProtocol(HttpProtocols.`HTTP/1.1`)
      .withHttpMethod(HttpMethods.GET)
      .withParams(fixedParams)
  }
  // the processing flow of each combination
  val modifierToProcessingMessage: SerializableFunction1[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate]] = new SerializableFunction1[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate]] {
    override def apply(v1: RequestTemplateBuilderModifier): ProcessingMessage[RequestTemplate] = {
      val requestTemplateBuilder: RequestTemplateBuilder = requestTemplateBuilderSupplier.get()
      val requestTemplate: RequestTemplate = v1.apply(requestTemplateBuilder).build()
      val seqToString: SerializableFunction1[Seq[String], String] = new SerializableFunction1[Seq[String], String] {
        override def apply(v1: Seq[String]): String = v1.mkString("-")
      }
      Corn(requestTemplate)
        // tag the processing element
        .withTags(TagType.AGGREGATION,
          Set(Tags.ParameterSingleValueTag(
            Map("q" -> requestTemplate.getParameter("q").map(seqToString).getOrElse(""))
          ))
        )
    }
  }
  val processingFlow: Flow[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate], NotUsed] = Flow.fromFunction[RequestTemplateBuilderModifier, ProcessingMessage[RequestTemplate]](modifierToProcessingMessage)

  def connectionFunc(implicit actorSystem: ActorSystem): SerializableFunction1[Connection, Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _]]  = new SerializableFunction1[Connection, Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _]]{
    override def apply(v1: Connection): Flow[(HttpRequest, ProcessingMessage[RequestTemplate]), (Try[HttpResponse], ProcessingMessage[RequestTemplate]), _] = {
      getHttpsConnectionPoolFlow[ProcessingMessage[RequestTemplate]].apply(Host(v1.host, v1.port))
    }
  }

  def requestingFlow(throughputActor: ActorRef)(implicit as: ActorSystem, ec: ExecutionContext): Graph[FlowShape[ProcessingMessage[RequestTemplate], ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)]], NotUsed] = RequestProcessingFlows.requestAndParsingFlow(
    Some(throughputActor),
    "q",
    "jobId",
    connections,
    connectionFunc,
    SolrHttpResponseHandlers.httpToProductIdSeqFuture
  )

  // full request flow of tagging the resulting request template and then async requesting and response parsing
  def fullProcessingFlow(throughputActor: ActorRef,
                         judgementProviderFactory: JudgementProviderFactory[Double],
                         metricsCalculation: MetricsCalculation)
                        (implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): Flow[RequestTemplateBuilderModifier, ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)], NotUsed] = {
    val partialFlow: Flow[RequestTemplateBuilderModifier, ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], NotUsed] = processingFlow.via(Flow.fromGraph(requestingFlow(throughputActor)))

    // TODO: correctly transfer the below here to serializable function without lambdas
    def judgementProviderToMetricRow(searchTerm: String, productIds: Seq[String], requestTemplate: RequestTemplate): SerializableFunction1[JudgementProvider[Double], ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]] = new SerializableFunction1[JudgementProvider[Double], ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]] {
      override def apply(v1: JudgementProvider[Double]): ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)] = {
        val judgements: Seq[Option[Double]] = v1.retrieveJudgements(searchTerm, productIds)
        val metricRow: MetricRow = metricsCalculation.calculateAll(judgements)
        Corn((Right(metricRow), requestTemplate))
      }
    }

    //    val judgementProviderToMetricRow: SerializableFunction2[JudgementProvider[Double], ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]] = new SerializableFunction2[JudgementProvider[Double], ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]] {
    //      override def apply(v1: JudgementProvider[Double], v2: ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)]): ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)] = {
    //        val judgements: Seq[Option[Double]] = v1.retrieveJudgements(v2.data._2.query, v2.data._1.toOption.get)
    //        val metricRow: MetricRow = metricsCalculation.calculateAll(judgements)
    //        Corn((Right(metricRow), v2.data._2))
    //      }
    //    }
    val processingFunc: SerializableFunction1[ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], Future[ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]]] = new SerializableFunction1[ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)], Future[ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]]] {
      override def apply(v1: ProcessingMessage[(Either[Throwable, Seq[String]], RequestTemplate)]): Future[ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]] = {
        v1.data._1 match {
          case e@Left(_) => Future.successful(Corn((e.asInstanceOf[Either[Throwable, MetricRow]], v1.data._2)))
          case e@Right(_) =>
            judgementProviderFactory.getJudgements.future.map(
              judgementProviderToMetricRow(v1.data._2.query, e.value, v1.data._2)
              //              judgementProvider => {
              //              val judgements: Seq[Option[Double]] = judgementProvider.retrieveJudgements(v1.data._2.query, e.value)
              //              val metricRow: MetricRow = metricsCalculation.calculateAll(judgements)
              //              Corn((Right(metricRow), v1.data._2))
              //            }
            )
        }
      }
    }

    partialFlow.mapAsyncUnordered[ProcessingMessage[(Either[Throwable, MetricRow], RequestTemplate)]](config.requestParallelism)(processingFunc)

    // way via actor, which should not be needed
    //          val future: Future[Any] = ac.actorOf(Props[MetricsCalculationWorker]) ? ProcessProductIdSequenceResponse(
    //            query = x.data._2.query,
    //            response = e,
    //            judgementProviderFactory = judgementProviderFactory,
    //            metricsCalculation = metricsCalculation)
    //          future.map({
    //            case CalculatedMetricsEvent(metricsRow: MetricRow) =>
    //              Corn((Right(metricsRow), x.data._2))
    //            case MetricsRetrievalFailedEvent(_: Seq[String], throwable: Throwable) =>
    //              Corn((Left(throwable), x.data._2))
    //            case _ => Corn((Left(new RuntimeException("unknown result message")), x.data._2))
    //          })

  }


  val expectationGen: SerializableFunction1[Int, ExecutionExpectation] = new SerializableFunction1[Int, ExecutionExpectation] {
    override def apply(v1: Int): ExecutionExpectation = BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(ClassifyingCountExpectation(classifier = Map("finishResponse" -> {
        case Corn(e) if e.isInstanceOf[MetricRow] => true
        case _ => false
      }), expectedClassCounts = Map("finishResponse" -> v1))),
      fulfillAnyForFail = Seq(StopExpectation(v1, {
        _ => false
      }, x => x._2 > 0),
        TimeExpectation(10 seconds))
    )
  }

  //    val batchGenerator: IndexedGenerator[ActorRunnable[RequestTemplateBuilderModifier, RequestTemplate, MetricRow, MetricAggregation[Tag]]] =
  //      batchByGeneratorByIndex.mapGen(x => {
  //        // TODO: need to define processingActorProps
  //        ActorRunnable("testJobId", 0, x, processingFlow, None, expectationGen, metricRowAggregatorSupplier, ActorRunnableSinkType.IGNORE_SINK, 3 seconds, 3 seconds)
  //      })
  // ProcessJobCmd is actually the task within JobManager, for supervisor message with writer
  // and so on we need ProcessActorRunnableJobCmd (see below)
//  def anyToModifier(paramName: String): SerializableFunction1[Any, Modifier[RequestTemplateBuilder]] = new SerializableFunction1[Any, Modifier[RequestTemplateBuilder]]{
//    override def apply(v1: Any): Modifier[RequestTemplateBuilder] = {
//      RequestTemplateBuilderModifiers.RequestParameterModifier(params = immutable.Map(paramName -> Seq(v1.toString)), replace = false)
//    }
//  }
  val orderedValuesToBatched: SerializableFunction1[OrderedValues[Any], IndexedGenerator[RequestTemplateBuilderModifier]] = new SerializableFunction1[OrderedValues[Any], IndexedGenerator[RequestTemplateBuilderModifier]] {
    override def apply(v1: OrderedValues[Any]): IndexedGenerator[RequestTemplateBuilderModifier] = {
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(v1.getAll.map(anyToModifier(v1.name)
      ))
    }
  }

  val modifiersToCombinedModifier: SerializableFunction1[IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]], Batch[Modifier[RequestTemplateBuilder]]] = new SerializableFunction1[IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]], Batch[Modifier[RequestTemplateBuilder]]] {
    override def apply(v1: IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]]): Batch[Modifier[RequestTemplateBuilder]] = {
      val atomicInt = new AtomicInteger(0)
      val data: IndexedGenerator[Modifier[RequestTemplateBuilder]] = v1.mapGen(modifierSeqToCombined)
      Batch(batchNr = atomicInt.addAndGet(1), data = data)
    }
  }
  val dataToBatchGenerator: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[RequestTemplateBuilderModifier]]] = new SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[RequestTemplateBuilderModifier]]]() {
    override def apply(v1: OrderedMultiValues): IndexedGenerator[Batch[RequestTemplateBuilderModifier]] = {
      val paramGenerators: Seq[IndexedGenerator[RequestTemplateBuilderModifier]] = v1.values.map(orderedValuesToBatched)
      val batchGeneratorBySize: IndexedGenerator[IndexedGenerator[Seq[RequestTemplateBuilderModifier]]] = BatchByGeneratorIndexedGenerator(paramGenerators, 0)
      val atomicInt = new AtomicInteger(0)
      batchGeneratorBySize.mapGen(
        modifiersToCombinedModifier
//        x => {
//          val data: IndexedGenerator[Modifier[RequestTemplateBuilder]] = x.mapGen(y => CombinedModifier(y))
//          Batch(batchNr = atomicInt.addAndGet(1), data = data)
//        }
      )
    }
  }
  // ProcessJobCmd is command already to JobManager, so its usually what the supervisor will send after receiving ProcessActorRunnableJobCmd
  //    val processJobCmd: ProcessJobCmd[RequestTemplateBuilderModifier, RequestTemplate, MetricRow, MetricAggregation[Tag]] =
  //      ProcessJobCmd[RequestTemplateBuilderModifier, RequestTemplate, MetricRow, MetricAggregation[Tag]](job = batchGenerator)


  val fileWriter: FileWriter[String, Any] = LocalDirectoryFileFileWriter(directory = "/home/ed/REPOS/github/kolibri_release/kolibri-base/kolibri-test")

  val keyToFilenameFunc: SerializableFunction1[Tag, String] = new SerializableFunction1[Tag, String] {
    override def apply(v1: Tag): String = v1.toString
  }
  val documentWriter: Writer[MetricDocument[Tag], Tag, Any] = BaseMetricDocumentWriter(
    writer = fileWriter,
    format = CSVParameterBasedMetricDocumentFormat(columnSeparator = "/t"),
    keyToFilenameFunc = keyToFilenameFunc)
  val writer: Writer[MetricAggregation[Tag], Tag, Any] = BaseMetricAggregationWriter(writer = documentWriter)

  // define the message to supervisor which will generate ProcessJobCmd
  // TODO: we can only initialize this within some actor with a given actorcontext, so if we define this within routes
  // the respective actor wont be there yet to provide context, thus wrap this here to be able to create route without
  def processActorRunnableJobCmd(implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, (Either[Throwable, MetricRow], RequestTemplate), MetricRow, MetricAggregation[Tag]] =
    JobMsgFactory.createActorRunnableJobCmd[OrderedMultiValues, RequestTemplateBuilderModifier, (Either[Throwable, MetricRow], RequestTemplate), MetricRow, MetricAggregation[Tag]](
      jobId = "testJobId",
      data = requestParameterGrid,
      dataBatchGenerator = dataToBatchGenerator,
      transformerFlow = fullProcessingFlow(
        throughputActor = null,
        judgementProviderFactory = judgementProviderFactory,
        metricsCalculation = metricsCalculation),
      processingActorProps = None, // need to change this
      perBatchExpectationGenerator = expectationGen,
      perBatchAndOverallAggregatorSupplier = metricRowAggregatorSupplier,
      writer = writer,
      returnType = ActorRunnableSinkType.IGNORE_SINK,
      allowedTimePerElementInMillis = 100,
      allowedTimePerBatchInSeconds = 120,
      allowedTimeForJobInSeconds = 1200
    )

}

