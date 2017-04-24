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

import play.api.libs.json.{JsObject, Json, Writes}

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