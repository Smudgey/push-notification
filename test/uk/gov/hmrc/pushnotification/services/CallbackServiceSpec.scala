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
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.http.{HttpException, ServiceUnavailableException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Answer}
import uk.gov.hmrc.pushnotification.domain._
import uk.gov.hmrc.pushnotification.repository.ProcessingStatus.Queued
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
    val someProcessingStatus = Queued
    val otherProcessingStatus = Queued
    val someAttempt = 1
    val otherAttempt = 2

    val someCallback = PushMessageCallbackPersist(BSONObjectID.generate, someMessageId, someUrl, someStatus, someAnswer, someProcessingStatus, someAttempt)
    val otherCallback = PushMessageCallbackPersist(BSONObjectID.generate, otherMessageId, otherUrl, otherStatus, otherAnswer, otherProcessingStatus, otherAttempt)

    val someCallbackResult = CallbackResult(someMessageId, someStatus, success = true)
    val otherCallbackResult = CallbackResult(otherMessageId, otherStatus, success = false)

    val updates = CallbackResultBatch(Seq(someCallbackResult, otherCallbackResult))
    val service = new CallbackService(mockRepository, maxAttempts, lockRepository)
  }

  private trait LockOK extends Setup {
    doReturn(successful(true), Nil: _*).when(lockRepository).lock(any[String](), any[String](), any[Duration]())
    doReturn(successful({}), Nil: _*).when(lockRepository).releaseLock(any[String](), any[String]())
  }

  private trait Success extends LockOK {
    doReturn(successful(Seq(someCallback, otherCallback)), Nil: _*).when(mockRepository).findUndelivered(ArgumentMatchers.any[Int]())

    doReturn(successful(Right(someCallback)), Nil: _*).when(mockRepository).update(someCallbackResult)
    doReturn(successful(Left("Something is wrong")), Nil: _*).when(mockRepository).update(otherCallbackResult)
  }

  private trait Failed extends LockOK {

    doReturn(failed(new Exception("SPLAT!")), Nil: _*).when(mockRepository).findUndelivered(ArgumentMatchers.any[Int]())

    doReturn(failed(new Exception("SPLAT!")), Nil: _*).when(mockRepository).update(any[CallbackResult]())
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
      "save the callback result details in the repository" in new Success {
        // TODO: capture arguments!

        val actualUpdates: Boolean = await(service.updateCallbacks(updates))

        actualUpdates shouldBe false
      }

      "throw a service unavailable exception given repository problems" in new Failed {
        val result = intercept[HttpException] {
          await(service.updateCallbacks(updates))
        }

        result.getMessage shouldBe s"""processGroup failed for value="${someCallbackResult.toString}""""
      }
    }
  }
}
