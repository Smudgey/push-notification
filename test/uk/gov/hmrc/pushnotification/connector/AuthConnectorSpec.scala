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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse, UnauthorizedException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthConnectorSpec extends UnitSpec with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val hc = HeaderCarrier()

  def authConnector(response : HttpResponse, cl: ConfidenceLevel = ConfidenceLevel.L200) = new AuthConnector {

    override def http: HttpGet = new HttpGet {
      override protected def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = Future.successful(response)

      override val hooks: Seq[HttpHook] = Seq.empty
    }

    override val serviceUrl: String = "http://localhost"

    override def serviceConfidenceLevel: ConfidenceLevel = cl
  }

  "Accounts" should {

    "be found when user's confidence level is greater than the configured confidence level" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L500

      val saUtr = Some(SaUtr("1872796160"))
      val nino = Some(Nino("CS100700A"))
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      val accounts = authConnector(response, serviceConfidenceLevel).accounts()

      accounts.nino.get shouldBe nino.get
      accounts.saUtr.get shouldBe saUtr.get
    }

    "be found when user's confidence level is equal to the configured confidence level" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = Some(Nino("CS100700A"))
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      val accounts = authConnector(response, serviceConfidenceLevel).accounts()

      accounts.nino.get shouldBe nino.get
      accounts.saUtr.get shouldBe saUtr.get
    }

    "find Nino only accounts" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = None
      val nino = Some(Nino("CS100700A"))
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      val accounts = authConnector(response, serviceConfidenceLevel).accounts()

      accounts.nino.get shouldBe nino.get
      accounts.saUtr shouldBe None
    }

    "error when an account does not have an associated NINO" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = None
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      try{
        authConnector(response, serviceConfidenceLevel).accounts()
      } catch {
        case e : UnauthorizedException =>
          e.message shouldBe "The user must have a National Insurance Number"
        case t : Throwable =>
          fail("Unexpected error failure")
      }
    }

    "error when user's confidence level is lower than the configured confidence level" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L50

      val saUtr = Some(SaUtr("1872796160"))
      val nino = Some(Nino("CS100700A"))
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      try{
        authConnector(response, serviceConfidenceLevel).accounts()
      } catch {
        case e : UnauthorizedException =>
          e.message shouldBe "The user does not have sufficient permissions to access this service"
        case t : Throwable =>
          fail("Unexpected error failure")
      }

    }

  }

  "Accounts that have nino" should {

    "error with unauthorised" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = None
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      try{
        authConnector(response, serviceConfidenceLevel).hasNino()
      } catch {
        case e : UnauthorizedException =>
          e.message shouldBe "The user must have a National Insurance Number to access this service"
        case t : Throwable =>
          fail("Unexpected error failure")
      }
    }

    "find Nino only accounts" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = None

      val nino = Some(Nino("CS100700A"))
      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino)))

      authConnector(response, serviceConfidenceLevel).hasNino().futureValue

    }
  }


  def authorityJson(confidenceLevel: ConfidenceLevel, saUtr: Option[SaUtr], nino : Option[Nino]): JsValue = {

    val sa : String = saUtr match {
      case Some(utr) => s"""
                          | "sa": {
                          |            "link": "/sa/individual/$utr",
                          |            "utr": "$utr"
                          |        },
                        """.stripMargin
      case None => ""
    }

    val paye = nino match {
      case Some(n) => s"""
                          "paye": {
                          |            "link": "/paye/individual/$n",
                          |            "nino": "$n"
                          |        },
                        """.stripMargin
      case None => ""
    }

    val json =
      s"""
         |{
         |    "accounts": {
         |       $sa
         |       $paye
         |        "ct": {
         |            "link": "/ct/8040200779",
         |            "utr": "8040200779"
         |        },
         |        "vat": {
         |            "link": "/vat/999904829",
         |            "vrn": "999904829"
         |        },
         |        "epaye": {
         |            "link": "/epaye/754%2FMODES02",
         |            "empRef": "754/MODES02"
         |        }
         |    },
         |    "confidenceLevel": ${confidenceLevel.level}
         |}
      """.stripMargin

    Json.parse(json)
  }
}
