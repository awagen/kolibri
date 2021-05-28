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
import akka.http.scaladsl.model.{HttpMethods, HttpProtocol, HttpProtocols}
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.ActorRunnableJobGenerator
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.ProcessJobCmd
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.{Batch, JobMsgFactory}
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilderModifiers.CombinedModifier
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder, RequestTemplateBuilderModifiers}
import de.awagen.kolibri.base.processing.execution.expectation.{BaseExecutionExpectation, ClassifyingCountExpectation, ExecutionExpectation, StopExpectation, TimeExpectation}
import de.awagen.kolibri.base.processing.execution.job.{ActorRunnable, ActorRunnableSinkType}
import de.awagen.kolibri.base.types.Modifier
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.SearchOptDefinitions.paramGenerators
import de.awagen.kolibri.base.usecase.searchopt.metrics.Metrics._
import de.awagen.kolibri.base.usecase.searchopt.metrics.{JudgementHandlingStrategy, MetricsCalculation, MetricsEvaluation}
import de.awagen.kolibri.base.usecase.searchopt.provider.{ClassPathFileBasedJudgementProviderFactory, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.collections.BaseBatchIterable
import de.awagen.kolibri.datatypes.collections.generators.{BatchByGeneratorIndexedGenerator, ByFunctionNrLimitedIndexedGenerator, IndexedGenerator, PermutatingIndexedGenerator}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.{TagType, TaggedWithType, Tags}
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.utils.ParameterUtils
import de.awagen.kolibri.datatypes.values.{DistinctValues, RangeValues}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.{Aggregator, TagKeyMetricAggregationPerClassAggregator}

import scala.collection.immutable
import scala.math.{Fractional, exp}
import scala.concurrent.duration._

object SearchOptDefinitions {

  // 1) define parts needed
  val aggregatorSupplier: SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]]] =
    new SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]]] {
      override def get(): Aggregator[TaggedWithType[Tag] with DataStore[MetricRow], MetricAggregation[Tag]] = new TagKeyMetricAggregationPerClassAggregator()
    }
  // some connections to send the requests to (connections go into flow definition of the execution)
  val connections: Seq[Connection] = Seq(
    Connection(host = "host1", port = 8998, useHttps = false, credentialsProvider = None)
  )
  val judgementProviderFactory: JudgementProviderFactory[Double] = ClassPathFileBasedJudgementProviderFactory("judgementFile")
  val metricsCalculation: MetricsCalculation = MetricsCalculation(metrics = Seq(NDCG_10, PRECISION_4, ERR), judgementHandling = JudgementHandlingStrategy.default)
  val metricsEvaluation: MetricsEvaluation = MetricsEvaluation(judgementProviderFactory,
    metricsCalculation: MetricsCalculation)
  // requestBuilderSupplier provides base RequestTemplateBuilder with contextPath preset
  // modifications before building the request are done using the IndexedGenerators[Seq[Modifier[RequestTemplateBuilder]]]
  val fixedParams: Map[String, Seq[String]] = Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("v3"))
  val requestBuilderSupplier: SerializableSupplier[RequestTemplateBuilder] = new SerializableSupplier[RequestTemplateBuilder] {
    override def get(): RequestTemplateBuilder = new RequestTemplateBuilder()
      .withContextPath("/searchpath")
      .withParams(fixedParams)
  }
  // in dataGrid need distinction between normal values, headers, body and so on to correctly modify the request context
  val requestParameterGrid: OrderedMultiValues = GridOrderedMultiValues.apply(Seq(
    DistinctValues[String]("paramName1", Seq("v1", "v2", "v3", "v4")),
    RangeValues[Float](name = "rangeParam1", start = 0.0F, end = 10.0F, stepSize = 1.0F)
  ))
  // OrderedMultiValues to generators of parameter modifiers
  val paramGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = requestParameterGrid.values.map(x =>
    ByFunctionNrLimitedIndexedGenerator.createFromSeq(x.getAll.map(y =>
      RequestTemplateBuilderModifiers.RequestParameterModifier(params = immutable.Map(x.name -> Seq(y.toString)), replace = false)
    ))
  )
  // define some batching strategy on the above using BatchByGeneratorIndexedGenerator (e.g can use one generator as criterium for batching,
  // can just split overall generator into its supparts and so on (we tag individually, to in which batch a query is doesnt matter except
  // in case of error criteria based on single class (usually represented by one query)
  val batchesBasedOnParameter0: Iterator[IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]]] = BatchByGeneratorIndexedGenerator(paramGenerators, 0).iterator
  // alternatively by batchSize (TODO: rather put this into batching generator to be able to pick distinct batches without iterating all)
  val batchSize: Int = 1000
  val iterable: Iterable[Iterable[Seq[Modifier[RequestTemplateBuilder]]]] = BaseBatchIterable(PermutatingIndexedGenerator(paramGenerators), batchSize)
  //so we use the implementation of a sized batch generator
  val batchGeneratorBySize: IndexedGenerator[IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]]] = BatchByGeneratorIndexedGenerator(paramGenerators, batchSize)

  // define permutating generator given the sequence of generators of RequestTemplateModifiers
  val permutateParamGenerator: IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]] = PermutatingIndexedGenerator(paramGenerators)
  // define putting RequestTemplate in ProcessingMessage (Corn) and tag according to some variable
  val templateToTaggedPM: SerializableFunction1[RequestTemplate, ProcessingMessage[RequestTemplate]] = new SerializableFunction1[RequestTemplate, ProcessingMessage[RequestTemplate]] {
    override def apply(v1: RequestTemplate): ProcessingMessage[RequestTemplate] = {
      Corn(v1).withTags(TagType.AGGREGATION, Set(Tags.ParameterSingleValueTag(Map("q" -> v1.getParameter("q").map(x => x.mkString("-")).getOrElse("")))))
    }
  }
  // TODO: define the parameters that are changed and need to be in the per-tag result files

  // TODO: define ProcessJobCmd
  // type ActorRunnableJobGenerator[U, V, V1, W] =  IndexedGenerator[ActorRunnable[U, V, V1, W]]
  // instead of Seq[Modifi[...]] group all modifiers in a single CombinedModifier below
  //  val genMap: SerializableFunction1[Seq[Modifier[RequestTemplateBuilder]], Modifier[RequestTemplateBuilder]] = new SerializableFunction1[Seq[Modifier[RequestTemplateBuilder]], Modifier[RequestTemplateBuilder]] {
  //    override def apply(v1: Seq[Modifier[RequestTemplateBuilder]]): Modifier[RequestTemplateBuilder] =
  //      CombinedModifier(v1)
  //
  //  }
  val singleModifierBatchGenerator: IndexedGenerator[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = batchGeneratorBySize.mapGen(x => x.mapGen(y => CombinedModifier(y)))
  val requestTemplateBuilderSupplier: SerializableSupplier[RequestTemplateBuilder] = new SerializableSupplier[RequestTemplateBuilder] {
    override def get(): RequestTemplateBuilder = new RequestTemplateBuilder()
      .withContextPath("testpath")
      .withProtocol(HttpProtocols.`HTTP/1.1`)
      .withHttpMethod(HttpMethods.GET)
      .withParams(Map("k1" -> Seq("v1")))
  }
  // the processing flow of each combination
  // TODO: we actually gotta put in the flow the requesting via the distinct defined
  // connections and send it to worker actor extracting the metrics
  val processingFlow: Flow[Modifier[RequestTemplateBuilder], ProcessingMessage[RequestTemplate], NotUsed] = Flow.fromFunction[Modifier[RequestTemplateBuilder], ProcessingMessage[RequestTemplate]](modifier => {
    val requestTemplateBuilder: RequestTemplateBuilder = requestTemplateBuilderSupplier.get()
    val requestTemplate: RequestTemplate = modifier.apply(requestTemplateBuilder).build()
    Corn(requestTemplate)
      // tag the processing element
      .withTags(TagType.AGGREGATION,
        Set(Tags.ParameterSingleValueTag(
          Map("q" -> requestTemplate.getParameter("q").map(x => x.mkString("-")).getOrElse(""))
        ))
      )
  })

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

  val batchGenerator: IndexedGenerator[ActorRunnable[Modifier[RequestTemplateBuilder], RequestTemplate, MetricRow, MetricAggregation[Tag]]] =
    singleModifierBatchGenerator.mapGen(x => {
      // TODO: need to define processingActorProps
      ActorRunnable("testJobId", 0, x, processingFlow, None, expectationGen, aggregatorSupplier,
        ActorRunnableSinkType.IGNORE_SINK, 2 minutes, 3 seconds)
    })
  // the actual processing command to send that will take care of execution when sent
  // to JobManagerActor
  // ProcessJobCmd is actually the task within JobManager, for supervisor message with writer
  // and so on we need ProcessActorRunnableJobCmd (see below)
  val dataToBatchGenerator: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]]] = new SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]]](){
    override def apply(v1: OrderedMultiValues): IndexedGenerator[Batch[Modifier[RequestTemplateBuilder]]] = {
      val paramGenerators: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = v1.values.map(x =>
        ByFunctionNrLimitedIndexedGenerator.createFromSeq(x.getAll.map(y =>
          RequestTemplateBuilderModifiers.RequestParameterModifier(params = immutable.Map(x.name -> Seq(y.toString)), replace = false)
        ))
      )
      val batchGeneratorBySize: IndexedGenerator[IndexedGenerator[Seq[Modifier[RequestTemplateBuilder]]]] = BatchByGeneratorIndexedGenerator(paramGenerators, batchSize)
      // TODO: correct batchNr here
      batchGeneratorBySize.mapGen(x => Batch(batchNr = 1, data = x.mapGen(y => CombinedModifier(y))))
    }
  }
  val processJobCmd: ProcessJobCmd[Modifier[RequestTemplateBuilder], RequestTemplate, MetricRow, MetricAggregation[Tag]] =
    ProcessJobCmd[Modifier[RequestTemplateBuilder], RequestTemplate, MetricRow, MetricAggregation[Tag]](job = batchGenerator)
  // define the message to supervisor which will generate ProcessJobCmd:
  val processActorRunnableJobCmd: SupervisorActor.ProcessActorRunnableJobCmd[Modifier[RequestTemplateBuilder], RequestTemplate, MetricRow, MetricAggregation[Tag]] = JobMsgFactory.createActorRunnableJobCmd(
    jobId = "testJobId",
    data = requestParameterGrid,
    dataBatchGenerator =  dataToBatchGenerator,
    transformerFlow = processingFlow,
    // TODO: correct empty actor prop here if needed
    processingActorProps = None, // need to change this
    expectationGenerator = expectationGen,
    aggregatorSupplier = aggregatorSupplier,
    // TODO: correct writer here
    writer = null,
    returnType = ActorRunnableSinkType.IGNORE_SINK,
    allowedTimePerBatchInSeconds = 120,
    allowedTimeForJobInSeconds = 1200
    )


  // TODO: instead of deriving parameters only from the OrderedMultiValues, make some
  // provider of Modifier[RequestTemplateProvider] configurable and add parsers, such as
  // a) define parameters as OrderedMultiValues
  // b) define headers by header provider
  // c) define body by body provider
  // ... and then collect all of them and provide some merging strategy (e.g combining userId and related queries and permutate those together)

}
