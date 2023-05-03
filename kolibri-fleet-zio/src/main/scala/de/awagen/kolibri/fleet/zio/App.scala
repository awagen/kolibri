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


package de.awagen.kolibri.fleet.zio

import de.awagen.kolibri.fleet.zio.config.ZIOConfig
import de.awagen.kolibri.fleet.zio.schedule.Schedules
import zio._
import zio.http._
import zio.logging.LogFormat
import zio.logging.backend.SLF4J

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Nothing, Unit] = SLF4J.slf4j(LogLevel.Info, LogFormat.colored)

  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }

  override val run: ZIO[Any, Throwable, Any] = {
    val fixed = Schedule.fixed(1.minute)
    val rio: RIO[Any, Option[Seq[String]]] = Schedules.taskCheckSchedule("")
    for {
      zioConfig <- ZIO.succeed(new ZIOConfig())
      _ <- zioConfig.init()
      jobHandler <- ZIO.succeed(zioConfig.getJobHandler)
      zioHttp <- ZIO.succeed({
        Http.collectZIO[Request] {
              case Method.GET -> !! / "registeredJobs" => jobHandler.registeredJobs.map(jobs => Response.text(s"Files: ${jobs.mkString(",")}"))
            }.catchAllZIO(x => ZIO.succeed(Response.text(x.toString)))
      })
      _ <- ZIO.logInfo("Application started!")
      _ <- Runtime.default.run(rio)
      _ <- Runtime.default.run(rio.repeat(fixed)).fork
      _ <- Runtime.default.run(Schedules.findAndRegisterJobs(jobHandler).repeat(fixed)).fork
      _ <- Server.serve(app ++ zioHttp).provide(Server.default)
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ()
  }
}
