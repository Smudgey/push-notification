/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.mvc.{Action, BodyParsers}
import uk.gov.hmrc.pushnotification.controllers.action.{AccountAccessControlForSandbox, AccountAccessControlWithHeaderCheck}
import uk.gov.hmrc.pushnotification.services.{pushnotificationService, LivepushnotificationService, SandboxpushnotificationService}
import uk.gov.hmrc.domain.Nino
import play.api.{Logger, mvc}
import uk.gov.hmrc.play.http.{ForbiddenException, HeaderCarrier, NotFoundException, UnauthorizedException}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.json._
import uk.gov.hmrc.api.controllers._

trait ErrorHandling {
  self:BaseController =>

  def errorWrapper(func: => Future[mvc.Result])(implicit hc:HeaderCarrier) = {
    func.recover {
      case ex:NotFoundException => Status(ErrorNotFound.httpStatusCode)(Json.toJson(ErrorNotFound))

      case ex:UnauthorizedException => Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case ex:ForbiddenException => Unauthorized(Json.toJson(ErrorUnauthorizedLowCL))

      case e: Throwable =>
        Logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }
  }
}

trait CustomerProfileController extends BaseController with HeaderValidator with ErrorHandling {

  import ErrorResponse.writes

  val service: pushnotificationService
  val accessControl:AccountAccessControlWithHeaderCheck

  final def ping() = accessControl.validateAccept(acceptHeaderValidationRules).async {
  implicit request =>
    implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
    errorWrapper(service.ping().map(as => Ok(Json.toJson(as))))
  }
}

object SandboxCustomerProfileController extends CustomerProfileController {
  override val service = SandboxpushnotificationService
  override val accessControl = AccountAccessControlForSandbox
}

object LiveCustomerProfileController extends CustomerProfileController {
  override val service = LivepushnotificationService
  override val accessControl = AccountAccessControlWithHeaderCheck
}
