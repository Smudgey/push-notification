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

import org.joda.time.Duration
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doReturn, verify}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.http.ServiceUnavailableException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Answer, PermanentlyFailed}
import uk.gov.hmrc.pushnotification.domain.{Callback, CallbackBatch, PushMessageStatus}
import uk.gov.hmrc.pushnotification.repository.{CallbackRepositoryApi, PushMessageCallbackPersist}

import scala.concurrent.Future.{failed, successful}

class CallbackServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {

  private trait Setup extends MockitoSugar {
    val mockRepository: CallbackRepositoryApi = mock[CallbackRepositoryApi]
    val lockRepository: LockRepository = mock[LockRepository]

    val maxAttempts = 5
    val someMessageId = "msg-id-1"
    val otherMessageId = "msg-id-2"
    val someUrl = "http://foo/bar"
    val otherUrl = "http://baz/quux"
    val someStatus = Acknowledge
    val otherStatus = Answer
    val someAnswer = None
    val otherAnswer = Some("flurb")
    val someAttempt = 1
    val otherAttempt = 2

    val someCallback = PushMessageCallbackPersist(BSONObjectID.generate, someMessageId, someUrl, someStatus, someAnswer, someAttempt)
    val otherCallback = PushMessageCallbackPersist(BSONObjectID.generate, otherMessageId, otherUrl, otherStatus, otherAnswer, otherAttempt)

    val updates = Map(someMessageId -> true, otherMessageId -> false)

    val service = new CallbackService(mockRepository, maxAttempts, lockRepository)
  }

  private trait LockOK extends Setup {
    doReturn(successful(true), Nil: _*).when(lockRepository).lock(any[String](), any[String](), any[Duration]())
    doReturn(successful({}), Nil: _*).when(lockRepository).releaseLock(any[String](), any[String]())
  }

  private trait Success extends LockOK {
    doReturn(successful(Seq(someCallback, otherCallback)), Nil: _*).when(mockRepository).findUndelivered(ArgumentMatchers.any[Int]())

    doReturn(successful(Some(someCallback)), Nil: _*).when(mockRepository).findLatest(ArgumentMatchers.eq(someMessageId))
    doReturn(successful(Some(otherCallback)), Nil: _*).when(mockRepository).findLatest(ArgumentMatchers.eq(otherMessageId))

    doReturn(successful(Right(true)), Nil: _*).when(mockRepository).save(ArgumentMatchers.eq(someMessageId), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]())
    doReturn(successful(Left("Something is wrong")), Nil: _*).when(mockRepository).save(ArgumentMatchers.eq(otherMessageId), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]())
  }

  private trait Final extends LockOK {
    override val updates = Map(someMessageId -> false)
    override val someCallback = PushMessageCallbackPersist(BSONObjectID.generate, someMessageId, someUrl, someStatus, someAnswer, maxAttempts)

    doReturn(successful(Some(someCallback)), Nil: _*).when(mockRepository).findLatest(ArgumentMatchers.eq(someMessageId))

    doReturn(successful(Right(true)), Nil: _*).when(mockRepository).save(any[String](), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]())
  }

  private trait Failed extends LockOK {

    doReturn(failed(new Exception("SPLAT!")), Nil: _*).when(mockRepository).findUndelivered(ArgumentMatchers.any[Int]())

    doReturn(successful(Some(someCallback)), Nil: _*).when(mockRepository).findLatest(any[String]())
    doReturn(failed(new Exception("SPLAT!")), Nil: _*).when(mockRepository).save(any[String](), any[String](), any[PushMessageStatus](), any[Option[String]](), any[Int]())
  }

  "CallbackService getUndeliveredCallbacks" should {
    "return a list of callbacks when undelivered callbacks are available" in new Success {
      val result: Option[CallbackBatch] = await(service.getUndeliveredCallbacks)

      val actualCallbacks = result.getOrElse(fail("should have some callbacks")).batch

      actualCallbacks.size shouldBe 2

      actualCallbacks.head.callbackUrl shouldBe someUrl
      actualCallbacks.head.status shouldBe someStatus
      actualCallbacks.head.response.messageId shouldBe someMessageId
      actualCallbacks.head.response.answer shouldBe someAnswer
      actualCallbacks.head.attempt shouldBe someAttempt

      actualCallbacks(1).callbackUrl shouldBe otherUrl
      actualCallbacks(1).response.messageId shouldBe otherMessageId
      actualCallbacks(1).response.answer shouldBe otherAnswer
      actualCallbacks(1).attempt shouldBe otherAttempt
    }

    "throw a service unavailable exception given an issue with the repository" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.getUndeliveredCallbacks)
      }

      result.getMessage shouldBe "Unable to retrieve undelivered callbacks"
    }

    "CallbackService updateCallbacks" should {
      "save the callback details in the repository" in new Success {
        val actualUpdates: Seq[Boolean] = await(service.updateCallbacks(updates))

        actualUpdates.size shouldBe 2

        actualUpdates.head shouldBe true
        actualUpdates(1) shouldBe false
      }

      "permanently fail a callback if it has exceeded the number of retry attempts" in new Final {
        val messageIdCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        val statusCaptor: ArgumentCaptor[PushMessageStatus] = ArgumentCaptor.forClass(classOf[PushMessageStatus])
        val answerCaptor: ArgumentCaptor[Option[String]] = ArgumentCaptor.forClass(classOf[Option[String]])
        val attemptCaptor: ArgumentCaptor[Int] = ArgumentCaptor.forClass(classOf[Int])

        val result: Seq[Boolean] = await(service.updateCallbacks(updates))

        verify(mockRepository).save(messageIdCaptor.capture(), urlCaptor.capture(), statusCaptor.capture(), answerCaptor.capture(), attemptCaptor.capture())

        messageIdCaptor.getValue shouldBe someMessageId
        urlCaptor.getValue shouldBe someUrl
        statusCaptor.getValue shouldBe PermanentlyFailed
        answerCaptor.getValue shouldBe someAnswer
        attemptCaptor.getValue shouldBe maxAttempts

        result.head shouldBe true
      }

      "throw a service unavailable exception given repository problems" in new Failed {
        val result = intercept[ServiceUnavailableException] {
          await(service.updateCallbacks(updates))
        }

        result.getMessage shouldBe "failed to save callback: SPLAT!"
      }
    }
  }
}
