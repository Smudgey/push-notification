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

import play.api.libs.json.{JsSuccess, Json}
import twirl.TwirlException
import uk.gov.hmrc.play.http.{BadRequestException, InternalServerException}

case class Template(id: String, params: Map[String, String] = Map.empty) {

  def complete(): NotificationMessage = {
    val template = twirl.txt.templates.render(id, UUID.randomUUID().toString,getParamById("title"), getParamById("firstName"),
                                                  getParamById("lastName"), getParamById("fullName"),
                                                  getParamById("agent"), getParamById("callbackUrl"))
    val result = Json.parse(template.toString())
    result.validate[NotificationMessage] match {
      case nm: JsSuccess[NotificationMessage] => nm.get
      case _ => {
        result.validate[TwirlException] match {
          case twirlError: JsSuccess[TwirlException] => {
            throw new BadRequestException(twirlError.get.exception)
          }
          case _ => throw new InternalServerException("Unable to parse template Json, there is a problem with the template")
        }
      }
    }
  }

  private def getParamById(key: String): String = {
    params.getOrElse(key, "")
  }

}

object Template {
  implicit val formats = Json.format[Template]
}
