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

import uk.gov.hmrc.play.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec

class TemplateSpec extends UnitSpec {

  "Template complete" should {

    "return a completed template with no message given a valid template name" in {
      val result = Template("NGC_001").complete()

      result.notification shouldBe "This is a push notification that does nothing else other than show you this text."
      result.message shouldBe None
    }

    "return a completed template populated with params with a message" in {
      val title = "Mr"
      val firstName = "Peter"
      val lastName = "Parker"
      val agent = "Agent 47"
      val callbackUrl = "http://callback.url"
      val messageId = UUID.randomUUID().toString
      val result = Template("NGC_003", Map("title" -> title, "firstName" -> firstName, "lastName" -> lastName, "agent" -> agent, "callbackUrl" -> callbackUrl, "messageId" -> messageId)).complete()

      result.notification.contains(title) shouldBe true
      result.notification.contains(firstName) shouldBe true
      result.notification.contains(lastName) shouldBe true
      result.message.isDefined shouldBe true
      result.message.get.subject shouldBe "You need to authorise your agent"
      result.message.get.body.contains(agent) shouldBe true
      result.message.get.callbackUrl shouldBe callbackUrl
      result.message.get.messageId shouldBe messageId
    }

    "throw a BadRequestException given a template without required parameters" in {
      val templateId = "NGC_002"
      intercept[BadRequestException] {
        Template(templateId).complete()
      }.message.contains(s"Missing parameter for template $templateId") shouldBe true
    }

    "throw a BadRequestException given an unsupported template id" in {
      val templateId = "NGC_invalid_template_id"
      intercept[BadRequestException] {
        Template(templateId).complete()
      }.message shouldBe s"Template $templateId not found"
    }
  }
}

