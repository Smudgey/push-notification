/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushnotification.domain

import java.util.UUID

import play.api.libs.json._
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.Queued

trait NotificationStatus

object NotificationStatus {
  val queued = "queued"
  val sent = "sent"
  val delivered = "delivered"
  val disabled = "disabled"
  val failed = "permanently-failed"

  case object Queued extends NotificationStatus {
    override def toString: String = queued
  }

  case object Sent extends NotificationStatus {
    override def toString: String = sent
  }

  case object Delivered extends NotificationStatus {
    override def toString: String = delivered
  }

  case object Disabled extends NotificationStatus {
    override def toString: String = disabled
  }

  case object PermanentlyFailed extends NotificationStatus {
    override def toString: String = failed
  }

  val reads: Reads[NotificationStatus] = new Reads[NotificationStatus] {
    override def reads(json: JsValue): JsResult[NotificationStatus] = json match {
      case JsString(NotificationStatus.queued) => JsSuccess(Queued)
      case JsString(NotificationStatus.sent) => JsSuccess(Sent)
      case JsString(NotificationStatus.delivered) => JsSuccess(Delivered)
      case JsString(NotificationStatus.disabled) => JsSuccess(Disabled)
      case JsString(NotificationStatus.failed) => JsSuccess(PermanentlyFailed)
      case _ => JsError(s"Failed to resolve $json")
    }
  }

  val writes: Writes[NotificationStatus] = new Writes[NotificationStatus] {
    override def writes(status: NotificationStatus): JsString = status match {
      case Queued => JsString(queued)
      case Sent => JsString(sent)
      case Delivered => JsString(delivered)
      case Disabled => JsString(disabled)
      case PermanentlyFailed => JsString(failed)
    }
  }

  implicit val formats = Format(NotificationStatus.reads, NotificationStatus.writes)
}

case class Notification(notificationId: Option[String] = Some(UUID.randomUUID().toString), status: NotificationStatus = Queued,
                        endpoint: String, content: String, messageId: Option[String], os: String)

object Notification {
  implicit val writes = new Writes[Notification] {
    def writes(notification: Notification): JsObject = Json.obj(
      "id" -> notification.notificationId,
      "endpointArn" -> notification.endpoint,
      "message" -> notification.content,
      "messageId" -> notification.messageId,
      "os" -> notification.os
    )
  }
}

case class NotificationResult(notificationId: String, status: NotificationStatus)
