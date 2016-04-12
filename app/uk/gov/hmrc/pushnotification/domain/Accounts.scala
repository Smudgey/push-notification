/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.domain.{Nino, SaUtr}

case class Accounts(nino: Option[Nino], saUtr: Option[SaUtr])

object Accounts {
  implicit val accountsFmt = {
    import Nino.{ninoRead, ninoWrite}
    import SaUtr.{saUtrRead, saUtrWrite}

    Json.format[Accounts]
  }
}
