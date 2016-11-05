package plambda

import java.io._
import java.net.{URL, URLEncoder}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.amazonaws.HttpMethod
import com.amazonaws.services.lambda.runtime.{LambdaLogger, Context => λContext}
import com.amazonaws.services.s3.model.{GeneratePresignedUrlRequest, ObjectMetadata, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{Cookie, Cookies, Result}
import play.api.test.{Helpers, Writeables}
import play.api.{ApplicationLoader, _}

import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.control.NonFatal

object LambdaEntrypoint {
  println("Starting and initialising!")
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

  val s3Client = new AmazonS3Client()

  val PLAMBDA_ENDPOINT = "/plambda"
  val COOKIE_ENDPOINT = s"$PLAMBDA_ENDPOINT/moreCookies"
  val PING_ENDPOINT = s"$PLAMBDA_ENDPOINT/ping"
}

class LambdaEntrypoint extends Writeables {

  def run(lambdaRequestStream: InputStream, lambdaResponseStream: OutputStream, context: λContext): Unit = {
    implicit val logger = context.getLogger
    logger.log(s"Running at ${DateTime.now()}")

    // actually call the router
    val maybeResponse: Option[LambdaResponse] = try {
      for {
        lambdaRequest <- RequestParser.fromStream(lambdaRequestStream)
      } yield {
        lambdaRequest match {
          case LambdaRequest("PING", _, _, _, _) =>
            logger.log("PING")
            LambdaResponse(HttpConstants.OK, body="PONG")
          case LambdaRequest("GET", LambdaEntrypoint.COOKIE_ENDPOINT, Some(queryParams), Some(headers), _) =>
            routeToPlambda(headers, LambdaEntrypoint.COOKIE_ENDPOINT, queryParams)
          case playRequest =>
            routeToPlay(playRequest, context)
        }
      }
    } catch {
      case NonFatal(e) => Some(LambdaResponse(statusCode = HttpConstants.INTERNAL_SERVER_ERROR, body = stackTraceString(e)))
    }

    maybeResponse.foreach{ response =>
      logger.log(s"Response object:\n$response")
      val json = Json.toJson(response)
      lambdaResponseStream.write(json.toString.getBytes("UTF-8"))
      lambdaResponseStream.flush()
      logger.log(s"Written JSON to response stream")
    }

    context.getLogger.log("Finished")
  }

  def routeToPlay(lambdaRequest: LambdaRequest, context: λContext)(implicit logger: LambdaLogger): LambdaResponse = {
    val playRequest = RequestParser.transform(lambdaRequest)
    val maybeResult = Helpers.route(LambdaEntrypoint.application, playRequest)(Helpers.writeableOf_AnyContentAsEmpty)
    logger.log(maybeResult.fold(s"Route not found for $lambdaRequest")(_ => s"Successfully routed $lambdaRequest"))

    import Helpers.defaultAwaitTimeout
    import LambdaEntrypoint.materializer

    maybeResult.fold {
      LambdaResponse(HttpConstants.NOT_FOUND, body = "Route not found")
    } { futureResult =>
      val bytes = Helpers.contentAsBytes(futureResult)
      val result: Result = Await.result(futureResult, Helpers.defaultAwaitTimeout.duration)
      val cookiesToSet = Cookies.fromSetCookieHeader(result.header.headers.get(HttpConstants.SET_COOKIE))

      val tooManyCookies = cookiesToSet.size > 1
      val isBinaryData = HttpConstants.isBinaryType(result.body.contentType)
      val redirectLocation = Helpers.redirectLocation(futureResult)

      (lambdaRequest.headers, isBinaryData, tooManyCookies, redirectLocation) match {
        case (Some(requestHeaders), _, true, Some(location)) =>
          // multiple cookies with a redirect
          cookieResponse(requestHeaders, cookiesToSet, location)

        case (_, true, _, _) =>
          // binary body - upload and redirect to S3
          val headers = headersToSend(result, getSingleCookieLogRemainder(cookiesToSet, logger))
          val url = binaryDataS3Url("flexible-restorer-lambda-code2-binary-data",
            s"${context.getAwsRequestId}${lambdaRequest.path}", bytes, headers, LambdaEntrypoint.s3Client)
          LambdaResponse(HttpConstants.SEE_OTHER, Map("Location" -> url.toString))

        case (_, false, _, _) =>
          // text body - parse as UTF-8 and return
          val body = bytes.decodeString("utf-8")
          val headers = headersToSend(result, getSingleCookieLogRemainder(cookiesToSet, logger))
          LambdaResponse(result.header.status, headers, body)
      }
    }
  }

  def routeToPlambda(requestHeaders: Map[String, String], endpointUrl: String, queryParams: Map[String, String]): LambdaResponse = {
    endpointUrl match {
      case LambdaEntrypoint.COOKIE_ENDPOINT =>
        cookieDataFromRequest(queryParams).map{ case (cookies, location) =>
          cookieResponse(requestHeaders, cookies, location)
        }.getOrElse(LambdaResponse(HttpConstants.BAD_REQUEST, body = "Invalid request for Plaλ! cookie magic"))
      case _ => LambdaResponse(HttpConstants.NOT_FOUND, body = "Magic Plaλ! route not found")
    }
  }

  private def getSingleCookieLogRemainder(cookies: Cookies, logger: LambdaLogger) = cookies.toList match {
    case cookie :: otherCookies =>
      otherCookies.foreach { cookie =>
        logger.log(s"WARNING: Not setting cookie ${cookie.name} due to AWS API Gateway limitation")
      }
      Some(cookie)
    case _ => None
  }

  private def headersToSend(result: Result, maybeCookie: Option[Cookie]): Map[String, String] = result.header.headers ++
    result.body.contentType.map(HttpConstants.CONTENT_TYPE ->) ++
    result.body.contentLength.map(HttpConstants.CONTENT_LENGTH -> _.toString) ++
    maybeCookie.map(cookie => HttpConstants.SET_COOKIE -> Cookies.encodeSetCookieHeader(Seq(cookie)))

  /**
    * Push the data to S3 and return the URL
    *
    * @param bucket Name of S3 bucket to push data to
    * @param key Path in S3 bucket
    * @param bytes Data to upload
    * @param headersToSend The headers that should be delivered
    * @return
    */
  private def binaryDataS3Url(bucket: String, key: String, bytes: ByteString, headersToSend: Map[String, String],
    s3Client: AmazonS3): URL = {

    val bais = new ByteArrayInputStream(bytes.toArray)
    val metadata = new ObjectMetadata()
    headersToSend.foreach { case (name, value) => metadata.setHeader(name, value) }
    metadata.setContentLength(bytes.length)

    val request = new PutObjectRequest(bucket, key, bais, metadata)
    s3Client.putObject(request)

    val generatePresignedUrlRequest: GeneratePresignedUrlRequest =
      new GeneratePresignedUrlRequest(bucket, key)
        .withMethod(HttpMethod.GET)
        .withExpiration(new DateTime().plusMinutes(5).toDate)

    s3Client.generatePresignedUrl(generatePresignedUrlRequest)
  }

  /** Create a response for setting multiple cookies
    *
    * @param requestHeaders The original incoming request headers
    * @param cookies The list of cookies that need to be set
    * @param finalLocation The final URL to send the user to
    * @return
    */
  private def cookieResponse(requestHeaders: Map[String, String], cookies: Cookies, finalLocation: String): LambdaResponse = {
    val cookie :: otherCookies = cookies.toList
    val nextLocation: String =
      if (otherCookies.isEmpty) {
        finalLocation
      } else {
        cookieRedirectLocation(requestHeaders, otherCookies, finalLocation)
      }

    LambdaResponse(
      statusCode = HttpConstants.SEE_OTHER,
      headers = Map(
        HttpConstants.LOCATION -> nextLocation,
        HttpConstants.SET_COOKIE -> Cookies.encodeSetCookieHeader(Seq(cookie))
      )
    )
  }

  private def cookieDataFromRequest(queryParams: Map[String, String]): Option[(Cookies, String)] = {
    queryParams.get("c").zip(queryParams.get("r")).headOption.map { case (cookies, finalLocation) =>
      (Cookies.fromSetCookieHeader(Some(cookies)), finalLocation)
    }
  }

  /** Create the target URL to set the next cookie
    *
    * @param requestHeaders The original request headers - used to extract the host and protocol
    * @param cookies The sequence of cookies still to set
    * @param redirectLocation The final location to redirect to once all the cookies are set
    * @return
    */
  private def cookieRedirectLocation(requestHeaders: Map[String, String], cookies: Seq[Cookie], redirectLocation: String): String = {
    // protocol should always be HTTPS as that's all that API gateway supports
    val protocol = requestHeaders.get("CloudFront-Forwarded-Proto").orElse(requestHeaders.get("X-Forwarded-Proto")).getOrElse("https")
    val maybeHost = requestHeaders.get("Host")
    val cookieParamValue = encode(Cookies.encodeSetCookieHeader(cookies))
    val locationParamValue = encode(redirectLocation)
    val pathAndParams = s"${LambdaEntrypoint.COOKIE_ENDPOINT}?c=$cookieParamValue&r=$locationParamValue"
    maybeHost.map(host => s"$protocol://$host$pathAndParams").getOrElse(pathAndParams)
  }

  /** URL encode with %20 for spaces */
  private def encode(input: String): String = URLEncoder.encode(input, "UTF-8").replace("+", "%20")

  /** Turn a stack trace into a string */
  private def stackTraceString(t: Throwable): String = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}
