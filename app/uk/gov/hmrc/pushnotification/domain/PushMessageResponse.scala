package uk.gov.hmrc.pushnotification.domain

import play.api.libs.json.Json

case class PushMessageResponse(messages: Seq[PushMessage])

object PushMessageResponse {
  implicit val formatPushMessage = Json.format[PushMessage]
  implicit val format = Json.format[PushMessageResponse]
}