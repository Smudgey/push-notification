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

package uk.gov.hmrc.pushnotification.connector

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import play.api.libs.json.JsValue
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{CoreGet, ForbiddenException, HeaderCarrier}

import scala.concurrent.{ExecutionContext, Future}

class NinoNotFoundOnAccount(message: String) extends uk.gov.hmrc.http.HttpException(message, 401)

class NoInternalId(message: String) extends uk.gov.hmrc.http.HttpException(message, 401)

class AccountWithLowCL(message: String) extends uk.gov.hmrc.http.HttpException(message, 401)

case class Authority(nino: Nino, cl: ConfidenceLevel, authInternalId: String)

@ImplementedBy(classOf[AuthConnector])
trait AuthConnectorApi {

  val serviceUrl: String

  def http: CoreGet

  def serviceConfidenceLevel: ConfidenceLevel

  def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = {
    for (
      (nino, cl, oid) <- getAuthority;
      id <- getAuthInternalId(oid)
    ) yield Authority(nino, cl, id)
  }

  private def getAuthority()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[(Nino, ConfidenceLevel, String)] =
    http.GET(s"$serviceUrl/auth/authority") map {
      resp => {
        val json = resp.json
        val cl = confirmConfidenceLevel(json)
        val ids = (json \ "ids").as[String]
        val nino = (json \ "accounts" \ "paye" \ "nino").asOpt[String]

        if ((json \ "accounts" \ "paye" \ "nino").asOpt[String].isEmpty)
          throw new NinoNotFoundOnAccount("The user must have a National Insurance Number")

        (Nino(nino.get), ConfidenceLevel.fromInt(cl).getOrElse(ConfidenceLevel.L0), ids)
      }
    }

  private def getAuthInternalId(ids: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] =
    http.GET(s"$serviceUrl$ids") map {
      resp => {
        val json = resp.json
        if ((json \ "internalId").asOpt[String].isEmpty)
          throw new NoInternalId("The user must have an internal id")

        (json \ "internalId").as[String]
      }
    }

  private def confirmConfidenceLevel(jsValue: JsValue): Int = {
    val usersCL = (jsValue \ "confidenceLevel").as[Int]
    if (serviceConfidenceLevel.level > usersCL) {
      throw new ForbiddenException("The user does not have sufficient permissions to access this service")
    }
    usersCL
  }

}

@Singleton
class AuthConnector @Inject()(@Named("authUrl") val serviceUrl: String,
                              val serviceConfidenceLevel: ConfidenceLevel,
                              val http: CoreGet) extends AuthConnectorApi