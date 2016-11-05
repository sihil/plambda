package plambda

import java.io.InputStream

import com.amazonaws.services.lambda.runtime.LambdaLogger
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}

object RequestParser {
  def buildPath(uri: String, pathParams: Map[String, String]): String = {
    pathParams.foldLeft(uri){ case (acc, (key, value)) => acc.replace(s"{$key}", value) }
  }

  // TODO: this should really return an either with better error reporting
  def fromStream(stream: InputStream)(implicit logger: LambdaLogger): Option[LambdaRequest] = {
    val json = Json.parse(stream)
    logger.log(s"Got JSON: $json")
    val request = Json.fromJson[LambdaRequest](json)
    logger.log(s"Parsed request: $request")
    request.asOpt
  }

  def transform(request: LambdaRequest): FakeRequest[AnyContentAsEmpty.type] = {
    val queryString = for {
      queryMap <- request.queryStringParameters.toList
      (name, value) <- queryMap
    } yield s"$name=$value"
    val pathWithQueryString =
      if (queryString.isEmpty) request.path
      else queryString.mkString(s"${request.path}?", "&", "")
    FakeRequest(
      method = request.httpMethod,
      uri = pathWithQueryString,
      headers = FakeHeaders(request.headers.map(_.toList).getOrElse(Nil)),
      body = AnyContentAsEmpty
    )
  }
}
