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

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class PushMessageSpec extends UnitSpec {
  "PushMessage" should {
    "render the question in Json given a message with a question" in {
      val message = PushMessage(
        subject = "You need to authorise your agent",
        body = "You are required to authorise your agent, Dodger & Pretend Accountants.",
        callbackUrl = "https://some/end/point",
        responses = Map("Yes, fine" -> "Yes", "No, thanks" -> "No"),
        messageId = "msg-1234")

      val result = Json.toJson(message)

      result shouldBe Json.parse(
        """{
          |"id" : "msg-1234",
          |"subject" : "You need to authorise your agent",
          |"body" : "You are required to authorise your agent, Dodger & Pretend Accountants.",
          |"responses" : {
          |   "Yes, fine" : "Yes",
          |   "No, thanks" : "No"
          |   }
          |}
        """.stripMargin)
    }

    "not render a question in Json given a message without a question" in {
      val message = PushMessage(
        subject = "Your agent has been authorised",
        body = "Your agent, Dodger & Pretend Accountants, is now authorised to act on your behalf.",
        callbackUrl = "https://some/end/point",
        messageId = "msg-1235")

      val result = Json.toJson(message)

      result shouldBe Json.parse(
        """{
          |"id" : "msg-1235",
          |"subject" : "Your agent has been authorised",
          |"body" : "Your agent, Dodger & Pretend Accountants, is now authorised to act on your behalf."
          |}
        """.stripMargin)
    }
  }
}
