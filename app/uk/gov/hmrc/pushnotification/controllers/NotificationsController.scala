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
import play.api.mvc.{Action, AnyContent, BodyParsers, Result}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pushnotification.domain.NotificationStatus
import uk.gov.hmrc.pushnotification.services.NotificationsServiceApi

import scala.concurrent.{ExecutionContext, Future}


@ImplementedBy(classOf[NotificationsController])
trait NotificationsControllerApi extends BaseController with ErrorHandling {
  val NoUnsentNotifications: JsValue = Json.parse("""{"code":"NOT_FOUND","message":"No unsent notifications"}""")

  def getUnsentNotifications : Action[AnyContent]

  def updateNotifications : Action[JsValue]
}

@Singleton
class NotificationsController @Inject()(service: NotificationsServiceApi) extends NotificationsControllerApi {
  override implicit val ec: ExecutionContext = ExecutionContext.global

  override def getUnsentNotifications: Action[AnyContent] = Action.async {
    implicit request =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)

    errorWrapper(service.getUnsentNotifications.map { response =>
      if (response.isEmpty) {
        NotFound(NoUnsentNotifications)
      }
      else
        Ok(Json.toJson(response))
    })
  }

  override def updateNotifications: Action[JsValue] = Action.async(BodyParsers.parse.json) {
    implicit request =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)

      request.body.validate[Map[String, NotificationStatus]].fold(
        errors => {
          Logger.warn("Service failed for updateNotifications: " + errors)
          Future.successful(BadRequest)
        },
        updates => {
          errorWrapper(service.updateNotifications(updates).map { s =>
            if (s.foldLeft(true)(_ && _)) {
              NoContent
            } else {
              Accepted
            }
          })
        })
  }
}