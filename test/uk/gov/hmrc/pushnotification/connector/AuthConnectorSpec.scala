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

import org.mockito.ArgumentMatchers.{any, contains, endsWith}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.config.WSHttpImpl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

class AuthConnectorSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  implicit val hc = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockHttp: WSHttpImpl = mock[WSHttpImpl]

  val serviceUrl = "http://localhost:1234/auth"

  "grantAccess" should {

    "create an Authority with an internal id" in {
      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = Some(Nino("CS100700A"))
      val oid = "ab12cd34"
      val internalId = "int-" + oid
      val externalId = "ext-" + oid

      val authResponse = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, oid)))
      val oidResponse = HttpResponse(200, Some(idsJson(internalId, externalId)))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(authResponse))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      val authority: Authority = await(new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess())

      authority.authInternalId shouldBe internalId
    }

    "error with unauthorised when no internal id can be found" in {
      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = Some(Nino("CS100700A"))
      val oid = "ab12cd34"
      val internalId = "int-" + oid
      val externalId = "ext-" + oid

      val authResponse = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, oid)))
      val oidResponse = HttpResponse(200, Some(Json.parse("""{ "foo": "bar" }""")))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(authResponse))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      try {
        await(new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess())
      } catch {
        case e: NoInternalId =>
          e.message shouldBe "The user must have an internal id"
        case t: Throwable =>
          fail("Unexpected exception")
      }
    }

    "error with unauthorised when account has low CL" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L50
      val saUtr = Some(SaUtr("1872796160"))
      val nino = None

      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, "ab12cd34")))
      val oidResponse = HttpResponse(200, Some(idsJson("foo", "bar")))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(response))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      try {
        await(new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess())
      } catch {
        case e: ForbiddenException =>
          e.message shouldBe "The user does not have sufficient permissions to access this service"
        case t: Throwable =>
          fail("Unexpected error failure")
      }
    }

    "successfully return account with NINO when SAUTR is empty" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = Some(Nino("CS100700A"))

      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, "ab12cd34")))
      val oidResponse = HttpResponse(200, Some(idsJson("foo", "bar")))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(response))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      await(new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess())
    }

    "find NINO only account when CL is correct" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200
      val saUtr = None
      val nino = Some(Nino("CS100700A"))

      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, "ab12cd34")))
      val oidResponse = HttpResponse(200, Some(idsJson("foo", "bar")))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(response))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      await(new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess())

    }

    "fail to return authority when no NINO exists" in {
      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200
      val saUtr = None
      val nino = None

      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, "ab12cd34")))
      val oidResponse = HttpResponse(200, Some(idsJson("foo", "bar")))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(response))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      try {
        await(new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess())
      } catch {
        case e: NinoNotFoundOnAccount =>
          e.message shouldBe "The user must have a National Insurance Number"
        case t: Throwable =>
          fail("Unexpected error failure with exception " + t)
      }
    }

  }


  "Accounts that have nino" should {

    "error with unauthorised" in {

      val serviceConfidenceLevel = ConfidenceLevel.L200
      val authorityConfidenceLevel = ConfidenceLevel.L200

      val saUtr = Some(SaUtr("1872796160"))
      val nino = None

      val response = HttpResponse(200, Some(authorityJson(authorityConfidenceLevel, saUtr, nino, "ab12cd34")))
      val oidResponse = HttpResponse(200, Some(idsJson("foo", "bar")))

      when(mockHttp.GET[HttpResponse](endsWith("/authority"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(response))
      when(mockHttp.GET[HttpResponse](contains("/oid"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(oidResponse))

      try {
        new AuthConnector(serviceUrl, serviceConfidenceLevel, mockHttp).grantAccess()
      } catch {
        case e: UnauthorizedException =>
          e.message shouldBe "The user must have a National Insurance Number to access this service"
        case t: Throwable =>
          fail("Unexpected error failure")
      }
    }
  }

  def authorityJson(confidenceLevel: ConfidenceLevel, saUtr: Option[SaUtr], nino: Option[Nino], oid: String): JsValue = {

    val sa: String = saUtr match {
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
         |    "confidenceLevel" : ${confidenceLevel.level},
         |    "uri" : "/auth/oid/$oid",
         |    "ids" : "/auth/oid/$oid/ids"
         |}
      """.stripMargin

    Json.parse(json)
  }

  def idsJson(internalId: String, externalId: String): JsValue = {
    val json =
      s"""
         |{
         |  "internalId": "$internalId",
         |  "externalId": "$externalId"
         |}
       """.stripMargin

    Json.parse(json)
  }
}
