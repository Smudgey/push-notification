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
  val statuses = List("acknowledge", "acknowledged", "answer", "answered", "timeout")

  val acknowledge: String = statuses.head
  val acknowledged: String = statuses(1)
  val answer: String = statuses(2)
  val answered: String = statuses(3)
  val timeout: String = statuses(4)

  case object Acknowledge extends PushMessageStatus {
    override def toString: String = acknowledge
  }

  case object Acknowledged extends PushMessageStatus {
    override def toString: String = acknowledged
  }

  case object Answer extends PushMessageStatus {
    override def toString: String = answer
  }

  case object Answered extends PushMessageStatus {
    override def toString: String = answered
  }

  case object Timeout extends PushMessageStatus {
    override def toString: String = timeout
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
        case _ => JsError(s"Failed to resolve $json")
      }
  }

  val reads: Reads[PushMessageStatus] = new Reads[PushMessageStatus] {
    override def reads(json: JsValue): JsResult[PushMessageStatus] = json match {
      case JsString(PushMessageStatus.acknowledge) => JsSuccess(Acknowledge)
      case JsString(PushMessageStatus.acknowledged) => JsSuccess(Acknowledged)
      case JsString(PushMessageStatus.answer) => JsSuccess(Answer)
      case JsString(PushMessageStatus.answered) => JsSuccess(Answered)
      case JsString(PushMessageStatus.timeout) => JsSuccess(Timeout)
      case _ => JsError(s"Failed to resolve $json")
    }
  }

  val writes: Writes[PushMessageStatus] = new Writes[PushMessageStatus] {
    override def writes(status: PushMessageStatus): JsString = status match {
      case Acknowledge => JsString(acknowledge)
      case Acknowledged => JsString(acknowledged)
      case Answer => JsString(answer)
      case Answered => JsString(answered)
      case Timeout => JsString(timeout)
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