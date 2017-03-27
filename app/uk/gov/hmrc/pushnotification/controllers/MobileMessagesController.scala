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

package uk.gov.hmrc.pushnotification.controllers

import play.api.libs.json._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pushnotification.controllers.action.{AccountAccessControlCheckAccessOff, AccountAccessControlWithHeaderCheck}
import uk.gov.hmrc.pushnotification.services._

import scala.concurrent.ExecutionContext


trait MobileMessagesController extends BaseController with HeaderValidator with ErrorHandling {
  val service: MobileMessagesService
  val accessControl: AccountAccessControlWithHeaderCheck

  def ping(journeyId: Option[String] = None) = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
      errorWrapper(service.ping().map(as => Ok(Json.toJson(as))))

  }
}

object SandboxMobileMessagesController extends MobileMessagesController {
  override val service = SandboxMobileMessagesService
  override val accessControl = AccountAccessControlCheckAccessOff
  override implicit val ec: ExecutionContext = ExecutionContext.global
}

object LiveMobileMessagesController extends MobileMessagesController {
  override val service = LiveMobileMessagesService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override implicit val ec: ExecutionContext = ExecutionContext.global
}
