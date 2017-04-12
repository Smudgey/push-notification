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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HttpVerbs._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.http.ServiceUnavailableException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Disabled, Sent}
import uk.gov.hmrc.pushnotification.services.NotificationsServiceApi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NotificationsControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures with StubApplicationConfiguration {
  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  implicit val system = ActorSystem()
  implicit val am = ActorMaterializer()

  private trait Setup extends MockitoSugar {
    val mockService = mock[NotificationsServiceApi]

    val controller = new NotificationsController(mockService)

    val acceptHeader = "Accept" -> "application/vnd.hmrc.1.0+json"
    val updates: Map[String, NotificationStatus] = Map("123" -> Delivered, "456" -> Delivered, "789" -> Disabled)
    val updatesJsonBody: JsValue = Json.toJson(updates)

    val emptyRequest = FakeRequest(GET, "url").withHeaders(acceptHeader)
    val updateRequest = fakeRequest(updatesJsonBody)
    val invalidRequest = fakeRequest(Json.parse("""{ "foo" : "bar" }""")).withHeaders(acceptHeader)

    def fakeRequest(body:JsValue): FakeRequest[JsValue] = FakeRequest(POST, "url").withBody(body)
      .withHeaders("Content-Type" -> "application/json")
  }

  private trait Success extends Setup {
    val someMessage = Notification("end:point:a", "Hello world", Some("msg-id-1"), Sent)
    val otherMessage = Notification("end:point:b", "Goodbye", Some("msg-id-2"), Sent)

    when(mockService.getUnsentNotifications).thenReturn(Future(Seq(someMessage, otherMessage)))
    when(mockService.updateNotifications(ArgumentMatchers.any[Map[String,NotificationStatus]]())).thenReturn(Future(Seq(true, true, true)))
  }

  private trait NoNotifications extends Setup {
    when(mockService.getUnsentNotifications).thenReturn(Future(Seq.empty))
  }

  private trait Partial extends Setup {
    when(mockService.updateNotifications(ArgumentMatchers.any[Map[String,NotificationStatus]]())).thenReturn(Future(Seq(true, false, true, true)))
  }

  private trait RepositoryFailure extends Setup {
    when(mockService.getUnsentNotifications).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
    when(mockService.updateNotifications(ArgumentMatchers.any[Map[String,NotificationStatus]]())).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
  }

  "NotificationsController getUnsentNotifications" should {
    "return Ok (200) and unsent notifications when there are unsent notifications" in new Success {

      val result: Result = await(controller.getUnsentNotifications()(emptyRequest))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(
        """[
          |{"id":"msg-id-1","endpointArn":"end:point:a","message":"Hello world"},
          |{"id":"msg-id-2","endpointArn":"end:point:b","message":"Goodbye"}
          |]""".stripMargin)
    }

    "return Not Found (404) when there are no unsent notifications" in new NoNotifications {
      val result: Result = await(controller.getUnsentNotifications()(emptyRequest))

      status(result) shouldBe 404
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"NOT_FOUND","message":"No unsent notifications"}""")
    }

    "return Server Error (500) when a ServiceUnavailableException is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.getUnsentNotifications()(emptyRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }

  "NotificationsController updateNotifications" should {
    "return No Content (204) when all notifications were updated successfully" in new Success {
      val result: Result = await(controller.updateNotifications()(updateRequest))

      status(result) shouldBe 204
    }

    "return Accepted (202) when some notifications were updated successfully" in new Partial {
      val result: Result = await(controller.updateNotifications()(updateRequest))

      status(result) shouldBe 202
    }

    "return Bad Request (400) given invalid updates" in new Success {
      val result: Result = await(controller.updateNotifications()(invalidRequest))

      status(result) shouldBe 400
    }

    "return Server Error (500) when a ServiceUnavailableException is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.updateNotifications()(updateRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }
}

