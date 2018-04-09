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

import play.api.mvc.Result
import uk.gov.hmrc.api.controllers.ErrorResponse
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

case object ErrorUnauthorizedNoNino extends ErrorResponse(401, "UNAUTHORIZED", "NINO does not exist on account")

case object ErrorNoInternalId extends ErrorResponse(401, "UNAUTHORIZED", "Account id error")

case object ForbiddenAccess extends ErrorResponse(403, "UNAUTHORIZED", "Access denied!")

case class BadRequestError(e: BadRequestException) extends ErrorResponse(400, "BAD REQUEST", e.getMessage)

trait ErrorHandling {
  self: BaseController =>

  import play.api.libs.json.Json
  import play.api.{Logger, mvc}
  import uk.gov.hmrc.api.controllers.{ErrorInternalServerError, ErrorNotFound, ErrorUnauthorizedLowCL}
  import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, NotFoundException, UnauthorizedException}

  implicit val ec: ExecutionContext

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier): Future[Result] = {
    func.recover {
      case _: NotFoundException => Status(ErrorNotFound.httpStatusCode)(Json.toJson(ErrorNotFound))

      case _: UnauthorizedException => Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case _: ForbiddenException => Unauthorized(Json.toJson(ErrorUnauthorizedLowCL))

      case e: BadRequestException => BadRequest(Json.toJson(BadRequestError(e)))

      case e: Throwable =>
        Logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }
  }
}