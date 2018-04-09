/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.pushnotification.repository

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus._
import uk.gov.hmrc.pushnotification.domain.{CallbackResult, PushMessageStatus}
import uk.gov.hmrc.pushnotification.repository.ProcessingStatus.Queued

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CallbackMongoRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val maxRetryAttempts = 5

  val repository: CallbackMongoRepository = new CallbackMongoRepository(mongo(), maxRetryAttempts)

  trait Setup {
    val maxRows = 10
    val someMessageId = "msg-some-id"
    val otherMessageId = "msg-other-id"
    val someUrl = "https//example.com/foo"
    val otherUrl = "https//example.com/bar"
    val someMessageStatus = Acknowledge
    val otherMessageStatus = PushMessageStatus.Answer
    val someAnswer = Some("yes")
    val otherAnswer = Some("no")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "CallbackMongoRepository indexes" should {
    "not be able to insert duplicate messageId/status/attempt combinations" in new Setup {
      val saved = PushMessageCallbackPersist(BSONObjectID.generate, someMessageId, someUrl, someMessageStatus, None, Queued, 0)

      await(repository.insert(saved))

      a[DatabaseException] should be thrownBy await(repository.insert(saved.copy(id = BSONObjectID.generate, callbackUrl = otherUrl)))
    }

    "be able to insert multiple messages with the same messageId and attempt but different statuses" in new Setup {
      await(repository.save(someMessageId, someUrl, someMessageStatus, None))

      val actual: Either[String, Boolean] = await(repository.save(someMessageId, someUrl, otherMessageStatus, someAnswer))

      actual.isRight shouldBe true
      actual.right.get shouldBe true
    }
  }

  "CallbackMongoRepository" should {

    "persist callbacks that include an answer" in new Setup {
      val result: Either[String, Boolean] = await(repository.save(someMessageId, someUrl, someMessageStatus, Some("answer")))

      result.isRight shouldBe true
      result.right.get shouldBe true
    }

    "persist callbacks that do not include an answer" in new Setup {
      val result: Either[String, Boolean] = await(repository.save(someMessageId, someUrl, someMessageStatus, None))

      result.isRight shouldBe true
      result.right.get shouldBe true
    }

    "return false given a callback that was previously saved" in new Setup {
      val initial: Either[String, Boolean] = await(repository.save(someMessageId, someUrl, someMessageStatus, someAnswer))

      initial.isRight shouldBe true
      initial.right.get shouldBe true

      val result: Either[String, Boolean] = await(repository.save(someMessageId, someUrl, someMessageStatus, someAnswer))

      result.isRight shouldBe true
      result.right.get shouldBe false
    }

    "find the latest status and answer given a message id" in new Setup {
      val saved: Seq[Either[String, Boolean]] =
        Seq(
          await(repository.save(someMessageId, someUrl, Acknowledge, None)),
          await(repository.save(someMessageId, someUrl, PushMessageStatus.Answer, None)),
          await(repository.save(otherMessageId, otherUrl, PermanentlyFailed, someAnswer)),
          await(repository.save(otherMessageId, otherUrl, PushMessageStatus.Answer, None)),
          await(repository.save(otherMessageId, otherUrl, Acknowledge, None))
        )

      saved.count(_.isRight) shouldBe 5

      val someResult: Option[PushMessageCallbackPersist] = await(repository.findLatest(someMessageId))

      val someActual: PushMessageCallbackPersist = someResult.getOrElse(fail("should have found a callback status"))

      someActual.messageId shouldBe someMessageId
      someActual.callbackUrl shouldBe someUrl
      someActual.status shouldBe PushMessageStatus.Answer
      someActual.answer shouldBe None

      val otherResult: Option[PushMessageCallbackPersist] = await(repository.findLatest(otherMessageId))

      val otherActual: PushMessageCallbackPersist = otherResult.getOrElse(fail("should have found a callback status"))

      otherActual.messageId shouldBe otherMessageId
      otherActual.callbackUrl shouldBe otherUrl
      otherActual.status shouldBe PermanentlyFailed
      otherActual.answer shouldBe someAnswer
    }

    "not find a status given a non-existent message id" in new Setup {
      val saved: Seq[Either[String, Boolean]] =
        Seq(
          await(repository.save(someMessageId, someUrl, Acknowledge, None)),
          await(repository.save(someMessageId, someUrl, PushMessageStatus.Answer, None)),
          await(repository.save(otherMessageId, otherUrl, Timeout, someAnswer))
        )

      saved.count(_.isRight) shouldBe 3

      val result: Option[PushMessageCallbackPersist] = await(repository.findLatest("does-not-exist-message-id"))
      result shouldBe None
    }

    "find a callback with a specific messageId and status" in new Setup {
      val saved: Seq[Either[String, Boolean]] =
        Seq(
          await(repository.save(someMessageId, someUrl, Acknowledge, None)),
          await(repository.save(someMessageId, someUrl, PushMessageStatus.Answer, None)),
          await(repository.save(otherMessageId, otherUrl, PermanentlyFailed, someAnswer)),
          await(repository.save(otherMessageId, otherUrl, PushMessageStatus.Answer, otherAnswer)),
          await(repository.save(otherMessageId, otherUrl, Acknowledge, None))
        )

      saved.count(_.isRight) shouldBe 5

      val found: Option[PushMessageCallbackPersist] = await(repository.findByStatus(otherMessageId, PushMessageStatus.Answer))

      val actual: PushMessageCallbackPersist = found.getOrElse(fail("should have been found"))

      actual.messageId shouldBe otherMessageId
      actual.status shouldBe PushMessageStatus.Answer
      actual.answer shouldBe otherAnswer
    }

    "find undelivered callbacks (oldest first) that have not exceeded the maximum number of attempts, and increase the number of attempts" in new Setup {
      val initialState: Seq[Either[String, Boolean]] =
        Seq(
          await(repository.save(someMessageId, someUrl, Acknowledge, None, 1)),
          await(repository.save(otherMessageId, someUrl, Acknowledge, None, 2)),
          await(repository.save(someMessageId, someUrl, PushMessageStatus.Answer, someAnswer, 3)),
          await(repository.save(otherMessageId, someUrl, PushMessageStatus.Answer, someAnswer, 3)),
          await(repository.save(someMessageId, otherUrl, Timeout, None, 4)),
          await(repository.save(otherMessageId, otherUrl, Timeout, None, 5)),
          await(repository.save(someMessageId, otherUrl, PermanentlyFailed, None, 5))
        )

      initialState.count(_.isRight) shouldBe 7

      val first: Seq[PushMessageCallbackPersist] = await(repository.findUndelivered(maxRows))

      first.size shouldBe 5 // because 2 will have exceeded maximum retry attempts

      first.head.status shouldBe Acknowledge
      first(4).status shouldBe Timeout

      first.count(_.attempts == 1) shouldBe 0
      first.count(_.attempts == 2) shouldBe 1
      first.count(_.attempts == 3) shouldBe 1
      first.count(_.attempts == 4) shouldBe 2
      first.count(_.attempts == 5) shouldBe 1
      first.count(_.attempts == 6) shouldBe 0

      val second: Seq[PushMessageCallbackPersist] = await(repository.findUndelivered(maxRows))

      second.size shouldBe 0
    }

    "return only max-limit number of callbacks when there are more than max-limit unprocessed callbacks" in new Setup {
      val someLimit = 10

      await {
        Future.sequence((1 to someLimit + 1).map(i => repository.save(s"msg-id-$i", someUrl, Acknowledge, None)))
      }

      val allSaved: List[PushMessageCallbackPersist] = await(repository.findAll())

      allSaved.size should be > someLimit

      val result = await(repository.findUndelivered(someLimit))

      result.size shouldBe someLimit
    }

    "update the processing status of existing callbacks and re-queue failed callback processing results" in new Setup {
      val initialState: Seq[Either[String, Boolean]] =
        Seq(
          await(repository.save(someMessageId, someUrl, someMessageStatus, None)),
          await(repository.save(otherMessageId, someUrl, otherMessageStatus, None))
        )

      initialState.count(_.isRight) shouldBe 2

      val initial: Seq[PushMessageCallbackPersist] = await(repository.findUndelivered(maxRows))

      initial.size shouldBe 2

      val processingFailed: Either[String, PushMessageCallbackPersist] = await(repository.update(CallbackResult(someMessageId, someMessageStatus, success = false)))

      processingFailed match {
        case Right(actual) =>
          actual.messageId shouldBe someMessageId
          actual.processingStatus shouldBe Queued
          actual.attempts shouldBe 1
        case Left(e) => fail(e)
      }

      val processingSuccess: Either[String, PushMessageCallbackPersist] = await(repository.update(CallbackResult(otherMessageId, otherMessageStatus, success = true)))

      processingSuccess.isRight shouldBe true

      val afterUpdate: Seq[PushMessageCallbackPersist] = await(repository.findUndelivered(maxRows))

      afterUpdate.size shouldBe 1

      afterUpdate.head.messageId shouldBe someMessageId
      afterUpdate.head.attempts shouldBe 2
    }

    "not update the processing status of callbacks given a non-existent message-id and status combination" in new Setup {
      await(repository.save(someMessageId, someUrl, someMessageStatus, None))

      val updated: Either[String, PushMessageCallbackPersist] = await(repository.update(CallbackResult(someMessageId, otherMessageStatus, success = true)))

      updated match {
        case Right(_) =>
          fail(new Exception(s"should not have updated callback!"))
        case Left(msg) =>
          msg shouldBe s"Cannot find callback with message-id = $someMessageId and status = $otherMessageStatus"
      }
    }
  }
}
