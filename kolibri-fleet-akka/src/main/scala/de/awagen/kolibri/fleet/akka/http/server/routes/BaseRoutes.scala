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


package de.awagen.kolibri.fleet.akka.http.server.routes

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, get, onSuccess, parameters, path, post, redirect}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.status.ClusterStates.ClusterStatus
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ClusterMetricsListenerActor
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ClusterMetricsListenerActor.{MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor._
import de.awagen.kolibri.fleet.akka.config.AppConfig
import de.awagen.kolibri.fleet.akka.config.AppConfig.JsonFormats.executionFormat
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.{analyzeTimeout, internalJobStatusRequestTimeout, kolibriDispatcherName}
import de.awagen.kolibri.fleet.akka.http.server.routes.JobTemplateResourceRoutes.TemplateTypeValidationAndExecutionInfo
import de.awagen.kolibri.fleet.akka.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.fleet.akka.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.definitions.processing.JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition, TestPiCalculationDefinition}
import de.awagen.kolibri.fleet.akka.processing.JobMessagesImplicits.QueryBasedSearchEvaluationImplicits
import de.awagen.kolibri.fleet.akka.usecase.searchopt.jobdefinitions.SearchJobDefinitions
import org.slf4j.{Logger, LoggerFactory}
import spray.json.{RootJsonFormat, enrichAny}

import java.util.Objects
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Random

/**
  * route examples: https://doc.akka.io/docs/akka-http/current/introduction.html#using-akka-http
  */
object BaseRoutes {

  implicit val sf: RootJsonFormat[SearchEvaluationDefinition] = AppConfig.JsonFormats.searchEvaluationJsonProtocol.SearchEvaluationFormat
  implicit val qsf: RootJsonFormat[QueryBasedSearchEvaluationDefinition] = AppConfig.JsonFormats.queryBasedSearchEvaluationJsonProtocol.QueryBasedSearchEvaluationFormat

  private[this] val logger: Logger = LoggerFactory.getLogger(BaseRoutes.getClass)

  private[routes] var supervisorActor: ActorRef = _
  private[routes] var clusterMetricsListenerActor: ActorRef = _

  def init(implicit system: ActorSystem): Unit = {
    this.synchronized {
      if (Objects.isNull(supervisorActor)) {
        supervisorActor = system.actorOf(SupervisorActor.props(true)
          .withDispatcher(kolibriDispatcherName))
      }
      if (Objects.isNull(clusterMetricsListenerActor)) {
        clusterMetricsListenerActor = system.actorOf(ClusterMetricsListenerActor.props)
      }
    }
  }

  def extractHeaderValue(headerName: String): Seq[HttpHeader] => Option[String] = {
    x => {
      val value = x.filter(y => y.name() == headerName)
      if (value.nonEmpty) Some(value.head.value())
      else None
    }
  }

  lazy val simpleHelloRoute: Route = {
    corsHandler(
      path("hello") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              "<h1>'Hi' by Kolibri</h1>"))
        }
      })
  }


  val numbers: Source[Int, NotUsed] = Source.fromIterator(() =>
    Iterator.continually(Random.nextInt())).take(100)

  // just an example of utilizing streaming response
  lazy val streamingUserRoutes: Route =
    corsHandler(
      path("random") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              // transform each number to a chunk of bytes
              numbers.map(n => {
                println(n)
                ByteString(s"$n\n")
              })
            )
          )
        }
      })


  def clusterStatusRoutee(implicit system: ActorSystem): Route = {
    val clusterStatusLoggingActor: ActorRef = system.actorOf(ClusterMetricsListenerActor.props, "RouteClusterMetricsActor")
    implicit val timeout: Timeout = 10.seconds
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import akka.http.scaladsl.server.Directives._
    import de.awagen.kolibri.definitions.status.ClusterStates.ClusterStatusImplicits._
    import spray.json.DefaultJsonProtocol._
    corsHandler(
      path("clusterstatus") {
        get {
          onSuccess(clusterStatusLoggingActor ? ProvideMetrics) {
            case MetricsProvided(stats: Seq[ClusterStatus]) => complete(stats)
            case _ => complete(StatusCodes.NotFound)
          }
        }
      })
  }

  def killAllJobs(implicit system: ActorSystem): Route = {
    corsHandler(
      path("killall") {
        delete {
          supervisorActor ! KillAllChildren
          complete(StatusCodes.OK)
        }
      })
  }

  // the division of pi * r^2 / (2*r)^2 for throwing dart in unit-square with circle of radius 0.5
  // for unit-sware is pi / 4 -> calculate freq of darts within circle, multiply by 4, gives pi
  // estimation will be more accurate for more darts
  def executeDistributedPiCalculationExample(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("pi_calc") {
        post {
          parameters("jobName", "nrThrows", "batchSize", "resultDir") { (jobName, nrThrows, batchSize, resultDir) => {
            val actorRunnableJob = TestJobDefinitions.piEstimationJob(jobName, nrThrows.toInt, batchSize.toInt, resultDir)
            supervisorActor ! actorRunnableJob
            complete(StatusCodes.Accepted, "Processing Pi Calculation Example")
          }
          }
        }
      })
  }

  def executeDistributedPiCalculationExampleWithoutSerialization(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("pi_calc_no_ser") {
        post {
          parameters("jobName", "requestTasks", "nrThrows", "batchSize", "resultDir") { (jobName, requestTasks, nrThrows, batchSize, resultDir) => {
            val msg = TestPiCalculationDefinition(jobName, requestTasks.toInt, nrThrows.toInt, batchSize.toInt, resultDir)
            supervisorActor ! msg
            complete(StatusCodes.Accepted, "Processing Pi Calculation (without full ActorRunnable serialization) Example")
          }
          }
        }
      })
  }

  def killJob(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("stopJob") {
        delete {
          parameters("jobId") {
            jobId => {
              onSuccess(supervisorActor ? StopJob(jobId)) {
                e => complete(e.toString)
              }
            }
          }
        }
      })
  }

  def startSearchEval(implicit system: ActorSystem): Route = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("search_eval") {
        post {
          entity(as[SearchEvaluationDefinition]) {
            searchEvaluation =>
              supervisorActor ! SearchJobDefinitions.searchEvaluationToRunnableJobCmd(searchEvaluation)
              complete(StatusCodes.Accepted, "Starting search evaluation example")
          }
        }
      })
  }

  def startSearchEvalNoSerialize(implicit system: ActorSystem): Route = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("search_eval_no_ser") {
        post {
          entity(as[SearchEvaluationDefinition]) {
            searchEvaluation =>
              supervisorActor ! searchEvaluation
              complete(StatusCodes.Accepted, "Starting search evaluation example")
          }
        }
      })
  }

  def startQueryBasedReducedSearchEvalNoSerialize(implicit system: ActorSystem): Route = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("search_eval_query_reduced") {
        post {
          entity(as[QueryBasedSearchEvaluationDefinition]) {
            searchEvaluation =>
              supervisorActor ! searchEvaluation.toFullSearchEvaluation
              complete(StatusCodes.Accepted, "Starting search evaluation example")
          }
        }
      })
  }

  /**
    * Route to shoot a json job definition of type given by type-parameter.
    * The type parameter needs to be found within
    * TemplateTypeValidationAndExecutionInfo.getByNameFunc function
    * to be executed (the matched enum also provides the execution path to redirect to),
    * otherwise its declined.
    * @param system - ActorSystem
    * @return
    */
  def startExecutionDefinition(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("execute_job") {
        post {
          parameters("type") { typeName => {
            logger.debug(s"received execution request for type '$typeName'")
            val templateInfoOpt: Option[TemplateTypeValidationAndExecutionInfo.Val[_]] = TemplateTypeValidationAndExecutionInfo.getByNameFunc()(typeName)
            if (templateInfoOpt.isEmpty) {
              logger.debug(s"no valid template type found for '$typeName'")
              complete(StatusCodes.BadRequest, s"Invalid type '$typeName'")
            }
            else {
              val normedExecutionPath: String = templateInfoOpt.get.requestPath.stripPrefix("/")
              logger.debug(s"redirecting execution request for '$typeName' to path '$normedExecutionPath'")
              redirect(s"/$normedExecutionPath", StatusCodes.PermanentRedirect)
            }
          }
          }
        }
      })
  }

  def startExecution(implicit system: ActorSystem): Route = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    implicit val timeout: Timeout = Timeout(analyzeTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)

    corsHandler(
      path("execution") {
        post {
          entity(as[Execution[Any]]) {
            execution =>
              supervisorActor ! execution
              complete(StatusCodes.Accepted, "Starting execution")
          }
        }
      })
  }

  def getJudgements(implicit system: ActorSystem): Route = {
    corsHandler(
      path("judgements") {
        get {
          parameters("query", "productId", "judgementFilePath") {
            (query, productId, filePath) => {
              val provider: JudgementProvider[Double] = AppConfig.filepathToJudgementProvider(filePath)
              val judgement: Option[Double] = provider.retrieveJudgement(query, productId)
              complete(judgement.toString)
            }
          }
        }
      })
  }

  def getAllJudgements(implicit system: ActorSystem): Route = {
    import spray.json.DefaultJsonProtocol._
    corsHandler(
      path("allJudgements") {
        get {
          parameters("query", "judgementFilePath") {
            (query, filePath) => {
              val provider: JudgementProvider[Double] = AppConfig.filepathToJudgementProvider(filePath)
              val judgements: Map[String, Double] = provider.retrieveJudgementsForTerm(query)
              complete(judgements.toJson.toString())
            }
          }
        }
      })
  }
}
