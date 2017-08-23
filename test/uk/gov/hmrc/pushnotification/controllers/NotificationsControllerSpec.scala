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
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Disabled, Sent}
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
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

    def fakeRequest(body: JsValue): FakeRequest[JsValue] = FakeRequest(POST, "url").withBody(body)
      .withHeaders("Content-Type" -> "application/json")
  }

  private trait Success extends Setup {
    val someNotification = Notification(messageId = Some("msg-id-1"), endpoint = "end:point:a", content = "Hello world", notificationId = Some("ntfy-id-1"), status = Sent, os = "windows")
    val otherNotification = Notification(messageId = Some("msg-id-1"), endpoint = "end:point:b", content = "Goodbye", notificationId = Some("ntfy-id-2"), status = Sent, os = "windows")

    when(mockService.getQueuedNotifications).thenReturn(Future(Some(Seq(someNotification, otherNotification))))
    when(mockService.getTimedOutNotifications).thenReturn(Future(Some(Seq(otherNotification, someNotification))))
    when(mockService.updateNotifications(ArgumentMatchers.any[Map[String, NotificationStatus]]())).thenReturn(Future(true))
  }

  private trait NoNotifications extends Setup {
    when(mockService.getQueuedNotifications).thenReturn(Future(Some(Seq.empty)))
    when(mockService.getTimedOutNotifications).thenReturn(Future(Some(Seq.empty)))
  }

  private trait LockFailed extends Setup {
    when(mockService.getQueuedNotifications).thenReturn(Future(None))
    when(mockService.getTimedOutNotifications).thenReturn(Future(None))
  }

  private trait Partial extends Setup {
    when(mockService.updateNotifications(ArgumentMatchers.any[Map[String, NotificationStatus]]())).thenReturn(Future(false))
  }

  private trait RepositoryFailure extends Setup {
    when(mockService.getQueuedNotifications).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
    when(mockService.getTimedOutNotifications).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
    when(mockService.updateNotifications(ArgumentMatchers.any[Map[String, NotificationStatus]]())).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
  }

  "NotificationsController getQueuedNotifications" should {
    "return Ok (200) and notifications when there are queued notifications" in new Success {

      val result: Result = await(controller.getQueuedNotifications()(emptyRequest))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(
        """[
          |{"id":"ntfy-id-1","endpointArn":"end:point:a","message":"Hello world", "messageId":"msg-id-1","os":"windows"},
          |{"id":"ntfy-id-2","endpointArn":"end:point:b","message":"Goodbye", "messageId":"msg-id-1","os":"windows"}
          |]""".stripMargin)
    }

    "return Not Found (404) when there are no queued notifications" in new NoNotifications {
      val result: Result = await(controller.getQueuedNotifications()(emptyRequest))

      status(result) shouldBe 404
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"NOT_FOUND","message":"No notifications found"}""")
    }

    "return Conflict (409) when it is not possible to obtain the mongo lock" in new LockFailed {
      val result: Result = await(controller.getQueuedNotifications()(emptyRequest))

      status(result) shouldBe 409
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"CONFLICT","message":"Failed to obtain lock"}""")

    }

    "return Server Error (500) when a ServiceUnavailableException is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.getQueuedNotifications()(emptyRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }

  "NotificationsController getTimedOutNotifications" should {
    "return Ok (200) and timed-out notifications when there are notifications that have timed out" in new Success {

      val result: Result = await(controller.getTimedOutNotifications()(emptyRequest))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(
        """[
          |{"id":"ntfy-id-2","endpointArn":"end:point:b","message":"Goodbye", "messageId":"msg-id-1","os":"windows"},
          |{"id":"ntfy-id-1","endpointArn":"end:point:a","message":"Hello world", "messageId":"msg-id-1","os":"windows"}
          |]""".stripMargin)
    }

    "return Not Found (404) when there are no timed-out notifications" in new NoNotifications {
      val result: Result = await(controller.getTimedOutNotifications()(emptyRequest))

      status(result) shouldBe 404
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"NOT_FOUND","message":"No notifications found"}""")
    }

    "return CONFLICT (409) when it is not possible to obtain the mongo lock" in new LockFailed {
      val result: Result = await(controller.getTimedOutNotifications()(emptyRequest))

      status(result) shouldBe 409
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"CONFLICT","message":"Failed to obtain lock"}""")

    }

    "return Server Error (500) when a ServiceUnavailableException is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.getTimedOutNotifications()(emptyRequest))

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

