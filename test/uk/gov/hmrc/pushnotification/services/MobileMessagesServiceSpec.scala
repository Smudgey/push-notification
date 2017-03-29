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

import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.{doAnswer, doReturn}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.connector.PushRegistrationConnector
import uk.gov.hmrc.pushnotification.domain.Notification
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationRepository}

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

class MobileMessagesServiceSpec extends UnitSpec with ScalaFutures {
  implicit val hc = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  private trait Setup extends MockitoSugar {
    val mockConnector = mock[PushRegistrationConnector]
    val mockRepository = mock[PushNotificationRepository]

    val service = new MobileMessagesService(mockConnector, mockRepository)

    val someAuthId = "int-auth-id-1"
    val otherAuthId = "int-auth-id-2"
    val brokenAuthId = "int-auth-id-3"
    val someTemplate = "hello"
    val someParams = Seq("Bob")
    val endpoints = Seq("foo", "bar", "baz")

    doReturn(successful(endpoints), Nil: _* ).when(mockConnector).endpointsForAuthId(any[String]())(any[HttpReads[Seq[String]]](), any[ExecutionContext]())
    doReturn(failed(new NotFoundException("no endpoints")), Nil: _* ).when(mockConnector).endpointsForAuthId(matches(otherAuthId))(any[HttpReads[Seq[String]]](), any[ExecutionContext]())
    doReturn(successful(Left("failed to save the thing")), Nil: _* ).when(mockRepository).save(matches(brokenAuthId),any[Notification]())
    doAnswer(new Answer[Future[Either[String,NotificationPersist]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[String, NotificationPersist]] = {
        val actualAuthId = invocationOnMock.getArgument[String](0)
        val actualNotification = invocationOnMock.getArgument[Notification](1)

        successful(Right(NotificationPersist(BSONObjectID.generate, actualAuthId, actualNotification.endpoint, actualNotification.message, actualNotification.endpoint + "-id", actualNotification.status)))
      }
    }).when(mockRepository).save(matches(someAuthId),any[Notification]())
  }

  "MobileMessagesService sendTemplateMessage" should {
    "return a list of message ids given a valid template name and an authority with endpoints" in new Setup {
      val result = await(service.sendTemplateMessage(someAuthId, someTemplate, someParams))

      result.size shouldBe 3
      result shouldBe endpoints.map(_ + "-id")
    }

    "throw a bad request exception given a non-existent template" in new Setup {
      val nonExistent = "foo"

      val result = intercept[BadRequestException] {
        await(service.sendTemplateMessage(someAuthId, nonExistent, Seq.empty))
      }

      result.getMessage shouldBe s"no such template '$nonExistent'"
    }

    "throw a not found exception given an authority that does not have any endpoints" in new Setup {
      val result = intercept[NotFoundException] {
        await(service.sendTemplateMessage(otherAuthId, someTemplate, someParams))
      }

      result.getMessage shouldBe s"no endpoints"
    }

    "throw a server error if the messages could not be created" in new Setup {
      val result = intercept[ServiceUnavailableException] {
        await(service.sendTemplateMessage(brokenAuthId, someTemplate, someParams))
      }

      result.getMessage shouldBe "failed to save the thing"
    }
  }
}