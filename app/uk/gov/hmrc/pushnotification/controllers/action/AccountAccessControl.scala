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

package uk.gov.hmrc.pushnotification.controllers.action

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.api.controllers.{ErrorAcceptHeaderInvalid, ErrorUnauthorized, ErrorUnauthorizedLowCL, HeaderValidator}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, HttpGet, HttpResponse}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.pushnotification.connector._
import uk.gov.hmrc.pushnotification.controllers.{ErrorNoInternalId, ErrorUnauthorizedNoNino, ForbiddenAccess}

import scala.concurrent.Future

case class AuthenticatedRequest[A](authority: Option[Authority], request: Request[A]) extends WrappedRequest(request)

trait AccountAccessControlApi extends ActionBuilder[AuthenticatedRequest] with Results {

  import scala.concurrent.ExecutionContext.Implicits.global

  val authConnector: AuthConnector

  def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]) = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

    authConnector.grantAccess().flatMap {
      authority => {
        block(AuthenticatedRequest(Some(authority), request))
      }
    }.recover {
      case _: uk.gov.hmrc.http.Upstream4xxResponse => Unauthorized(Json.toJson(ErrorUnauthorized))

      case _: ForbiddenException =>
        Logger.error("Unauthorized! ForbiddenException caught and returning 403 status!")
        Forbidden(Json.toJson(ForbiddenAccess))

      case _: NinoNotFoundOnAccount =>
        Logger.error("Unauthorized! NINO not found on account!")
        Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case _: NoInternalId =>
        Logger.info("Account does not have an internal id!")
        Unauthorized(Json.toJson(ErrorNoInternalId))

      case _: AccountWithLowCL =>
        Logger.error("Unauthorized! Account with low CL!")
        Unauthorized(Json.toJson(ErrorUnauthorizedLowCL))
    }
  }

}

trait AccountAccessControlWithHeaderCheckApi extends HeaderValidator {
  val checkAccess = true
  val accessControl: AccountAccessControlApi

  override def validateAccept(rules: Option[String] => Boolean) = new ActionBuilder[AuthenticatedRequest] {

    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]) = {
      if (rules(request.headers.get("Accept"))) {
        if (checkAccess) accessControl.invokeBlock(request, block)
        else block(AuthenticatedRequest(None, request))
      }
      else Future.successful(Status(ErrorAcceptHeaderInvalid.httpStatusCode)(Json.toJson(ErrorAcceptHeaderInvalid)))
    }
  }
}

@Singleton
class Auth @Inject()(val authConnector: AuthConnector)

@Singleton
class AccountAccessControl @Inject()(val auth: Auth) extends AccountAccessControlApi {
  val authConnector: AuthConnector = auth.authConnector
}

@Singleton
class AccountAccessControlWithHeaderCheck @Inject()(val accessControl: AccountAccessControl) extends AccountAccessControlWithHeaderCheckApi

@Singleton
class AccountAccessControlSandbox @Inject()(configuration: Option[Config]) extends AccountAccessControlApi {
  val authConnector: AuthConnector = new AuthConnector("NO SERVICE", ConfidenceLevel.L0, new HttpGet {
    override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = Future.failed(new IllegalArgumentException("Sandbox mode!"))

    override val hooks: Seq[HttpHook] = NoneRequired

    override def configuration: Option[Config] = configuration
  })
}
