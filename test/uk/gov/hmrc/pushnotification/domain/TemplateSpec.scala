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

import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.services.GUIDUtil

class TemplateSpec extends UnitSpec {

  "Template complete" should {

    "return a completed template with no message given a valid template name" in {
      val result = Template("NGC_001").complete()

      result.notification shouldBe "This is a push notification that does nothing else other than show you this text."
      result.message shouldBe None
    }

    "return a completed template populated with params with a message" in new GUIDUtil {
      val name = "Peter Parker"
      val device = "Nexus 5X"
      val time = "17:21"
      val location = "Manchester, UK"
      val callbackUrl = "http://callback.url"
      val result: NotificationMessage = Template("NGC_003", Map("name" -> name, "device" -> device, "time" -> time, "location" -> location, "callbackUrl" -> callbackUrl)).complete()

      result.notification.contains(name) shouldBe true
      result.message.isDefined shouldBe true
      result.message.get.subject shouldBe "Are you trying to sign in?"
      result.message.get.body.contains(device) shouldBe true
      result.message.get.body.contains(time) shouldBe true
      result.message.get.body.contains(location) shouldBe true
      result.message.get.callbackUrl shouldBe callbackUrl
      result.message.get.messageId should BeGuid
    }

    "ignore a messageId parameter passed by the client" in {
      val messageId = "foo"

      val result = Template("NGC_002", Map("name" -> "foo", "device" -> "bar", "time" -> "1:00PM", "location" -> "quux", "callbackUrl" -> "/baz", "messageId" -> messageId)).complete()

      result.message.get.messageId should not be messageId
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
