package plambda

import play.api.http.{HeaderNames, Status}

object HttpConstants extends Status with HeaderNames {
  val mimeTypeTextWhitelist = Seq(
    "text/",
    "application/javascript",
    "application/json",
    "image/svg+xml"
  )
  def isBinaryType(maybeContentType: Option[String]): Boolean =
    maybeContentType.exists(contentType => !mimeTypeTextWhitelist.exists(contentType.startsWith))
}
