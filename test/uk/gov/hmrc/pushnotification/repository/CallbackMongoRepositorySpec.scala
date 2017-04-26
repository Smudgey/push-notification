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

package uk.gov.hmrc.pushnotification.repository

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Acknowledged, Answer}

import scala.concurrent.ExecutionContext.Implicits.global

class CallbackMongoRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val repository: CallbackMongoRepository = new CallbackMongoRepository(mongo())

  trait Setup {
    val someMessageId = "msg-some-id"
    val otherMessageId = "msg-other-id"
    val someUrl = "https//example.com/foo"
    val otherUrl = "https//example.com/bar"
    val someMessageStatus = Acknowledge
    val otherMessageStatus = Answer
    val someAnswer = Some("yes")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "CallbackMongoRepository indexes" should {
    "not be able to insert duplicate messageId/status combinations" in new Setup {
      val saved = PushMessageCallbackPersist(BSONObjectID.generate, someMessageId, someUrl, someMessageStatus, None)

      await(repository.insert(saved))

      a[DatabaseException] should be thrownBy await(repository.insert(saved.copy(id = BSONObjectID.generate, callbackUrl = otherUrl)))
    }

    "be able to insert multiple messages with the same messageId but different statuses" in new Setup {
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
          await(repository.save(someMessageId, someUrl, Acknowledged, None)),
          await(repository.save(otherMessageId, otherUrl, Answer, someAnswer)),
          await(repository.save(otherMessageId, otherUrl, Acknowledged, None)),
          await(repository.save(otherMessageId, otherUrl, Acknowledge, None))
        )

      saved.count(_.isRight) shouldBe 5

      val someResult: Option[PushMessageCallbackPersist] = await(repository.findLatest(someMessageId))

      val someActual: PushMessageCallbackPersist = someResult.getOrElse(fail("should have found a callback status"))

      someActual.messageId shouldBe someMessageId
      someActual.callbackUrl shouldBe someUrl
      someActual.status shouldBe Acknowledged
      someActual.answer shouldBe None

      val otherResult: Option[PushMessageCallbackPersist] = await(repository.findLatest(otherMessageId))

      val otherActual: PushMessageCallbackPersist = otherResult.getOrElse(fail("should have found a callback status"))

      otherActual.messageId shouldBe otherMessageId
      otherActual.callbackUrl shouldBe otherUrl
      otherActual.status shouldBe Answer
      otherActual.answer shouldBe someAnswer
    }

    "not find a status given a non-existent message id" in new Setup {
      val saved: Seq[Either[String, Boolean]] =
        Seq(
          await(repository.save(someMessageId, someUrl, Acknowledge, None)),
          await(repository.save(someMessageId, someUrl, Acknowledged, None)),
          await(repository.save(otherMessageId, otherUrl, Answer, someAnswer))
        )

      saved.count(_.isRight) shouldBe 3

      val result: Option[PushMessageCallbackPersist] = await(repository.findLatest("does-not-exist-message-id"))

      result.isDefined shouldBe false
    }
  }

}
