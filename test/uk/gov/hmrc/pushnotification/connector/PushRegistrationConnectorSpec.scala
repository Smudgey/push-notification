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

package uk.gov.hmrc.pushnotification.connector

import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class PushRegistrationConnectorSpec extends UnitSpec with ScalaFutures {

  implicit val hc = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new PushRegistrationConnector("http://somewhere:1234", mockHttp)

    val someId = "int-some-id"
    val otherId = "int-other-id"
    val brokenId = "int-broken-id"
    val endpoints = Map[String, String]("end:point:a" -> "windows", "end:point:b" -> "android")

    when(mockHttp.GET[Map[String, String]](matches(s"${connector.serviceUrl}/push/endpoint/$someId"))(any[HttpReads[Map[String, String]]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(successful(endpoints))
    when(mockHttp.GET[Map[String, String]](matches(s"${connector.serviceUrl}/push/endpoint/$otherId"))(any[HttpReads[Map[String, String]]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(failed(new NotFoundException("nothing for you here")))
    when(mockHttp.GET[Map[String, String]](matches(s"${connector.serviceUrl}/push/endpoint/$brokenId"))(any[HttpReads[Map[String, String]]], any[HeaderCarrier], any[ExecutionContext])).thenReturn(failed(Upstream5xxResponse("BOOOOM!", 500, 500)))
  }

  "PushRegistrationConnector endpointsForAuthId" should {
    "return endpoints given an auth id that has associated endpoints" in new Setup {
      val result: Map[String, String] = await(connector.endpointsForAuthId(someId))

      result shouldBe endpoints
    }

    "throw a NotFoundException given an auth id that does not have any endpoints" in new Setup {
      intercept[NotFoundException] {
        await(connector.endpointsForAuthId(otherId))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.endpointsForAuthId(brokenId))
      }
    }
  }

}
