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

import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.fleet.zio.DataEndpoints.SummarizeCommand
import de.awagen.kolibri.fleet.zio.JobDefsServerEndpoints.{ID_JOB_DEF, ID_JOB_SUMMARY_DEF, ID_TASK_DEF}
import de.awagen.kolibri.fleet.zio.ServerEndpoints.ResponseContentProtocol.responseContentFormat
import de.awagen.kolibri.fleet.zio.ServerEndpoints.ResponseContent
import de.awagen.kolibri.fleet.zio.config.AppConfig.JsonFormats.executionFormat
import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.HttpConfig.corsConfig
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.metrics.Metrics.CalculationsWithMetrics.countAPIRequests
import de.awagen.kolibri.storage.io.reader.ReaderUtils.safeContentRead
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, JsValueFormat, StringJsonFormat, immSeqFormat, jsonFormat3, mapFormat}
import spray.json._
import zio.ZIO
import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.stream.ZStream

import java.nio.charset.Charset

object JobTemplatesServerEndpoints {

  val jobTemplatePath: String = AppProperties.config.jobTemplatesPath.get.stripSuffix("/")
  val contentInfoFileName = "info.json"
  val RESPONSE_TEMPLATE_KEY = "template"
  val RESPONSE_TEMPLATE_INFO_KEY = "info"

  case class TemplateInfo(templateType: String, templateId: String, content: JsValue)

  implicit val templateInfoFormat: RootJsonFormat[TemplateInfo] = jsonFormat3(TemplateInfo)

  def getJobTemplateTypes(dataOverviewReader: DataOverviewReader): zio.Task[Seq[String]] = {
    ZIO.attemptBlockingIO({
      dataOverviewReader
        .listResources(s"$jobTemplatePath", _ => true)
        .map(filepath => filepath.split("/").last.trim)
    })
  }

  def getJobTemplatesForType(templateType: String, dataOverviewReader: DataOverviewReader): zio.Task[Seq[String]] = {
    for {
      foundJsonFiles <- ZIO.attemptBlockingIO(dataOverviewReader.listResources(s"$jobTemplatePath/$templateType", x => x.endsWith(".json")))
      _ <- ZIO.logDebug(s"Found files for templateType '$templateType': $foundJsonFiles")
      results <- ZIO.attempt({
        foundJsonFiles
          .map(filepath => filepath.split("/").last)
          .filter(filename => filename != contentInfoFileName)
      })
    } yield results
  }

  def getTemplateTypeToAvailableTemplatesMapping(dataOverviewReader: DataOverviewReader,
                                                 contentReader: Reader[String, Seq[String]]): zio.Task[Map[String, Seq[TemplateInfo]]] = {
    for {
      templateTypes <- getJobTemplateTypes(dataOverviewReader)
      results <- ZStream.fromIterable(templateTypes)
        .mapZIO(templateType => {
          for {
            templateNames <- getJobTemplatesForType(templateType, dataOverviewReader)
            _ <- ZIO.logDebug(s"Found templates for type '$templateType': $templateNames")
            jobTemplates <- ZStream.fromIterable(templateNames)
              .map(template => {
                val relativePath = s"$jobTemplatePath/$templateType/$template"
                val content: String = safeContentRead(contentReader, relativePath, "{}", logOnFail = true)
                TemplateInfo(templateType, template, content.parseJson)
              }).runCollect
          } yield (templateType, jobTemplates)
        })
        .runCollect.map(x => x.toMap)
    } yield results
  }

  def templateEndpoints(dataOverviewReader: DataOverviewReader,
                        contentReader: Reader[String, Seq[String]],
                        writer: Writer[String, String, _]) = Http.collectZIO[Request] {
    case Method.GET -> Root / "jobs" / "templates" / "types" =>
      (for {
        result <- getJobTemplateTypes(dataOverviewReader)
        response <- ZIO.attempt(Response.json(ResponseContent(result, "").toJson.toString()))
      } yield response).catchAll(throwable =>
        ZIO.logError(s"Error reading available job templates:\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed(Response.json(ResponseContent("", "failed loading data").toJson.toString()).withStatus(Status.InternalServerError))
      ) @@ countAPIRequests("GET", "/jobs/templates/types")
    case Method.GET -> Root / "jobs" / "templates" / "types" / "perType" =>
      (for {
        mapping <- getTemplateTypeToAvailableTemplatesMapping(dataOverviewReader, contentReader)
        _ <- ZIO.logDebug(s"Found template per type mapping: $mapping")
        response <- ZIO.attempt(Response.json(ResponseContent(mapping, "").toJson.toString()))
      } yield response).catchAll(throwable =>
        ZIO.logError(s"Error reading template type to available job templates mapping:\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed(Response.json(ResponseContent("", s"failed loading data for template type to template mapping").toJson.toString()).withStatus(Status.InternalServerError))
      ) @@ countAPIRequests("GET", "/jobs/templates/types/perType")
    case Method.GET -> Root / "jobs" / "templates" / "types" / templateType =>
      (for {
        templates <- getJobTemplatesForType(templateType, dataOverviewReader)
        response <- ZIO.attempt(Response.json(ResponseContent(templates, "").toJson.toString()))
      } yield response).catchAll(throwable =>
        ZIO.logError(s"Error reading available job templates for type '$templateType':\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed(Response.json(ResponseContent("", s"failed loading data for template type '$templateType'").toJson.toString()).withStatus(Status.InternalServerError))
      ) @@ countAPIRequests("GET", "/jobs/templates/types/[templateType]")
    case Method.GET -> Root / "jobs" / "templates" / templateType / templateId =>
      ZIO.attemptBlockingIO({
        // retrieve the content of the definition file defined by type and identifier
        val filepath = s"$jobTemplatePath/$templateType/$templateId"
        val infoFilepath = s"$jobTemplatePath/$templateType/$contentInfoFileName"
        // pick the content
        val content: String = safeContentRead(contentReader, filepath, "{}", logOnFail = true)
        // pick the information corresponding to the job definition fields
        val contentFieldInfo: String = safeContentRead(contentReader, infoFilepath, "{}", logOnFail = false)
        content.trim match {
          case e if e.isEmpty =>
            Response.json(ResponseContent("", s"failed loading template for type '$templateType' and id '$templateId'").toJson.toString()).withStatus(Status.NotFound)
          case _ =>
            val contentAndInfo: JsValue = JsObject(Map(
              RESPONSE_TEMPLATE_KEY -> content.parseJson,
              RESPONSE_TEMPLATE_INFO_KEY -> contentFieldInfo.parseJson
            ))
            Response.json(ResponseContent(contentAndInfo, "").toJson.toString())
        }
      }).catchAll(throwable =>
        ZIO.logError(s"Error loading template for type '$templateType' and id '$templateId':\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed(Response.json(ResponseContent("", s"failed loading template for type '$templateType' and id '$templateId'").toJson.toString()).withStatus(Status.InternalServerError))
      ) @@ countAPIRequests("GET", "/jobs/templates/[templateType]/[templateId]")
    case req@Method.POST -> Root / "jobs" / "templates" / templateType / templateId =>
      val computeEffect = for {
        // validate name and type for template
        _ <- ZIO.attempt({
          if (templateId.trim.isEmpty || templateType.startsWith("..") || templateId.startsWith("..")) {
            throw new RuntimeException(s"TemplateType '$templateType' or templateName '$templateId' invalid.")
          }
        })
        // parse body and try to convert it to JobDefinition. If conversion fails, its not a valid format.
        jsonStr <- for {
          bodyStr <- req.body.asString(Charset.forName("UTF-8"))
          _ <- ZIO.whenCase(templateType)({
            case ID_JOB_DEF =>
              bodyStr.parseJson.convertTo[ZIO[Client, Throwable, JobDefinition[_, _, _ <: WithCount]]] *> ZIO.succeed(())
            case ID_TASK_DEF =>
              ZIO.attempt(bodyStr.parseJson.convertTo[Execution[Any]]) *> ZIO.succeed(())
            case ID_JOB_SUMMARY_DEF =>
              ZIO.attempt(bodyStr.parseJson.convertTo[SummarizeCommand]) *> ZIO.succeed(())
            case _ => ZIO.fail(new RuntimeException("Can not parse job/task definition"))
          })
        } yield bodyStr
        fullTemplateName <- ZIO.attempt({
          templateId.stripPrefix("/").stripSuffix("/").trim match {
            case n if n.endsWith(".json") => n
            case n => s"$n.json"
          }
        })
        response <- for {
          templateFilePath <- ZIO.succeed(s"$jobTemplatePath/$templateType/$fullTemplateName")
          overwrite <- ZIO.attempt(req.url.queryParams.getOrElse("overwrite", Seq("false")).head.toBoolean)
          fileExists <- ZIO.attemptBlockingIO({
            try {
              contentReader.getSource(templateFilePath)
              true
            }
            catch {
              case _: Exception => false
            }
          })
          writeResult <- ZIO.whenCase(fileExists && !overwrite)({
            case true => ZIO.attempt(Response.json(ResponseContent("", s"Template '$templateFilePath' already exists").toJson.toString()).withStatus(Status.Conflict))
            case false =>
              writer.write(jsonStr, templateFilePath) match {
                case Left(e) =>
                  ZIO.logError(s"""Error trying to write job template:\nmsg: ${e.getMessage}\nCause: ${e.getStackTrace.mkString("\n")}""") *>
                    ZIO.attempt(Response.json(ResponseContent(false, s"Failed to persist template for TemplateType '$templateType' and templateName '$templateId'").toJson.toString()).withStatus(Status.InternalServerError))
                case Right(_) =>
                  ZIO.attempt(Response.json(ResponseContent(true, "").toJson.toString()).withStatus(Status.Accepted))
              }
          })
        } yield writeResult.get
      } yield response
      computeEffect.catchAll(throwable => {
        ZIO.logError(s"Error reading registered jobs:\n${throwable.getStackTrace.mkString("\n")}") *>
          ZIO.succeed(Response.json(ResponseContent("", "failed persisting job template").toJson.toString()).withStatus(Status.InternalServerError))
      }) @@ countAPIRequests("POST", s"/jobs/templates/$templateType/[templateId]")
  } @@ cors(corsConfig)

}
