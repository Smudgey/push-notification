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
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HttpVerbs.{GET, POST}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.http.ServiceUnavailableException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Answer, Timeout}
import uk.gov.hmrc.pushnotification.domain._
import uk.gov.hmrc.pushnotification.services.CallbackService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CallbackControllerSpec  extends UnitSpec with WithFakeApplication with ScalaFutures with StubApplicationConfiguration {
  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  implicit val system = ActorSystem()
  implicit val am = ActorMaterializer()

  private trait Setup extends MockitoSugar {
    val mockService = mock[CallbackService]

    val controller = new CallbackController(mockService)

    val acceptHeader = "Accept" -> "application/vnd.hmrc.1.0+json"
    val callbackResults: CallbackResultBatch = CallbackResultBatch(Seq(
      CallbackResult("msg-123", Acknowledge, success = true),
      CallbackResult("msg-456", Answer, success = false)
    ))
    val callbackResultsJson: JsValue = Json.toJson(callbackResults)

    val emptyRequest = FakeRequest(GET, "url").withHeaders(acceptHeader)
    val updateRequest = fakeRequest(callbackResultsJson)
    val invalidRequest = fakeRequest(Json.parse("""{ "foo" : "bar" }""")).withHeaders(acceptHeader)

    def fakeRequest(body: JsValue): FakeRequest[JsValue] = FakeRequest(POST, "url").withBody(body)
      .withHeaders("Content-Type" -> "application/json")
  }

  private trait Success extends Setup {
    val someCallback = Callback("http://call/back/here", Answer, Response("msg-1", Some("yes")), 2)
    val otherCallback = Callback("http://call/back/there", Timeout, Response("msg-2", None), 1)

    when(mockService.getUndeliveredCallbacks).thenReturn(Future(Some(CallbackBatch(Seq(someCallback, otherCallback)))))
    when(mockService.updateCallbacks(ArgumentMatchers.any[CallbackResultBatch]())).thenReturn(Future(true))
  }

  private trait NoCallbacks extends Setup {
    when(mockService.getUndeliveredCallbacks).thenReturn(Future(Some(CallbackBatch(Seq.empty))))
  }

  private trait LockFailed extends Setup {
    when(mockService.getUndeliveredCallbacks).thenReturn(Future(None))
  }

  private trait Partial extends Setup {
    when(mockService.updateCallbacks(ArgumentMatchers.any[CallbackResultBatch]())).thenReturn(Future(false))
  }

  private trait RepositoryFailure extends Setup {
    when(mockService.getUndeliveredCallbacks).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
    when(mockService.updateCallbacks(ArgumentMatchers.any[CallbackResultBatch]())).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
  }

  "CallbackController getQueuedNotifications" should {
    "return Ok (200) and notifications when there are undelivered callbacks" in new Success {

      val result: Result = await(controller.getUndeliveredCallbacks()(emptyRequest))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(
        """
          |{
          | "batch" : [
          |   {
          |     "callbackUrl" : "http://call/back/here",
          |     "status" : "answer",
          |     "response" : {
          |       "messageId" : "msg-1",
          |       "answer" : "yes"
          |     },
          |     "attempt" : 2
          |   },
          |   {
          |     "callbackUrl":"http://call/back/there",
          |     "status" : "timeout",
          |     "response" : {
          |       "messageId" : "msg-2"
          |     },
          |     "attempt":1
          |   }
          | ]
          |}
          |""".stripMargin)
    }

    "return Not Found (404) when there are no undelivered callbacks" in new NoCallbacks {
      val result: Result = await(controller.getUndeliveredCallbacks()(emptyRequest))

      status(result) shouldBe 404
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"NOT_FOUND","message":"No callbacks found"}""")
    }

    "return CONFLICT (409) when it is not possible to obtain the mongo lock" in new LockFailed {
      val result: Result = await(controller.getUndeliveredCallbacks()(emptyRequest))

      status(result) shouldBe 409
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"CONFLICT","message":"Failed to obtain lock"}""")

    }

    "return Server Error (500) when a ServiceUnavailableException is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.getUndeliveredCallbacks()(emptyRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }

  "CallbackController updateCallbacks" should {
    "return No Content (204) when all callback results were updated successfully" in new Success {
      val result: Result = await(controller.updateCallbacks()(updateRequest))

      status(result) shouldBe 204
    }

    "return Accepted (202) when some callback results were updated successfully" in new Partial {
      val result: Result = await(controller.updateCallbacks()(updateRequest))

      status(result) shouldBe 202
    }

    "return Bad Request (400) given invalid updates" in new Success {
      val result: Result = await(controller.updateCallbacks()(invalidRequest))

      status(result) shouldBe 400
    }

    "return Server Error (500) when a ServiceUnavailableException is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.updateCallbacks()(updateRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }
}
