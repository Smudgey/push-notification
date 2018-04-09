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

import scala.math.BigDecimal

sealed trait PushMessageStatus

object PushMessageStatus {
  val statuses = List(Acknowledge, Answer, Timeout, PermanentlyFailed)

  // Notification acknowledged from device
  case object Acknowledge extends PushMessageStatus {
    override def toString: String = "acknowledge"
  }

  // User answer received from device
  case object Answer extends PushMessageStatus {
    override def toString: String = "answer"
  }

  case object Timeout extends PushMessageStatus {
    override def toString: String = "timeout"
  }

  case object PermanentlyFailed extends PushMessageStatus {
    override def toString: String = "failed"
  }

  def ordinal(status: PushMessageStatus): Int = statuses.indexOf(status)

  val readsFromRepository: Reads[PushMessageStatus] = new Reads[PushMessageStatus] {
    override def reads(json: JsValue): JsResult[PushMessageStatus] =
      json match {
        case JsNumber(value: BigDecimal) if statuses.exists(ordinal(_) == value) => JsSuccess(statuses(value.intValue()))
        case _ => JsError(s"Failed to resolve $json")
      }
  }

  val writes: Writes[PushMessageStatus] = new Writes[PushMessageStatus] {
    override def writes(status: PushMessageStatus): JsString = JsString(status.toString)
  }

  val reads: Reads[PushMessageStatus] = new Reads[PushMessageStatus] {
    override def reads(json: JsValue): JsResult[PushMessageStatus] =
      json match {
        case JsString(value: String) if value == Acknowledge.toString ⇒ JsSuccess(Acknowledge)
        case JsString(value: String) if value == Answer.toString ⇒ JsSuccess(Answer)
        case JsString(value: String) if value == Timeout.toString ⇒ JsSuccess(Timeout)
        case JsString(value: String) if value == PermanentlyFailed.toString ⇒ JsSuccess(PermanentlyFailed)
        case _ => JsError(s"Failed to resolve $json")
      }
  }
  implicit val formats = Format(PushMessageStatus.reads, PushMessageStatus.writes)
}

case class PushMessage(subject: String, body: String, callbackUrl: String, responses: Map[String, String] = Map.empty, messageId: String = UUID.randomUUID().toString)

object PushMessage {
  implicit val messageWrites: Writes[PushMessage] = new Writes[PushMessage] {
    def writes(message: PushMessage): JsObject = {
      val core = Json.obj(
        "id" -> message.messageId,
        "subject" -> message.subject,
        "body" -> message.body)
      if (message.responses.isEmpty) {
        core
      } else {
        core ++ Json.obj("responses" -> message.responses)
      }
    }
  }
}
