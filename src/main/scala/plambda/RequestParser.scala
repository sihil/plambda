package plambda

import java.io.InputStream
import com.amazonaws.services.lambda.runtime.LambdaLogger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeHeaders
import play.api.test.FakeRequest

object RequestParser {
  def buildPath(uri: String, pathParams: Map[String, String]): String = {
    pathParams.foldLeft(uri) { case (acc, (key, value)) => acc.replace(s"{$key}", value) }
  }

  // TODO: this should really return an either with better error reporting
  def fromStream(stream: InputStream, lambdaRequestHandler: LambdaRequestHandler)(
      implicit logger: LambdaLogger
  ): Option[LambdaRequest] = {
    val json: JsValue = Json.parse(stream)
    logger.log(s"Got JSON: $json")
    val request = Json.fromJson[HttpRequest](json)
    logger.log(s"Parsed request: $request")
    request.asOpt match {
      case None => {
        logger.log(s"Non HTTP Request... Attempting Kinesis parsing...")
        val parsedKinesis = Json.fromJson[KinesisRequest](json).asOpt
        logger.log(s"Parsed kinesis request: ${parsedKinesis}")
        parsedKinesis
      }
      case Some(request) => Some(request)
    }
  }

  def transform(request: HttpRequest): FakeRequest[AnyContent] = {
    val queryString = for {
      queryMap <- request.queryStringParameters.toList
      (name, value) <- queryMap
    } yield s"$name=$value"
    val pathWithQueryString =
      if (queryString.isEmpty) request.path
      else queryString.mkString(s"${request.path}?", "&", "")
    val body: AnyContent = request.body.map(AnyContentAsText).getOrElse(AnyContentAsEmpty)
    FakeRequest(
      method = request.httpMethod,
      uri = pathWithQueryString,
      headers = FakeHeaders(request.headers.map(_.toList).getOrElse(Nil)),
      body = body
    )
  }
}
