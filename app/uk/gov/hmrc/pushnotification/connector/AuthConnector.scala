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

package uk.gov.hmrc.pushnotification.connector

import play.api.{Logger, Play}
import play.api.libs.json.JsValue
import uk.gov.hmrc.pushnotification.config.WSHttp
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{ForbiddenException, HeaderCarrier, HttpGet, UnauthorizedException}

import scala.concurrent.{ExecutionContext, Future}

trait AuthConnector {

  import uk.gov.hmrc.pushnotification.domain.Accounts
  import uk.gov.hmrc.domain.{Nino, SaUtr}
  import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

  val serviceUrl: String

  def http: HttpGet

  def serviceConfidenceLevel: ConfidenceLevel

  def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = {
    http.GET(s"$serviceUrl/auth/authority") map {
      resp =>
        val json = resp.json
        confirmConfiendenceLevel(json)

        val accounts = json \ "accounts"

        val utr = (accounts \ "sa" \ "utr").asOpt[String]

        val nino = (accounts \ "paye" \ "nino").asOpt[String]

        val acc = Accounts(nino.map(Nino(_)), utr.map(SaUtr(_)))
        acc match {
          case Accounts(None, _) => {
            //TODO add a metric for this ????
            Logger.warn("User without a NINO has accessed the service this should not be possible")
            throw new UnauthorizedException("The user must have a National Insurance Number")
          }
          case _ => acc
        }
    }
  }

  def hasNino()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    http.GET(s"$serviceUrl/auth/authority") map {
      resp =>
        if((resp.json \ "accounts" \ "paye" \ "nino").asOpt[String].isEmpty)
          throw new UnauthorizedException("The user must have a National Insurance Number to access this service")
    }
  }

  private def confirmConfiendenceLevel(jsValue : JsValue) = {
    val usersCL = (jsValue \ "confidenceLevel").as[Int]
    if (serviceConfidenceLevel.level > usersCL) {
      throw new ForbiddenException("The user does not have sufficient permissions to access this service")
    }
  }
}

object AuthConnector extends AuthConnector with ServicesConfig {

  import play.api.Play.current

  val serviceUrl = baseUrl("auth")
  val http = WSHttp
  val serviceConfidenceLevel: ConfidenceLevel = ConfidenceLevel.fromInt(Play.configuration.getInt("controllers.confidenceLevel")
    .getOrElse(throw new RuntimeException("The service has not been configured with a confidence level")))
}
