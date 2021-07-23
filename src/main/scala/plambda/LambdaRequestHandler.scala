package plambda

import akka.util.ByteString
import play.api.http.Writeable
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.AnyContentAsText
import play.api.mvc.Cookies
import play.api.mvc.Result
import play.api.test.Helpers

import scala.concurrent.Await
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.{Context => λContext}
import HttpUtils._
import Helpers.defaultAwaitTimeout
import LambdaEntrypoint.materializer

trait LambdaRequestHandler {
  def handleLambdaRequest(request: LambdaRequest, context: λContext)(
      implicit logger: LambdaLogger
  ): LambdaResponse

  //To be called by handleLambdaRequest; can be overriden if needed
  def routeToPlay(lambdaRequest: HttpRequest, context: λContext)(
      implicit logger: LambdaLogger
  ): HttpResponse = {

    val playRequest = RequestParser.transform(lambdaRequest)
    val writeable: Writeable[AnyContent] = new Writeable[AnyContent](
      transform = {
        case AnyContentAsEmpty => ByteString.empty
        case AnyContentAsText(t) => ByteString.apply(t, "UTF-8")
      },
      playRequest.headers.get(HttpConstants.CONTENT_TYPE)
    )
    val maybeResult = Helpers.route(LambdaEntrypoint.application, playRequest)(writeable)
    logger.log(maybeResult.fold(s"Route not found for $lambdaRequest")(_ =>
      s"Successfully routed $lambdaRequest"))

    maybeResult.fold {
      HttpResponse(HttpConstants.NOT_FOUND, body = "Route not found")
    } { futureResult =>
      val bytes = Helpers.contentAsBytes(futureResult)
      val result: Result = Await.result(futureResult, Helpers.defaultAwaitTimeout.duration)
      val cookiesToSet =
        Cookies.fromSetCookieHeader(result.header.headers.get(HttpConstants.SET_COOKIE))

      val tooManyCookies = cookiesToSet.size > 1
      val isBinaryData = HttpConstants.isBinaryType(result.body.contentType)
      val redirectLocation = Helpers.redirectLocation(futureResult)

      (lambdaRequest.headers, isBinaryData, tooManyCookies, redirectLocation) match {
        case (Some(requestHeaders), _, true, Some(location)) =>
          // multiple cookies with a redirect
          cookieResponse(requestHeaders, cookiesToSet, location)

        case (_, true, _, _) =>
          logger.log("Failed to parse none JSON request")
          // binary body - upload and redirect to S3
          HttpResponse(HttpConstants.SEE_OTHER, body = "Failed to parse request body")

        case (_, false, _, _) =>
          // text body - parse as UTF-8 and return
          val body = bytes.decodeString("utf-8")
          val headers = headersToSend(result, getSingleCookieLogRemainder(cookiesToSet, logger))
          HttpResponse(result.header.status, headers, body)
      }
    }
  }
}
