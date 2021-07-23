package plambda

import com.amazonaws.services.lambda.runtime.LambdaLogger
import play.api.mvc.Cookie
import play.api.mvc.Cookies
import play.api.mvc.Result

import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder

object HttpUtils {

  def getSingleCookieLogRemainder(cookies: Cookies, logger: LambdaLogger) = cookies.toList match {
    case cookie :: otherCookies =>
      otherCookies.foreach { cookie =>
        logger.log(s"WARNING: Not setting cookie ${cookie.name} due to AWS API Gateway limitation")
      }
      Some(cookie)
    case _ => None
  }

  def headersToSend(result: Result, maybeCookie: Option[Cookie]): Map[String, String] =
    result.header.headers ++
      result.body.contentType.map(HttpConstants.CONTENT_TYPE ->) ++
      result.body.contentLength.map(HttpConstants.CONTENT_LENGTH -> _.toString) ++
      maybeCookie.map(cookie =>
        HttpConstants.SET_COOKIE -> Cookies.encodeSetCookieHeader(Seq(cookie)))

  /** Create a response for setting multiple cookies
    *
    * @param requestHeaders The original incoming request headers
    * @param cookies The list of cookies that need to be set
    * @param finalLocation The final URL to send the user to
    * @return
    */
  def cookieResponse(
      requestHeaders: Map[String, String],
      cookies: Cookies,
      finalLocation: String
  ): HttpResponse = {
    val cookie :: otherCookies = cookies.toList
    val nextLocation: String =
      if (otherCookies.isEmpty) {
        finalLocation
      } else {
        cookieRedirectLocation(requestHeaders, otherCookies, finalLocation)
      }

    HttpResponse(
      statusCode = HttpConstants.SEE_OTHER,
      headers = Map(
        HttpConstants.LOCATION -> nextLocation,
        HttpConstants.SET_COOKIE -> Cookies.encodeSetCookieHeader(Seq(cookie))
      )
    )
  }

  def cookieDataFromRequest(queryParams: Map[String, String]): Option[(Cookies, String)] = {
    queryParams.get("c").zip(queryParams.get("r")).headOption.map {
      case (cookies, finalLocation) =>
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
  def cookieRedirectLocation(
      requestHeaders: Map[String, String],
      cookies: Seq[Cookie],
      redirectLocation: String
  ): String = {
    // protocol should always be HTTPS as that's all that API gateway supports
    val protocol = requestHeaders
      .get("CloudFront-Forwarded-Proto")
      .orElse(requestHeaders.get("X-Forwarded-Proto"))
      .getOrElse("https")
    val maybeHost = requestHeaders.get("Host")
    val cookieParamValue = encode(Cookies.encodeSetCookieHeader(cookies))
    val locationParamValue = encode(redirectLocation)
    val pathAndParams =
      s"${LambdaEntrypoint.COOKIE_ENDPOINT}?c=$cookieParamValue&r=$locationParamValue"
    maybeHost.map(host => s"$protocol://$host$pathAndParams").getOrElse(pathAndParams)

  }

  /** URL encode with %20 for spaces */
  def encode(input: String): String = URLEncoder.encode(input, "UTF-8").replace("+", "%20")

  /** Turn a stack trace into a string */
  def stackTraceString(t: Throwable): String = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}
