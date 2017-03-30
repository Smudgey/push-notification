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
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, ServiceUnavailableException, UnauthorizedException}
import uk.gov.hmrc.pushnotification.config.MicroserviceAuditConnector
import uk.gov.hmrc.pushnotification.connector.{Authority, PushRegistrationConnector}
import uk.gov.hmrc.pushnotification.domain.{Notification, Template}
import uk.gov.hmrc.pushnotification.repository.PushNotificationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[MobileMessagesService])
trait MobileMessagesServiceApi extends Auditor {
  override val auditConnector = MicroserviceAuditConnector

  def sendTemplateMessage(template: Template)(implicit hc: HeaderCarrier, authority:Option[Authority]): Future[Seq[String]]
}

@Singleton
class MobileMessagesService @Inject() (connector: PushRegistrationConnector, repository: PushNotificationRepository) extends MobileMessagesServiceApi {

  override def sendTemplateMessage(template: Template)(implicit hc: HeaderCarrier, authority: Option[Authority]): Future[Seq[String]] = {
    withAudit("sendTemplateMessage", Map.empty) {
      val auth = authority.getOrElse {
        return Future.failed(new UnauthorizedException("No Authority record found for request!"))
      }

      val message = template.complete().getOrElse {
        Logger.error(s"no such template '${template.name}'")
        return Future.failed(new BadRequestException(s"No such template '${template.name}'"))
      }

      for (
        endpoints <- connector.endpointsForAuthId(auth.authInternalId);
        messageIds <- createNotifications(auth.authInternalId, endpoints, message)
      ) yield messageIds
    }
  }

  private def createNotifications(authId: String, endpoints: Seq[String], message: String): Future[Seq[String]] = {
    Future.sequence(endpoints.map{ endpoint =>
      val notification = Notification(endpoint, message)
      repository.save(authId, notification).map {
        case Right(n) => n.messageId
        case Left(e) => throw new ServiceUnavailableException(e)
      }
    })
  }
}