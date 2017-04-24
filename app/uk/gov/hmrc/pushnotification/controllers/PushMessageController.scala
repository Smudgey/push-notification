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

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, BodyParsers}
import uk.gov.hmrc.api.controllers.HeaderValidator
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pushnotification.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.pushnotification.domain.Template
import uk.gov.hmrc.pushnotification.services.PushMessageServiceApi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PushMessageController])
trait PushMessageControllerApi extends BaseController with HeaderValidator with ErrorHandling  {
  def sendTemplateMessage(journeyId: Option[String] = None): Action[JsValue]
}

@Singleton
class PushMessageController @Inject()(service: PushMessageServiceApi, accessControl: AccountAccessControlWithHeaderCheck) extends PushMessageControllerApi {
  override implicit val ec: ExecutionContext = ExecutionContext.global

  def sendTemplateMessage(journeyId: Option[String] = None): Action[JsValue] = accessControl.validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)

      authenticated.request.body.validate[Template].fold(
        errors => {
          Logger.warn("Service failed for sendTemplateMessage: " + errors)
          Future.successful(BadRequest)
        },
        template => {
          errorWrapper(service.sendTemplateMessage(template)(hc, authenticated.authority).map {
            id: String => Created(Json.obj("messageId" -> id))
          })
        }
      )
  }
}