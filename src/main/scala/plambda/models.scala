package plambda

import play.api.libs.json.Json
sealed trait LambdaResponse

sealed trait LambdaRequest

case class HttpRequest(
    httpMethod: String,
    path: String,
    queryStringParameters: Option[Map[String, String]],
    headers: Option[Map[String, String]],
    body: Option[String]
) extends LambdaRequest

case class HttpResponse(
    statusCode: Int,
    headers: Map[String, String] = Map.empty,
    body: String = ""
) extends LambdaResponse

case class KinesisInfo(
    partitionKey: String,
    kinesisSchemaVersion: String,
    data: String,
    sequenceNumber: String,
    approximateArrivalTimestamp: Int
)

case class KinesisRecords(
    kinesis: KinesisInfo,
    eventSource: String,
    eventID: String,
    invokeIdentityArn: String,
    eventVersion: String,
    eventName: String,
    eventSourceARN: String,
    awsRegion: String
)

case class KinesisRequest(
    Records: Seq[KinesisRecords]
) extends LambdaRequest

//(todo): emma For PoC never reports failures
//info: https://docs.aws.amazon.com/lambda/latest/dg/with-kinesis.html 'Success and Failure conditions'
case class KinesisResponse(batchItemFailures: Array[Int] = Array.empty[Int]) extends LambdaResponse

object HttpRequest {
  implicit val reads = Json.reads[HttpRequest]
}

object HttpResponse {
  implicit val writes = Json.writes[HttpResponse]
}

object KinesisInfo {
  implicit val reads = Json.reads[KinesisInfo]
  implicit val writes = Json.writes[KinesisInfo]
}

object KinesisRecords {
  implicit val reads = Json.reads[KinesisRecords]
  implicit val writes = Json.writes[KinesisRecords]

}

object KinesisRequest {
  implicit val reads = Json.reads[KinesisRequest]
  implicit val writes = Json.writes[KinesisRequest]
}

object KinesisResponse {
  implicit val writes = Json.writes[KinesisResponse]
}
