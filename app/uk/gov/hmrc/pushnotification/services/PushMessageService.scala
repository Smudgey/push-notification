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

package uk.gov.hmrc.pushnotification.services

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import play.api.Configuration
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, ServiceUnavailableException, UnauthorizedException}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.pushnotification.connector.{Authority, PushRegistrationConnector}
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Answer}
import uk.gov.hmrc.pushnotification.domain.{Notification, PushMessage, PushMessageStatus, Template}
import uk.gov.hmrc.pushnotification.repository._

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[PushMessageService])
trait PushMessageServiceApi extends Auditor {
  override val auditConnector: AuditConnector

  def sendTemplateMessage(template: Template)(implicit hc: HeaderCarrier, authority: Option[Authority]): Future[Option[String]]

  def respondToMessage(messageId: String, status: PushMessageStatus, answer: Option[String]): Future[(Boolean, Option[PushMessage])]

  def getCurrentMessages(authId: String): Future[Seq[PushMessage]]

  def getMessageFromMessageId(messageId: String, authId: String): Future[Option[PushMessage]]
}

@Singleton
class PushMessageService @Inject()(connector: PushRegistrationConnector,
                                   notificationRepository: PushNotificationRepositoryApi,
                                   messageRepository: PushMessageRepositoryApi,
                                   val appNameConfiguration: Configuration,
                                   val auditConnector: AuditConnector,
                                   callbackRepository: CallbackRepositoryApi) extends PushMessageServiceApi {

  override def sendTemplateMessage(template: Template)(implicit hc: HeaderCarrier, authority: Option[Authority]): Future[Option[String]] = {
    withAudit("sendTemplateMessage", Map.empty) {
      val auth = authority.getOrElse {
        return Future.failed(new UnauthorizedException("No Authority record found for request!"))
      }

      val message = template.complete()
      for (
        messageId <- persistPushMessage(message.message, auth.authInternalId);
        endpointsWithOs <- connector.endpointsForAuthId(auth.authInternalId);
        _ <- createNotifications(auth.authInternalId, endpointsWithOs, message.notification, messageId)
      ) yield messageId
    }
  }

  override def getMessageFromMessageId(messageId: String, authId: String): Future[Option[PushMessage]] = {

    def findCallback(message: Option[PushMessage]): Future[Option[PushMessage]] = {
      val notFound: Option[PushMessage] = None
      message.fold(Future.successful(notFound)) { found =>
        callbackRepository.findLatest(List(messageId)).map {
          case (callback :: _) if Seq(Acknowledge, Answer).contains(callback.status) => message
          case _ => None
        }
      }
    }

    for (
      message <- messageRepository.find(messageId, Some(authId)).map(_.map(pm =>
        PushMessage(pm.subject, pm.body, pm.callbackUrl, pm.responses, pm.messageId)));
      result <- findCallback(message)
    ) yield result
  }

  override def respondToMessage(messageId: String, status: PushMessageStatus, answer: Option[String]): Future[(Boolean, Option[PushMessage])] = {
    for (
      message: Option[PushMessage] <- messageRepository.find(messageId, None).map(_.map(pm =>
        PushMessage(pm.subject, pm.body, pm.callbackUrl, pm.responses, pm.messageId)));
      saved <- message.map { msg =>
        if (!answer.forall { a => msg.responses.count(_._1 == a) > 0 }) {
          throw new BadRequestException(s"invalid answer [${answer.getOrElse("")}]")
        }
        callbackRepository.save(messageId, msg.callbackUrl, status, answer).map {
          case Right(result) => result
          case Left(error) => throw new ServiceUnavailableException(error)
        }
      }.getOrElse(Future(false))
    ) yield (saved, message)
  }

  // todo: check if sequence is required?
  private def createNotifications(authId: String, endpoints: Map[String, String], message: String, messageId: Option[String]): Future[Seq[String]] = {
    Future.sequence(endpoints.map { endpointAndOs =>
      val notification = Notification(endpoint = endpointAndOs._1, content = message, messageId = messageId, os = endpointAndOs._2)
      notificationRepository.save(authId, notification).map {
        case Right(n) => n.notificationId
        case Left(e) => throw new ServiceUnavailableException(e)
      }
    } toSeq)
  }

  private def persistPushMessage(pushMessage: Option[PushMessage], authId: String): Future[Option[String]] = {
    pushMessage.fold(Future[Option[String]](None)) { pm =>
      messageRepository.save(authId, pm).map {
        case Right(persist) => Option(persist.messageId)
        case Left(error) => throw new Exception(error)
      }
    }
  }

  override def getCurrentMessages(authId: String): Future[Seq[PushMessage]] = {
    getMessagesByAuthority(authId)
  }

  private def getMessagesByAuthority(authId: String): Future[Seq[PushMessage]] = {
    for {
      messages <- messageRepository.findByAuthority(authId)
      callbackMessagesPairs: immutable.Seq[(PushMessageCallbackPersist, PushMessagePersist)] <- callbackRepository.findLatest(messages.map(_.messageId).toList).map(_.zip(messages))
    } yield
      callbackMessagesPairs.flatMap {
        case (PushMessageCallbackPersist(_, _, _, status, _, _, _), message) if Seq(Acknowledge, Answer).contains(status) =>
          Some(PushMessage(message.subject, message.body, message.callbackUrl, message.responses, message.messageId))
        case _ => None
      }
  }
}
