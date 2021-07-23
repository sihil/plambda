package plambda

import com.amazonaws.services.lambda.runtime.Context

import scala.util.control.NonFatal

object PlambdaConfig {
  def getConfig(context: Context): PlambdaConfig = {
    try {
      PlambdaConfig(
        binaryBucketName = "not used"
      )
    } catch {
      case NonFatal(t) =>
        context.getLogger.log(
          s"Exception whilst getting plambda config: \n$t\n${t.getStackTrace.mkString("\n")}")
        throw t
    }
  }
}

case class PlambdaConfig(binaryBucketName: String)
