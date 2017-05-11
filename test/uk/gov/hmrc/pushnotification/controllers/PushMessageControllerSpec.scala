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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.POST
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, ServiceUnavailableException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.{AuthConnector, Authority, NoInternalId, StubApplicationConfiguration}
import uk.gov.hmrc.pushnotification.controllers.action.{AccountAccessControl, AccountAccessControlWithHeaderCheck, Auth}
import uk.gov.hmrc.pushnotification.domain.{PushMessage, PushMessageStatus, Template}
import uk.gov.hmrc.pushnotification.services.PushMessageServiceApi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class PushMessageControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures with StubApplicationConfiguration {
  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  implicit val system = ActorSystem()
  implicit val am = ActorMaterializer()

  private trait Setup extends MockitoSugar {
    val mockService = mock[PushMessageServiceApi]
    val mockAuthConnector = mock[AuthConnector]

    val testAccessControl = new AccountAccessControlWithHeaderCheck(new AccountAccessControl(new Auth(mockAuthConnector)))

    val controller = new PushMessageController(mockService, testAccessControl)

    val someAuthority = Authority(Nino("CS700100A"), ConfidenceLevel.L200, "int-id-123")

    val someMessageId = "msg-some-id"
    val otherMessageId = "msg-other-id"

    val acceptHeader = "Accept" -> "application/vnd.hmrc.1.0+json"
    val template = Template("NGC_002", Map("fullName" -> "Inspector Gadget", "agent" -> "Agent 47"))
    val templateJsonBody: JsValue = Json.toJson(template)

    val messageRequest = fakeRequest(templateJsonBody).withHeaders(acceptHeader)
    val invalidRequest = fakeRequest(Json.parse("""{ "foo" : "bar" }""")).withHeaders(acceptHeader)

    val acknowledgeRequest = fakeRequest(Json.parse(s"""{ "messageId" : "$someMessageId" }"""))
    val answerRequest = fakeRequest(Json.parse(s"""{ "messageId" : "$someMessageId", "answer" : "yes" }"""))

    def fakeRequest(body: JsValue) = FakeRequest(POST, "url").withBody(body)
      .withHeaders("Content-Type" -> "application/json")

    val someMessage = Some(PushMessage("snarkle", "Foo, bar baz!", "http://example.com/quux", Map("yes" -> "Sure", "no" -> "Nope"), someMessageId))
  }

  private trait Success extends Setup {
    when(mockAuthConnector.grantAccess()(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(Future(someAuthority))
    when(mockService.sendTemplateMessage(any[Template]())(any[HeaderCarrier](), any[Option[Authority]]())).thenReturn(Future(Option("foo")))
    when(mockService.respondToMessage(any[String](), any[PushMessageStatus](), any[Option[String]])).thenReturn(Future((true, someMessage)))
  }

  private trait Duplicate extends Setup {
    when(mockService.respondToMessage(any[String](), any[PushMessageStatus](), any[Option[String]])).thenReturn(Future((false, someMessage)))
  }

  private trait AuthFailure extends Setup {
    when(mockAuthConnector.grantAccess()(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(Future(throw new NoInternalId("missing internal id")))
    when(mockService.sendTemplateMessage(any[Template]())(any[HeaderCarrier](), any[Option[Authority]]())).thenReturn(Future(Option("bar")))
  }

  private trait TemplateFailure extends Setup {
    when(mockAuthConnector.grantAccess()(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(Future(someAuthority))
    when(mockService.sendTemplateMessage(any[Template]())(any[HeaderCarrier](), any[Option[Authority]]())).thenReturn(Future(throw new BadRequestException("really bad request")))
  }

  private trait RepositoryFailure extends Setup {
    when(mockAuthConnector.grantAccess()(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(Future(someAuthority))
    when(mockService.sendTemplateMessage(any[Template]())(any[HeaderCarrier](), any[Option[Authority]]())).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
    when(mockService.respondToMessage(any[String](), any[PushMessageStatus](), any[Option[String]])).thenReturn(Future(throw new ServiceUnavailableException("service unavailable")))
  }

  private trait Invalid extends Setup {
    when(mockService.respondToMessage(any[String](), any[PushMessageStatus](), any[Option[String]])).thenReturn(Future(throw new BadRequestException("invalid answer")))
  }

  "PushMessageController sendTemplateMessage" should {
    "create notifications successfully and return 201 success" in new Success {

      val result: Result = await(controller.sendTemplateMessage()(messageRequest))

      status(result) shouldBe 201
      jsonBodyOf(result) shouldBe Json.parse("""{"messageId":"foo"}""")
    }


    "return successfully with a 201 response when journeyId is supplied" in new Success {

      val result: Result = await(controller.sendTemplateMessage(Some("journey-id"))(messageRequest))

      status(result) shouldBe 201
      jsonBodyOf(result) shouldBe Json.parse("""{"messageId":"foo"}""")
    }

    "return 400 bad request given an invalid request" in new Success {

      val result: Result = await(controller.sendTemplateMessage()(invalidRequest))

      status(result) shouldBe 400
    }

    "return 401 result when authority record does not contain an internal-id" in new AuthFailure {
      val result: Result = await(controller.sendTemplateMessage()(messageRequest))

      status(result) shouldBe 401
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"UNAUTHORIZED","message":"Account id error"}""")
    }

    "return 400 result when bad request exception is thrown by service" in new TemplateFailure {
      val result: Result = await(controller.sendTemplateMessage()(messageRequest))

      status(result) shouldBe 400
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"BAD REQUEST","message":"really bad request"}""")
    }

    "return 500 result when bad service unavailable exception is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.sendTemplateMessage()(messageRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }

  "PushMessageController respondToMessage" should {
    "acknowledge the message and return 200 success with the message details" in new Success {
      val result: Result = await(controller.respondToMessage(someMessageId)(acknowledgeRequest))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(
        """
          |{
          |  "id":"msg-some-id",
          |  "subject":"snarkle",
          |  "body":"Foo, bar baz!",
          |  "responses":{
          |    "yes":"Sure",
          |    "no":"Nope"
          |  }
          |}
          |""".stripMargin)
    }

    "record the answer and return 200 success" in new Success {
      val result: Result = await(controller.respondToMessage(someMessageId)(answerRequest))

      status(result) shouldBe 200
    }

    "return 202 accepted and message details, given a previously acknowledged message" in new Duplicate {
      val result: Result = await(controller.respondToMessage(someMessageId)(acknowledgeRequest))

      status(result) shouldBe 202
      jsonBodyOf(result) shouldBe Json.parse(
        """
          |{
          |  "id":"msg-some-id",
          |  "subject":"snarkle",
          |  "body":"Foo, bar baz!",
          |  "responses":{
          |    "yes":"Sure",
          |    "no":"Nope"
          |  }
          |}
          |""".stripMargin)
    }

    "return 202 accepted given a previously answered message" in new Duplicate {
      val result: Result = await(controller.respondToMessage(someMessageId)(answerRequest))

      status(result) shouldBe 202
    }

    "return successfully with a 201 response when journeyId is supplied" in new Success {
      val result: Result = await(controller.respondToMessage(someMessageId, Some("journey-id"))(answerRequest))

      status(result) shouldBe 200
    }

    "return 400 bad request given an invalid request" in new Success {
      val result: Result = await(controller.respondToMessage(someMessageId)(invalidRequest))

      status(result) shouldBe 400
    }

    "return 400 bad request when the messageId in the path does not match the id in the payload" in new Success {
      val result: Result = await(controller.respondToMessage(otherMessageId)(answerRequest))

      status(result) shouldBe 400
    }

    "return 400 bad request when an invalid answer is provided in the payload" in new Invalid {
      val result: Result = await(controller.respondToMessage(someMessageId)(answerRequest))

      status(result) shouldBe 400
    }

    "return 500 result when bad service unavailable exception is thrown by service" in new RepositoryFailure {
      val result: Result = await(controller.respondToMessage(someMessageId)(acknowledgeRequest))

      status(result) shouldBe 500
      jsonBodyOf(result) shouldBe Json.parse("""{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}""")
    }
  }
}

