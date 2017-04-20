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

package uk.gov.hmrc.pushnotification.services

import java.util.UUID

import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.{doAnswer, doReturn, times, verify}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeApplication
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel.L200
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.{Authority, PushRegistrationConnector, StubApplicationConfiguration}
import uk.gov.hmrc.pushnotification.domain.{Notification, Template}
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationRepositoryApi}

import scala.collection.JavaConversions._
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

class MobileMessagesServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {
  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  implicit val hc = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  private trait Setup extends MockitoSugar {
    val mockConnector = mock[PushRegistrationConnector]
    val mockRepository = mock[PushNotificationRepositoryApi]

    val service = new MobileMessagesService(mockConnector, mockRepository)

    val someAuth = Authority(Nino("CS700100A"), L200, "int-auth-id-1")
    val otherAuth = Authority(Nino("CS700101A"), L200, "int-auth-id-2")
    val brokenAuth = Authority(Nino("CS700102A"), L200, "int-auth-id-3")
    val someTemplateName = "hello"
    val someParams = Seq("Bob")
    val endpoints = Seq("foo", "bar", "baz")
    val someTemplate = Template(someTemplateName, someParams:_*)

    doReturn(successful(endpoints), Nil: _* ).when(mockConnector).endpointsForAuthId(any[String]())(any[HttpReads[Seq[String]]](), any[ExecutionContext]())
    doReturn(failed(new NotFoundException("No endpoints")), Nil: _* ).when(mockConnector).endpointsForAuthId(matches(otherAuth.authInternalId))(any[HttpReads[Seq[String]]](), any[ExecutionContext]())
    doReturn(successful(Left("Failed to save the thing")), Nil: _* ).when(mockRepository).save(matches(brokenAuth.authInternalId),any[Notification]())
    doAnswer(new Answer[Future[Either[String,NotificationPersist]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[String, NotificationPersist]] = {
        val actualAuthId: String = invocationOnMock.getArguments()(0).asInstanceOf[String]
        val actualNotification: Notification = invocationOnMock.getArguments()(1).asInstanceOf[Notification]

        successful(Right(NotificationPersist(BSONObjectID.generate, actualNotification.messageId, actualAuthId, actualNotification.endpoint, actualNotification.content, actualNotification.endpoint + "-ntfy-id", actualNotification.status, 1)))
      }
    }).when(mockRepository).save(matches(someAuth.authInternalId),any[Notification]())
  }

  "MobileMessagesService sendTemplateMessage" should {
    "return a message id given a valid template name and an authority with endpoints" in new Setup {
      val notificationCaptor: ArgumentCaptor[Notification] = ArgumentCaptor.forClass(classOf[Notification])

      val result: String = await(service.sendTemplateMessage(someTemplate)(hc, Option(someAuth)))

      verify(mockRepository, times(endpoints.size)).save(ArgumentMatchers.any[String](), notificationCaptor.capture())

      val actualNotifications = asScalaBuffer(notificationCaptor.getAllValues)

      endpoints.forall{ ep => actualNotifications.count{ n => n.endpoint == ep & n.messageId == result } == 1 } shouldBe true

      result should BeGuid
    }

    "throw an unauthorized exception given an empty authority" in new Setup {
      val result = intercept[UnauthorizedException] {
        await(service.sendTemplateMessage(someTemplate)(hc, None))
      }

      result.getMessage shouldBe "No Authority record found for request!"
    }

    "throw a bad request exception given a non-existent template" in new Setup {
      val nonExistent = "foo"

      val result = intercept[BadRequestException] {
        await(service.sendTemplateMessage(Template(nonExistent))(hc, Option(someAuth)))
      }

      result.getMessage shouldBe s"No such template '$nonExistent'"
    }

    "throw a not found exception given an authority that does not have any endpoints" in new Setup {
      val result = intercept[NotFoundException] {
        await(service.sendTemplateMessage(someTemplate)(hc, Option(otherAuth)))
      }

      result.getMessage shouldBe "No endpoints"
    }

    "throw a server error if the messages could not be created" in new Setup {
      val result = intercept[ServiceUnavailableException] {
        await(service.sendTemplateMessage(someTemplate)(hc, Option(brokenAuth)))
      }

      result.getMessage shouldBe "Failed to save the thing"
    }
  }

  class GUIDMatcher extends Matcher[String] {
    def apply(maybeGuid: String): MatchResult = {
      val result = try {
        UUID.fromString(maybeGuid)
        true
      } catch {
        case _: Exception => false
      }
      MatchResult(result,
        s"$maybeGuid is not a GUID",
        s"$maybeGuid is a GUID but it shouldn't be")
    }
  }

  def BeGuid = new GUIDMatcher
}