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

package uk.gov.hmrc.pushnotification.repository

import play.api.libs.json._

trait ProcessingStatus

object ProcessingStatus {
  val queued = "queued"
  val sent = "sent"
  val delivered = "delivered"
  val failed = "failed"

  case object Queued extends ProcessingStatus {
    override def toString: String = queued
  }

  case object Sent extends ProcessingStatus {
    override def toString: String = sent
  }

  case object Delivered extends ProcessingStatus {
    override def toString: String = delivered
  }

  case object Failed extends ProcessingStatus {
    override def toString: String = failed
  }

  val reads: Reads[ProcessingStatus] = new Reads[ProcessingStatus] {
    override def reads(json: JsValue): JsResult[ProcessingStatus] = json match {
      case JsString(ProcessingStatus.queued) => JsSuccess(Queued)
      case JsString(ProcessingStatus.sent) => JsSuccess(Sent)
      case JsString(ProcessingStatus.delivered) => JsSuccess(Delivered)
      case JsString(ProcessingStatus.failed) => JsSuccess(Failed)
      case _ => JsError(s"Failed to resolve $json")
    }
  }

  val writes: Writes[ProcessingStatus] = new Writes[ProcessingStatus] {
    override def writes(status: ProcessingStatus): JsString = status match {
      case Queued => JsString(queued)
      case Sent => JsString(sent)
      case Delivered => JsString(delivered)
      case Failed => JsString(failed)
    }
  }

  implicit val formats = Format(ProcessingStatus.reads, ProcessingStatus.writes)
}
