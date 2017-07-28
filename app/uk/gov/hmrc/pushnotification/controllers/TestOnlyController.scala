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

import play.api.Logger
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{Action, BodyParsers}
import uk.gov.hmrc.api.controllers.HeaderValidator
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationMongoRepositoryTest}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pushnotification.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.pushnotification.domain.{PushMessage, PushMessageStatus}
import uk.gov.hmrc.pushnotification.repository._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyController @Inject()(notificationRepository: PushNotificationMongoRepositoryTest,
                                   pushMessageMongoRepository: PushMessageMongoRepositoryTest,
                                   callbackMongoRepository: CallbackMongoRepositoryTest,
                                   accessControl: AccountAccessControlWithHeaderCheck) extends BaseController with ErrorHandling with HeaderValidator {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
  implicit val format = Json.format[NotificationPersist]

  def dropNotificationMongo() = Action.async {
    notificationRepository.removeAllRecords().map(_ => Ok)
  }

  def findByInternalId(internalId: String) = Action.async {
    notificationRepository.findByInternalId(internalId).map(res => Ok(Json.toJson(res)))
  }

  def findByEndpoint(token: String, internalId: String) = Action.async {
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    implicit val format = Json.format[NotificationPersist]

    notificationRepository.findByEndpoint(s"default-platform-arn/stubbed/default-platform-arn/$token", internalId).map(res => Ok(Json.toJson(res)))
  }

  def dropMessageMongo() = Action.async {
    for {
      _ <- pushMessageMongoRepository.removeAllRecords()
      result <- callbackMongoRepository.removeAllRecords().map(_ => Ok)
    } yield {
      result
    }
  }

  implicit val expectedMessageResponse: Reads[ExpectedMessageResponse] = Json.reads[ExpectedMessageResponse]

  def persist() = accessControl.validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)

      def getAuthId = authenticated.authority.fold(throw new Exception("no auth!")) { auth => auth.authInternalId }

      authenticated.request.body.validate[ExpectedMessageResponse].fold(
        errors => {
          Logger.warn("Service failed for persist: " + errors)
          Future.successful(BadRequest)
        },
        expectedResponse => {
          val pushMessage = PushMessage(expectedResponse.subject, expectedResponse.body, "", expectedResponse.responses, expectedResponse.id)
          errorWrapper {
            for {
              _ <- pushMessageMongoRepository.save(getAuthId, pushMessage)
              response <- callbackMongoRepository.save(pushMessage.messageId, pushMessage.callbackUrl, PushMessageStatus.Acknowledge, None).map(_ => Ok)
            } yield response
          }
        }
      )
  }
}

object ExpectedMessageResponse {
  val messageReads: Reads[ExpectedMessageResponse] = Json.reads[ExpectedMessageResponse]
}

case class ExpectedMessageResponse(id: String, subject: String, body: String, responses: Map[String, String])
