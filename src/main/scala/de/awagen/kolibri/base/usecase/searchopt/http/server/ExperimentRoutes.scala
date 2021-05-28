//package de.awagen.kolibri.base.usecase.searchopt.http.server
//
//import akka.actor.ActorSystem
//import akka.http.scaladsl.model.StatusCodes
//import akka.http.scaladsl.server.Route
//import de.awagen.kolibri.base.actors.work.aboveall.Supervisor.ProcessBatchJobCmd
//import de.awagen.kolibri.base.usecase.searchopt.domain.SearchEvaluationDefinition
//import org.slf4j.{Logger, LoggerFactory}
//
////route examples: https://doc.akka.io/docs/akka-http/current/introduction.html#using-akka-http
//trait ExperimentRoutes {
//
//  private val logger: Logger = LoggerFactory.getLogger(classOf[ExperimentRoutes])
//
//  def startSearchExperimentRoute(implicit system: ActorSystem): Route = {
//    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
//    import akka.http.scaladsl.server.Directives._
//    import de.awagen.kolibri.base.usecase.searchopt.io.json.SearchEvaluationDefinitionJsonProtocol._
//
//    path("runexperiment") {
//      post {
//        extractRequest { request =>
//          entity(as[SearchEvaluationDefinition]) { definition =>
////            supervisor ! ProcessBatchJobCmd(definition)
//            complete(StatusCodes.OK)
//          }
//        }
//      }
//    }
//  }
//
//}
