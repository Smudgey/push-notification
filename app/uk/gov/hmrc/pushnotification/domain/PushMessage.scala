/*
 * Copyright 2017 HM Revenue & Customs
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

trait PushMessageStatus

object PushMessageStatus {
  val statuses = List("acknowledge", "acknowledged", "answer", "answered", "timeout", "timed-out", "failed")

  case object Acknowledge extends PushMessageStatus {
    override def toString: String = statuses.head
  }

  case object Acknowledged extends PushMessageStatus {
    override def toString: String = statuses(1)
  }

  case object Answer extends PushMessageStatus {
    override def toString: String = statuses(2)
  }

  case object Answered extends PushMessageStatus {
    override def toString: String = statuses(3)
  }

  case object Timeout extends PushMessageStatus {
    override def toString: String = statuses(4)
  }

  case object Timedout extends PushMessageStatus {
    override def toString: String = statuses(5)
  }

  case object PermanentlyFailed extends PushMessageStatus {
    override def toString: String = statuses(6)
  }

  def ordinal(status: PushMessageStatus): Int = statuses.indexOf(status.toString)

  val readsFromRepository: Reads[PushMessageStatus] = new Reads[PushMessageStatus] {
    override def reads(json: JsValue): JsResult[PushMessageStatus] =
      json match {
        case JsNumber(value: BigDecimal) if value == ordinal(Acknowledge) => JsSuccess(Acknowledge)
        case JsNumber(value: BigDecimal) if value == ordinal(Acknowledged) => JsSuccess(Acknowledged)
        case JsNumber(value: BigDecimal) if value == ordinal(Answer) => JsSuccess(Answer)
        case JsNumber(value: BigDecimal) if value == ordinal(Answered) => JsSuccess(Answered)
        case JsNumber(value: BigDecimal) if value == ordinal(Timeout) => JsSuccess(Timeout)
        case JsNumber(value: BigDecimal) if value == ordinal(Timedout) => JsSuccess(Timedout)
        case JsNumber(value: BigDecimal) if value == ordinal(PermanentlyFailed) => JsSuccess(PermanentlyFailed)
        case _ => JsError(s"Failed to resolve $json")
      }
  }

  val writes: Writes[PushMessageStatus] = new Writes[PushMessageStatus] {
    override def writes(status: PushMessageStatus): JsString = JsString(status.toString)
  }

  val reads: Reads[PushMessageStatus] = new Reads[PushMessageStatus] {
    override def reads(json: JsValue): JsResult[PushMessageStatus] =
      json match {
        case JsString(value: String) if value == Acknowledge.toString => JsSuccess(Acknowledge)
        case JsString(value: String) if value == Acknowledged.toString => JsSuccess(Acknowledged)
        case JsString(value: String) if value == Answer.toString => JsSuccess(Answer)
        case JsString(value: String) if value == Answered.toString => JsSuccess(Answered)
        case JsString(value: String) if value == Timeout.toString => JsSuccess(Timeout)
        case JsString(value: String) if value == Timedout.toString => JsSuccess(Timedout)
        case JsString(value: String) if value == PermanentlyFailed.toString => JsSuccess(PermanentlyFailed)
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