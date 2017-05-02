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

package uk.gov.hmrc.pushnotification.services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import uk.gov.hmrc.play.http.ServiceUnavailableException
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotification.repository.PushNotificationRepositoryApi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[NotificationsService])
trait NotificationsServiceApi {

  def getUnsentNotifications: Future[Seq[Notification]]

  def updateNotifications(updates: Map[String, NotificationStatus]): Future[Seq[Boolean]]
}

@Singleton
class NotificationsService @Inject()(repository: PushNotificationRepositoryApi) extends NotificationsServiceApi {

  override def getUnsentNotifications: Future[Seq[Notification]] = {
    repository.getUnsentNotifications.map(
      _.map(np =>
        Notification(notificationId = Some(np.notificationId), status = np.status, endpoint = np.endpoint, content = np.content, messageId = np.messageId, os = np.os))
    ).recover{
      case e: Exception =>
        Logger.error(s"Unable to retrieve unsent notifications: ${e.getMessage}")
        throw new ServiceUnavailableException(s"Unable to retrieve unsent notifications")
    }
  }

  override def updateNotifications(updates: Map[String, NotificationStatus]): Future[Seq[Boolean]] = {
    val updated = updates.map(s => repository.update(s._1, s._2).map {
      case Right(_) => true
      case Left(msg) =>
        Logger.warn(s"unable to update notification [${s._1} -> ${s._2}]: $msg")
        false
    }.recover{
      case e: Exception =>
        Logger.error(s"Unable to update notification [${s._1} -> ${s._2}]: ${e.getMessage}")
        throw new ServiceUnavailableException(s"Unable to update notification [${s._1} -> ${s._2}]")
    }).toSeq

    Future.sequence(updated)
  }
}
