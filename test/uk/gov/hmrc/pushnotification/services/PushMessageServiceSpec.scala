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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, matches, eq => eqs}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeApplication
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel.L200
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.{Authority, PushRegistrationConnector, StubApplicationConfiguration}
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.Acknowledge
import uk.gov.hmrc.pushnotification.domain.{Notification, PushMessage, PushMessageStatus, Template}
import uk.gov.hmrc.pushnotification.repository.ProcessingStatus.Queued
import uk.gov.hmrc.pushnotification.repository._

import scala.collection.JavaConversions._
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

class PushMessageServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {
  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  implicit val hc = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  private trait Setup extends MockitoSugar {
    val mockConnector = mock[PushRegistrationConnector]
    val mockNotificationRepository = mock[PushNotificationRepositoryApi]
    val mockMessageRepository = mock[PushMessageRepositoryApi]
    val mockCallbackRepository = mock[CallbackRepositoryApi]

    val service = new PushMessageService(mockConnector, mockNotificationRepository, mockMessageRepository, mockCallbackRepository)

    val someAuth = Authority(Nino("CS700100A"), L200, "int-auth-id-1")
    val otherAuth = Authority(Nino("CS700101A"), L200, "int-auth-id-2")
    val brokenAuth = Authority(Nino("CS700102A"), L200, "int-auth-id-3")

    val someMessageId = "53809ad8-f482-41bd-8aef-2cbe7f324a39"

    val someTemplateId = "NGC_002"
    val someCallbackUrl = "http://callback.url"
     val someParams = Map("name" -> "Clark Kent", "device" -> "Snorble 3", "time"-> "09:12", "location"->"Metropolis", "callbackUrl" -> "http://callback.url")

    val endpointsWithOs = Map("foo" -> "windows", "bar" -> "android", "baz" -> "ios")
    val someTemplate = Template(someTemplateId, someParams)
    val someStatus = Acknowledge
    val someAnswer = None
    val someSubject = "Grault"
    val someBody = "There is no Frigate like a Book\nTo take us Lands away"
    val someResponses = Map("yes" -> "Yes", "no" -> "No")
    val someUrl = "http://over.yonder/call/back"

    val somePushMessage = PushMessage(someSubject, someBody, someUrl, someResponses, someMessageId )
    val savedMessage = PushMessagePersist(BSONObjectID.generate, someAuth.authInternalId, someMessageId, someSubject, someBody, someResponses, someUrl)
  }

  private trait Success extends Setup {
    when(mockConnector.endpointsForAuthId(any[String]())(any[HttpReads[Map[String, String]]](), any[ExecutionContext]())).thenReturn(successful(endpointsWithOs))
    when(mockConnector.endpointsForAuthId(matches(otherAuth.authInternalId))(any[HttpReads[Map[String, String]]](), any[ExecutionContext]())).thenReturn(failed(new NotFoundException("No endpoints")))
    when(mockNotificationRepository.save(matches(someAuth.authInternalId), any[Notification]())).thenAnswer(new Answer[Future[Either[String, NotificationPersist]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[String, NotificationPersist]] = {
        val actualAuthId: String = invocationOnMock.getArguments()(0).asInstanceOf[String]
        val actualNotification: Notification = invocationOnMock.getArguments()(1).asInstanceOf[Notification]

        successful(Right(NotificationPersist(BSONObjectID.generate, actualNotification.endpoint + "-ntfy-id", actualNotification.messageId, actualAuthId, actualNotification.endpoint, actualNotification.content, actualNotification.os, actualNotification.status, 1)))
      }
    })

    when(mockMessageRepository.find(matches(someMessageId), any[Option[String]]())).thenReturn(successful(Some(savedMessage)))
    when(mockMessageRepository.save(matches(someAuth.authInternalId), any[PushMessage]())).thenReturn(
      successful(Right(PushMessagePersist(BSONObjectID.generate, someAuth.authInternalId, someMessageId, someSubject, someBody, someResponses, someCallbackUrl)))
    )
    when(mockMessageRepository.save(matches(otherAuth.authInternalId), any[PushMessage]())).thenReturn(
      successful(Right(PushMessagePersist(BSONObjectID.generate, someAuth.authInternalId, someMessageId, someSubject, someBody, someResponses, someCallbackUrl)))
    )
    when(mockCallbackRepository.save(any[String](), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]())).thenReturn(successful(Right(true)))

    when(mockMessageRepository.findByAuthority(matches(someAuth.authInternalId))).thenReturn(successful(Seq(savedMessage)))


    def latestMessageIsOfStatus(status: PushMessageStatus) = {
      when(mockCallbackRepository.findLatest(savedMessage.messageId)).thenReturn(successful(Some(PushMessageCallbackPersist(BSONObjectID.generate, savedMessage.messageId, someUrl, status, someAnswer, Queued, 0))))
    }

    def latestCallbackForMessageIs(callback: Option[PushMessageCallbackPersist]) = {
      when(mockCallbackRepository.findLatest(eqs(savedMessage.messageId)))
        .thenReturn(successful(callback))
    }

    def callbackNotCreated() = {
      when(mockCallbackRepository.findLatest(savedMessage.messageId)).thenReturn(successful(None))
    }

    def primeFindForMessageAndAuthId(messageId:String, authId:String, response:Option[PushMessagePersist]) = {
      when(mockMessageRepository.find(messageId, Some(authId))).thenReturn(successful(response))
    }
  }

  private trait Duplicate extends Setup {
    when(mockMessageRepository.find(matches(someMessageId), any[Option[String]]())).thenReturn(successful(Some(savedMessage)))
    when(mockCallbackRepository.save(any[String](), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]()))
      .thenReturn(
        successful(Right(true)),
        successful(Right(false)))
  }

  private trait Invalid extends Setup {
    when(mockMessageRepository.find(matches(someMessageId), any[Option[String]]())).thenReturn(successful(None))
  }

  private trait Failed extends Setup {
    when(mockConnector.endpointsForAuthId(any[String]())(any[HttpReads[Map[String, String]]](), any[ExecutionContext]())).thenReturn(successful(endpointsWithOs))
    when(mockMessageRepository.save(any[String], any[PushMessage])).thenReturn(successful(Right(PushMessagePersist(BSONObjectID.generate, someAuth.authInternalId, someMessageId, someSubject, someBody, someResponses, someCallbackUrl))))
    when(mockMessageRepository.find(matches(someMessageId), any[Option[String]]())).thenReturn(successful(Some(savedMessage)))
    when(mockNotificationRepository.save(matches(brokenAuth.authInternalId), any[Notification]())).thenReturn(successful(Left("Failed to save the thing")))
    when(mockCallbackRepository.save(any[String](), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]())).thenReturn(successful(Left("Failed to save the thing")))
  }

  "PushMessageService sendTemplateMessage" should {
    "return a message id given a valid template name and an authority with endpoints" in new Success with GUIDUtil {
      val notificationCaptor: ArgumentCaptor[Notification] = ArgumentCaptor.forClass(classOf[Notification])

      val result: Option[String] = await(service.sendTemplateMessage(someTemplate)(hc, Option(someAuth)))

      verify(mockNotificationRepository, times(endpointsWithOs.size)).save(any[String](), notificationCaptor.capture())

      val actualNotifications = asScalaBuffer(notificationCaptor.getAllValues)

      endpointsWithOs.forall { ep => actualNotifications.count { n => n.endpoint == ep._1 & n.messageId.get == result.get } == 1 } shouldBe true

      result.get should BeGuid
    }

    //TODO Added tests for sending a message with and without a messageId.

    "throw an unauthorized exception given an empty authority" in new Success {
      val result = intercept[UnauthorizedException] {
        await(service.sendTemplateMessage(someTemplate)(hc, None))
      }

      result.getMessage shouldBe "No Authority record found for request!"
    }

    "throw a bad request exception given a non-existent template" in new Success {
      val nonExistent = "foo"

      val result = intercept[BadRequestException] {
        await(service.sendTemplateMessage(Template(nonExistent))(hc, Option(someAuth)))
      }

      result.getMessage shouldBe s"Template $nonExistent not found"
    }

    "throw a not found exception given an authority that does not have any endpoints" in new Success {
      val result = intercept[NotFoundException] {
        await(service.sendTemplateMessage(someTemplate)(hc, Option(otherAuth)))
      }

      result.getMessage shouldBe "No endpoints"
    }

    "throw a server error if the messages could not be created" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.sendTemplateMessage(someTemplate)(hc, Option(brokenAuth)))
      }

      result.getMessage shouldBe "Failed to save the thing"
    }
  }

  "PushMessageService respondToMessage" should {
    "save the acknowledgement with the callback url and return true given a valid message id" in new Success {
      val idCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val statusCaptor: ArgumentCaptor[PushMessageStatus] = ArgumentCaptor.forClass(classOf[PushMessageStatus])
      val answerCaptor: ArgumentCaptor[Option[String]] = ArgumentCaptor.forClass(classOf[Option[String]])
      val attemptCaptor: ArgumentCaptor[Int] = ArgumentCaptor.forClass(classOf[Int])

      val result: Boolean = await(service.respondToMessage(someMessageId, Acknowledge, None))

      verify(mockCallbackRepository).save(idCaptor.capture(), urlCaptor.capture(), statusCaptor.capture(), answerCaptor.capture(), attemptCaptor.capture())

      idCaptor.getValue shouldBe someMessageId
      urlCaptor.getValue shouldBe someUrl
      statusCaptor.getValue shouldBe Acknowledge
      answerCaptor.getValue shouldBe None
      attemptCaptor.getValue shouldBe 0

      result shouldBe true
    }

    "save the answer with the callback url and return true given a valid message id and answer" in new Success {
      val idCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val statusCaptor: ArgumentCaptor[PushMessageStatus] = ArgumentCaptor.forClass(classOf[PushMessageStatus])
      val answerCaptor: ArgumentCaptor[Option[String]] = ArgumentCaptor.forClass(classOf[Option[String]])
      val attemptCaptor: ArgumentCaptor[Int] = ArgumentCaptor.forClass(classOf[Int])

      val result: Boolean = await(service.respondToMessage(someMessageId, PushMessageStatus.Answer, Some("yes")))

      verify(mockCallbackRepository).save(idCaptor.capture(), urlCaptor.capture(), statusCaptor.capture(), answerCaptor.capture(), attemptCaptor.capture())

      idCaptor.getValue shouldBe someMessageId
      urlCaptor.getValue shouldBe someUrl
      statusCaptor.getValue shouldBe PushMessageStatus.Answer
      answerCaptor.getValue shouldBe Some("yes")
      attemptCaptor.getValue shouldBe 0

      result shouldBe true
    }

    "not save anything and return true given a valid message id and duplicate status" in new Duplicate {
      val initial: Boolean = await(service.respondToMessage(someMessageId, someStatus, someAnswer))

      initial shouldBe true

      val result: Boolean = await(service.respondToMessage(someMessageId, someStatus, someAnswer))

      result shouldBe false
    }

    "throw a not found exception given an invalid message id" in new Invalid {
      val result = intercept[NotFoundException] {
        await(service.respondToMessage(someMessageId, someStatus, someAnswer))
      }

      result.getMessage shouldBe s"message with id [$someMessageId] not found"
    }

    "throw a bad request exception given an invalid answer" in new Success {
      val result = intercept[BadRequestException] {
        await(service.respondToMessage(someMessageId, PushMessageStatus.Answer, Some("snarkle")))
      }

      result.getMessage shouldBe "invalid answer [snarkle]"
    }

    "throw a server error if the answer could not be processed" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.respondToMessage(someMessageId, someStatus, someAnswer))
      }

      result.getMessage shouldBe "Failed to save the thing"
    }
  }

  "PushMessageService getMessageFromMessageId" should {
      "return message for a given authId if no callback document has been created" in new Success {
        primeFindForMessageAndAuthId(savedMessage.messageId, "authId", Some(savedMessage))
        callbackNotCreated()
        await(service.getMessageFromMessageId(savedMessage.messageId, "authId")) shouldBe Some(somePushMessage)
    }

    PushMessageStatus.statuses.foreach { status =>
      s"not return $status message for a given authId" in new Success {
        primeFindForMessageAndAuthId(savedMessage.messageId, "authId", Some(savedMessage))
        latestMessageIsOfStatus(status)

        await(service.getMessageFromMessageId(savedMessage.messageId, "authId")).map(_.messageId) shouldBe None
      }
    }

    "return none when the message cannot be resolved" in new Success {
      primeFindForMessageAndAuthId(savedMessage.messageId, "authId", None)
      await(service.getMessageFromMessageId(savedMessage.messageId, "authId")) shouldBe None
    }
  }

  "PushMessageService getMessageStatus" should {
    "return the status from the latest callback for a given messageId" in new Success {
      latestCallbackForMessageIs(Some(PushMessageCallbackPersist(
        id = BSONObjectID.generate,
        messageId = savedMessage.messageId,
        callbackUrl = "",
        status = PushMessageStatus.Answer,
        answer = Some("no"),
        processingStatus = ProcessingStatus.Delivered,
        attempts = 0
      )))

      await(service.getMessageStatus(savedMessage.messageId)) shouldBe Some(PushMessageStatus.Answer)
    }

    "return None when there is no callback for a given messageId" in new Success {
      latestCallbackForMessageIs(None)

      await(service.getMessageStatus(savedMessage.messageId)) shouldBe None
    }
  }

}

trait GUIDUtil {
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
