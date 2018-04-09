/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent, BodyParsers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotification.services.NotificationsServiceApi

import scala.concurrent.{ExecutionContext, Future}


@ImplementedBy(classOf[NotificationsController])
trait NotificationsControllerApi extends BaseController with ErrorHandling {
  val NoNotifications: JsValue = Json.parse("""{"code":"NOT_FOUND","message":"No notifications found"}""")

  val LockFailed: JsValue = Json.parse("""{"code":"SERVICE_UNAVAILABLE","message":"Failed to obtain lock"}""")

  def getQueuedNotifications: Action[AnyContent]

  def getTimedOutNotifications: Action[AnyContent]

  def updateNotifications: Action[JsValue]
}

@Singleton
class NotificationsController @Inject()(service: NotificationsServiceApi) extends NotificationsControllerApi {
  override implicit val ec: ExecutionContext = ExecutionContext.global

  override def getQueuedNotifications: Action[AnyContent] = findNotifications(service.getQueuedNotifications)

  override def getTimedOutNotifications: Action[AnyContent] = findNotifications(service.getTimedOutNotifications)

  override def updateNotifications: Action[JsValue] = Action.async(BodyParsers.parse.json) {
    implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

      request.body.validate[Map[String, NotificationStatus]].fold(
        errors => {
          Logger.warn("Service failed for updateNotifications: " + errors)
          Future.successful(BadRequest)
        },
        updates => {
          errorWrapper(service.updateNotifications(updates).map { updates =>
            if (updates) {
              NoContent
            } else {
              Accepted
            }
          })
        })
  }

  private def findNotifications(f: => Future[Option[Seq[Notification]]]) = Action.async {
    implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

      errorWrapper(f.map { (result: Option[Seq[Notification]]) =>
        result.map { notifications =>
          if (notifications.isEmpty) NotFound(NoNotifications)
          else Ok(Json.toJson(notifications))
        }.getOrElse(ServiceUnavailable(LockFailed))
      })
  }
}