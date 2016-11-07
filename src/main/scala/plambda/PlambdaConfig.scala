package plambda

import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model.GetFunctionConfigurationRequest
import com.amazonaws.services.lambda.runtime.Context
import play.api.libs.json.Json

import scala.util.control.NonFatal

object PlambdaConfig {
  def getConfig(context: Context)(implicit lambdaClient: AWSLambdaClient): PlambdaConfig = {
    try{
      val functionMetadata = lambdaClient.getFunctionConfiguration(
        new GetFunctionConfigurationRequest()
          .withFunctionName(context.getFunctionName)
      )
      Json.parse(functionMetadata.getDescription).as[PlambdaConfig]
    } catch {
      case NonFatal(t) =>
        context.getLogger.log(s"Exception whilst getting plambda config: \n$t\n${t.getStackTrace.mkString("\n")}")
        throw t
    }
  }

  implicit val reads = Json.reads[PlambdaConfig]
}

case class PlambdaConfig(binaryBucketName: String)
