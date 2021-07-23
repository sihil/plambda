package plambda

import play.api.libs.json.Json
import play.api.libs.json.OFormat
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

// (todo): emma For PoC never reports failures
//info: https://docs.aws.amazon.com/lambda/latest/dg/with-kinesis.html 'Success and Failure conditions'
case class KinesisResponse(batchItemFailures: Array[Int] = Array.empty[Int]) extends LambdaResponse

object HttpRequest {
  implicit val formats: OFormat[HttpRequest] = Json.format[HttpRequest]
}

object HttpResponse {
  implicit val formats: OFormat[HttpResponse] = Json.format[HttpResponse]
}

object KinesisInfo {
  implicit val formats: OFormat[KinesisInfo] = Json.format[KinesisInfo]
}

object KinesisRecords {
  implicit val formats: OFormat[KinesisRecords] = Json.format[KinesisRecords]
}

object KinesisRequest {
  implicit val formats: OFormat[KinesisRequest] = Json.format[KinesisRequest]
}

object KinesisResponse {
  implicit val formats: OFormat[KinesisResponse] = Json.format[KinesisResponse]
}
