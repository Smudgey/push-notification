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

import uk.gov.hmrc.play.test.UnitSpec

class TemplateSpec extends UnitSpec {
  "Template complete" should {
    "return a completed template given a valid template name and a parameter" in {
      val result = Template("hello", "Bob").complete()

      result shouldBe Some("Hello Bob")
    }

    "return a completed template given a valid template name and no parameters" in {
      val result = Template("bye").complete()

      result shouldBe Some("Goodbye!")
    }

    "return a completed template given a valid template name and multiple parameters" in {
      val result = Template("more", "Eat", "pies").complete()

      result shouldBe Some("Eat more pies")
    }

    "return a completed template given too many parameters" in {
      val result = Template("bye", "cruel world").complete()

      result shouldBe Some("Goodbye!")
    }

    "return None given an invalid template name" in {
      val result = Template("missing").complete()

      result shouldBe None
    }
  }
}
