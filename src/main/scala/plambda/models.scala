package plambda

import play.api.libs.json.Json

object LambdaRequest {
  implicit val reads = Json.reads[LambdaRequest]
}

case class LambdaRequest(
  httpMethod: String,
  path: String,
  queryStringParameters: Option[Map[String, String]],
  headers: Option[Map[String, String]],
  body: Option[String]
)

object LambdaResponse {
  implicit val writes = Json.writes[LambdaResponse]
}

case class LambdaResponse(
  statusCode: Int,
  headers: Map[String, String] = Map.empty,
  body: String = ""
)
