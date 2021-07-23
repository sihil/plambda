package plambda

import java.io._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.{Context => λContext}
import org.joda.time.DateTime
import plambda.LambdaEntrypoint.application
import play.api.libs.json.Json
import play.api.test.Writeables
import play.api.ApplicationLoader
import play.api._
import scala.language.postfixOps
import scala.util.control.NonFatal
import HttpUtils._

object LambdaEntrypoint {
  println("Starting and initialising!")
//  val region = Option(System.getenv("AWS_DEFAULT_REGION")).map(Regions.fromName).get
  implicit val system = ActorSystem("LambdaActorSystem")
  implicit val materializer = ActorMaterializer()
  // Start the application
  val application: Application = {
    val environment = Environment(new File("/"), getClass.getClassLoader, Mode.Prod)
    val context = ApplicationLoader.createContext(environment)
    val loader = ApplicationLoader(context)
    loader.load(context)
  }

  println("Created application")
  Play.start(application)
  println("Play application started")

  val PLAMBDA_ENDPOINT = "/plambda"
  val COOKIE_ENDPOINT = s"$PLAMBDA_ENDPOINT/moreCookies"
  val PING_ENDPOINT = s"$PLAMBDA_ENDPOINT/ping"

  var started = false
  def init(context: λContext) = {
    if (!started) {
      started = true
      context.getLogger.log(s"Initialised LambdaEntrypoint in ${application.mode.toString} mode")
    }
  }
}

class LambdaEntrypoint extends Writeables {

  def run(
      lambdaRequestStream: InputStream,
      lambdaResponseStream: OutputStream,
      context: λContext
  ): Unit = {
    implicit val logger = context.getLogger
    LambdaEntrypoint.init(context)
    logger.log(s"Running at ${DateTime.now()}")

    val lambdaRequestHandler = application.injector.instanceOf(classOf[LambdaRequestHandler])

    // actually call the router
    val maybeResponse: Option[LambdaResponse] =
      try {
        for {
          lambdaRequest: LambdaRequest <-
            RequestParser.fromStream(lambdaRequestStream, lambdaRequestHandler)
        } yield {
          lambdaRequest match {

            case HttpRequest("PING", _, _, _, _) =>
              logger.log("PING")
              // the purpose of the ping is to keep this as warm as possible, so make sure the application has been
              // initialised
              HttpResponse(HttpConstants.OK, body = "PONG")
            case HttpRequest(
                  "GET",
                  LambdaEntrypoint.COOKIE_ENDPOINT,
                  Some(queryParams),
                  Some(headers),
                  _) =>
              routeToPlambda(headers, LambdaEntrypoint.COOKIE_ENDPOINT, queryParams)
            case kinesis: LambdaRequest =>
              //handles play routing and kinesis requests
              lambdaRequestHandler.handleLambdaRequest(kinesis, context)(context.getLogger)
          }
        }
      } catch {
        case NonFatal(e) =>
          Some(
            HttpResponse(
              statusCode = HttpConstants.INTERNAL_SERVER_ERROR,
              body = stackTraceString(e)))
      }

    maybeResponse.foreach { response =>
      logger.log(s"Response object:\n$response")
      val json = response match {
        case http: HttpResponse => Json.toJson(http)
        case kinesis: KinesisResponse => Json.toJson(kinesis)
      }
      lambdaResponseStream.write(json.toString.getBytes("UTF-8"))
      lambdaResponseStream.flush()
      logger.log(s"Written JSON to response stream")
    }

    context.getLogger.log("Finished")
  }

  def routeToPlambda(
      requestHeaders: Map[String, String],
      endpointUrl: String,
      queryParams: Map[String, String]
  ): HttpResponse = {
    endpointUrl match {
      case LambdaEntrypoint.COOKIE_ENDPOINT =>
        cookieDataFromRequest(queryParams)
          .map {
            case (cookies, location) =>
              cookieResponse(requestHeaders, cookies, location)
          }
          .getOrElse(
            HttpResponse(
              HttpConstants.BAD_REQUEST,
              body = "Invalid request for Plaλ! cookie magic"))
      case _ => HttpResponse(HttpConstants.NOT_FOUND, body = "Magic Plaλ! route not found")
    }
  }

}
