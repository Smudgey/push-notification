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
import play.api.mvc._
import uk.gov.hmrc.api.controllers.HeaderValidator
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pushnotification.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Answer}
import uk.gov.hmrc.pushnotification.domain._
import uk.gov.hmrc.pushnotification.services.PushMessageServiceApi

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PushMessageController])
trait PushMessageControllerApi extends BaseController with HeaderValidator with ErrorHandling {
  def sendTemplateMessage(journeyId: Option[String] = None): Action[JsValue]

  def respondToMessage(id: String, journeyId: Option[String] = None): Action[JsValue]

  def getMessageFromMessageId(messageId:String, journeyId: Option[String]): Action[AnyContent]

  def testOnlyGetMessageStatus(messageId:String): Action[AnyContent]
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
            case Some(id) => Created(Json.obj("messageId" -> id))
            case None => Created
          })
        }
      )
  }

  override def respondToMessage(id: String, journeyId: Option[String]): Action[JsValue] = Action.async(BodyParsers.parse.json) {
    implicit request =>
      request.body.validate[Response].fold(
        errors => {
          Logger.warn("Service failed for respondToMessage: " + errors)
          Future.successful(BadRequest)
        },
        response => {
          if (id == response.messageId) {
            val status = response.answer.map(_ => Answer).getOrElse(Acknowledge)
            errorWrapper(service.respondToMessage(response.messageId, status, response.answer).map { result =>
              if (result) {
                Ok(Json.obj())
              } else {
                Logger.info(s"Response for messageId=[${ response.messageId }] with status=[$status], answer=[${ response.answer.getOrElse("") }] was previously processed")
                Accepted(Json.obj())
              }
            })
          } else {
            Logger.warn("Message id in path does not match message id in payload")
            Future.successful(BadRequest)
          }
        }
      )
  }

  override def getMessageFromMessageId(messageId:String, journeyId: Option[String]): Action[AnyContent] =
    accessControl.validateAccept(acceptHeaderValidationRules).async {
      implicit request =>
        errorWrapper {
          def getAuthId = request.authority.fold(throw new Exception("no auth!")){auth => auth.authInternalId}
          service.getMessageFromMessageId(messageId, getAuthId).map {
            _.fold(NotFound("Message Id unknown!")) { found => Ok(Json.toJson(found)) }
          }
        }
    }

  override def testOnlyGetMessageStatus(messageId:String): Action[AnyContent] =
    accessControl.validateAccept(acceptHeaderValidationRules).async {
      implicit request =>
        errorWrapper {
          service.getMessageStatus(messageId).map {
            _.fold(NotFound(s"No callback found for message ID [$messageId]")) { found => Ok(Json.toJson(found)) }
          }
        }
    }
}