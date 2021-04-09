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

package de.awagen.kolibri.base.http.server

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, get, onSuccess, parameters, path, post}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor.{MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor._
import de.awagen.kolibri.base.cluster.ClusterStatus
import de.awagen.kolibri.base.config.AppConfig.config.internalJobStatusRequestTimeout
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions
import org.slf4j.{Logger, LoggerFactory}

import java.util.Objects
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Random


/**
  * route examples: https://doc.akka.io/docs/akka-http/current/introduction.html#using-akka-http
  */
object BaseRoutes {

  val logger: Logger = LoggerFactory.getLogger(BaseRoutes.getClass)
  private[this] var supervisorActor: ActorRef = _

  def init(implicit system: ActorSystem): Unit = {
    this.synchronized {
      if (Objects.isNull(supervisorActor)) {
        supervisorActor = system.actorOf(SupervisorActor.props(true)
          .withDispatcher("kolibri-dispatcher"))
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

  lazy val simpleHelloRoute: Route =
    path("hello") {
      get {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            "<h1>'Hi' by Kolibri</h1>"))
      }
    }


  val numbers: Source[Int, NotUsed] = Source.fromIterator(() =>
    Iterator.continually(Random.nextInt())).take(100)

  // just an example of utilizing streaming response
  lazy val streamingUserRoutes: Route =
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
    }


  def clusterStatusRoutee(implicit system: ActorSystem): Route = {
    val clusterStatusLoggingActor: ActorRef = system.actorOf(ClusterMetricsListenerActor.props, "RouteClusterMetricsActor")
    implicit val timeout: Timeout = 10.seconds

    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import akka.http.scaladsl.server.Directives._
    import de.awagen.kolibri.base.cluster.ClusterStatusImplicits._
    import spray.json.DefaultJsonProtocol._

    path("clusterstatus") {
      get {
        onSuccess(clusterStatusLoggingActor ? ProvideMetrics) {
          case MetricsProvided(stats: Seq[ClusterStatus]) => complete(stats)
          case _ => complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def killAllJobs(implicit system: ActorSystem): Route = {
    path("killall") {
      post {
        supervisorActor ! KillAllChildren
        complete(StatusCodes.OK)
      }
    }
  }

  // the division of pi * r^2 / (2*r)^2 for throwing dart in unit-square with circle of radius 0.5
  // for unit-sware is pi / 4 -> calculate freq of darts within circle, multiply by 4, gives pi
  // estimation will be more accurate for more darts
  def executeDistributedPiCalculationExample(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    path("pi_calc") {
      parameters("jobName", "nrThrows", "batchSize", "resultDir") { (jobName, nrThrows, batchSize, resultDir) => {
        val actorRunnableJob = TestJobDefinitions.piEstimationJob(jobName, nrThrows.toInt, batchSize.toInt, resultDir)
        supervisorActor ! actorRunnableJob
        complete(StatusCodes.Accepted, "Processing Pi Calculation Example")
      }
      }
    }
  }

  def getJobWorkerStatus(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    path("job_worker_status") {
      parameters("jobId") { jobId => {
        onSuccess(supervisorActor ? GetJobWorkerStatus(jobId)) {
          e => complete(e.toString)
        }
      }
      }
    }
  }

  def getRunningJobIds(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    path("getRunningJobIDs") {
      onSuccess(supervisorActor ? ProvideAllRunningJobIDs) {
        e => complete(e.toString)
      }
    }
  }


  def getJobStatus(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    path("getJobStatus") {
      parameters("jobId") {
        jobId => {
          onSuccess(supervisorActor ? ProvideJobState(jobId)) {
            e => complete(e.toString)
          }
        }
      }
    }
  }

  def killJob(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    path("stopJob") {
      parameters("jobId") {
        jobId => {
          onSuccess(supervisorActor ? StopJob(jobId)) {
            e => complete(e.toString)
          }
        }
      }
    }
  }
}
