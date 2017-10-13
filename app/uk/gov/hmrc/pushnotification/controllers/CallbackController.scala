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
import play.api.mvc.{Action, AnyContent, BodyParsers}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pushnotification.domain.{CallbackBatch, CallbackResultBatch}
import uk.gov.hmrc.pushnotification.services.CallbackServiceApi

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CallbackController])
trait CallbackControllerApi extends BaseController with ErrorHandling {
  val NoCallbacks: JsValue = Json.parse("""{"code":"NOT_FOUND","message":"No callbacks found"}""")

  val LockFailed: JsValue = Json.parse("""{"code":"CONFLICT","message":"Failed to obtain lock"}""")

  def getUndeliveredCallbacks: Action[AnyContent]

  def updateCallbacks: Action[JsValue]
}

@Singleton
class CallbackController @Inject()(service: CallbackServiceApi) extends CallbackControllerApi {
  override implicit val ec: ExecutionContext = ExecutionContext.global

  override def getUndeliveredCallbacks: Action[AnyContent] = Action.async {
    implicit request =>
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

      errorWrapper(service.getUndeliveredCallbacks.map { (result: Option[CallbackBatch]) =>
        result.map { callbacks =>
          if (callbacks.batch.isEmpty) {
            NotFound(NoCallbacks)
          }
          else {
            Ok(Json.toJson(callbacks))
          }
        }.getOrElse(Conflict(LockFailed))
      }
      )
  }

  override def updateCallbacks: Action[JsValue] = Action.async(BodyParsers.parse.json) {
    implicit request =>
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

      request.body.validate[CallbackResultBatch].fold(
        errors => {
          Logger.warn("Service failed for updateCallbacks: " + errors)
          Future.successful(BadRequest)
        },
        updates => {
          errorWrapper(service.updateCallbacks(updates).map { updates =>
            if (updates) {
              NoContent
            } else {
              Accepted
            }
          })
        })
  }
}
