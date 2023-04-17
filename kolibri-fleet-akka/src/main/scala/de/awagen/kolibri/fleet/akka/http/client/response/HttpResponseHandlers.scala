package de.awagen.kolibri.fleet.akka.http.client.response

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import de.awagen.kolibri.datatypes.io.BaseIO
import de.awagen.kolibri.fleet.akka.config.AppProperties.config.responseToStrictTimeoutInMs
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

object HttpResponseHandlers extends BaseIO {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def discardingConsume(httpResponse: HttpResponse)(implicit actorSystem: ActorSystem, mat: Materializer): Future[Done] = {
    httpResponse.entity.dataBytes
      .runWith(Sink.ignore)
  }

  /**
   * Assuming response to be json, generate parser given response, validation function, parsing function
   *
   * @param response    : HttpResponse
   * @param isValidFunc : validation function on the JsValue
   * @param parseFunc   : parsing function on the JsValue
   * @param actorSystem : implicit
   * @param mat         : implicit Materializer
   * @param ec          : implicit ExecutionContext
   * @tparam T : type the response is to be parsed to
   * @return
   */
  def doJSValueParse[T](response: HttpResponse,
                        isValidFunc: JsValue => Boolean,
                        parseFunc: JsValue => T)
                       (implicit actorSystem: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[Either[Throwable, T]] = {
    val strictEntity: Future[HttpEntity.Strict] = response.entity.toStrict(responseToStrictTimeoutInMs)
    strictEntity flatMap { e =>
      e.dataBytes
        .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
        .map(x => {
          try {
            val json: JsValue = jsonStringToJsValue(x.decodeString("UTF-8"))
            logger.debug(s"parsed response of type '${json.getClass}': $json")
            if (!isValidFunc(json)) {
              logger.debug(s"invalid response json: $json")
              throw new RuntimeException(s"invalid response: $json")
            }
            val product_ids: T = parseFunc.apply(json)
            logger.debug(s"parsed product_ids: $product_ids")
            Right(product_ids)
          }
          catch {
            case e: Throwable =>
              logger.warn(s"search response parsing failed: $e")
              Left(e)
          }
        })
    }
  }

}
