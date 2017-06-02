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

import java.util.UUID

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.domain.PushMessage

import scala.concurrent.ExecutionContext.Implicits.global

class PushMessageMongoRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val repository: PushMessageMongoRepository = new PushMessageMongoRepository(mongo())

  trait Setup {
    val someAuthId = "some-auth-id"
    val otherAuthId = "other-auth-id"

    val someMessageId = "msg-some-id"
    val someSubject = "You need to authorise your agent"
    val someBody = "You are required to authorise your agent, Dodger & Pretend Accountants."
    val someResponses = Map("Yes, fine" -> "Yes", "No, thanks" -> "No")
    val someUrl = "http://snarkle.internal/foo/bar"

    val otherMessageId = "msg-other-id"
    val otherSubject = "Your agent has been authorised"
    val otherBody = "Your agent, Dodger & Pretend Accountants, is now authorised to act on your behalf."
    val otherResponses: Map[String, String] = Map.empty
    val otherUrl = "http://snarkle.internal/quux/grault"
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PushMessageMongoRepository indexes" should {
    "not be able to insert duplicate messageIds" in new Setup {
      val message = PushMessage(messageId = someMessageId, subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)

      val actual: Either[String, PushMessagePersist] = await(repository.save(someAuthId, message))

      a[DatabaseException] should be thrownBy await(repository.insert(actual.right.get))
    }

    "be able to insert multiple messages with the same authId, subject, body, responses, and callbackUrl" in new Setup {
      val message = PushMessage(messageId = someMessageId, subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)

      val actual: Either[String, PushMessagePersist] = await(repository.save(someAuthId, message))

      await(repository.insert(actual.right.get.copy(id = BSONObjectID.generate, messageId = UUID.randomUUID().toString)))
    }
  }

  "PushMessageMongoRepository" should {
    "persist messages that include responses" in new Setup {
      val message = PushMessage(messageId = someMessageId, subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)

      val result: Either[String, PushMessagePersist] = await(repository.save(someAuthId, message))

      result match {
        case Right(actual) =>
          actual.authId shouldBe someAuthId
          actual.messageId shouldBe message.messageId
          actual.subject shouldBe message.subject
          actual.body shouldBe message.body
          actual.callbackUrl shouldBe message.callbackUrl
          actual.responses shouldBe message.responses
        case Left(e) => fail(e)
      }
    }

    "persist messages that do not include responses" in new Setup {
      val message = PushMessage(messageId = otherMessageId, subject = otherSubject, body = otherBody, callbackUrl = otherUrl, responses = otherResponses)

      val result: Either[String, PushMessagePersist] = await(repository.save(otherAuthId, message))

      result match {
        case Right(actual) =>
          actual.authId shouldBe otherAuthId
          actual.messageId shouldBe message.messageId
          actual.subject shouldBe message.subject
          actual.body shouldBe message.body
          actual.callbackUrl shouldBe message.callbackUrl
          actual.responses shouldBe Map.empty
        case Left(e) => fail(e)
      }
    }

    "find a message given a message id" in new Setup {
      val saved: Seq[Either[String, PushMessagePersist]] =
        Seq(
          await(repository.save(someAuthId, PushMessage(subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses))),
          await(repository.save(otherAuthId, PushMessage(subject = someSubject, body = someBody, callbackUrl = otherUrl, responses = someResponses))),
          await(repository.save(someAuthId, PushMessage(subject = otherSubject, body = otherBody, callbackUrl = someUrl, responses = otherResponses)))
        )

      saved.count(_.isRight) shouldBe 3

      val savedMessage = saved(1).right.get

      val result = await(repository.find(savedMessage.messageId, None))

      val foundMessage = result.getOrElse(fail("should have found the saved message"))

      foundMessage.authId shouldBe savedMessage.authId
      foundMessage.messageId shouldBe savedMessage.messageId
      foundMessage.subject shouldBe savedMessage.subject
      foundMessage.body shouldBe savedMessage.body
      foundMessage.callbackUrl shouldBe savedMessage.callbackUrl
      foundMessage.responses shouldBe savedMessage.responses
    }

    "find a message given a message id and auth id" in new Setup {
      val saved: Seq[Either[String, PushMessagePersist]] =
        Seq(
          await(repository.save(someAuthId, PushMessage(subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses))),
          await(repository.save(otherAuthId, PushMessage(subject = someSubject, body = someBody, callbackUrl = otherUrl, responses = someResponses))),
          await(repository.save(someAuthId, PushMessage(subject = otherSubject, body = otherBody, callbackUrl = someUrl, responses = otherResponses)))
        )

      saved.count(_.isRight) shouldBe 3

      val savedMessage = saved(1).right.get

      val result = await(repository.find(savedMessage.messageId, Some(otherAuthId)))

      val foundMessage = result.getOrElse(fail("should have found the saved message"))

      foundMessage.authId shouldBe savedMessage.authId
      foundMessage.messageId shouldBe savedMessage.messageId
      foundMessage.subject shouldBe savedMessage.subject
      foundMessage.body shouldBe savedMessage.body
      foundMessage.callbackUrl shouldBe savedMessage.callbackUrl
      foundMessage.responses shouldBe savedMessage.responses
    }

    "not find a message given a non-existent message id" in new Setup {
      val saved: Seq[Either[String, PushMessagePersist]] =
        Seq(
          await(repository.save(someAuthId, PushMessage(subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses))),
          await(repository.save(someAuthId, PushMessage(subject = otherSubject, body = otherBody, callbackUrl = otherUrl, responses = otherResponses))),
          await(repository.save(otherAuthId, PushMessage(subject = otherSubject, body = otherBody, callbackUrl = someUrl, responses = otherResponses)))
        )

      saved.count(_.isRight) shouldBe 3

      val result = await(repository.find("does-not-exist-message-id", None))

      result.isDefined shouldBe false
    }

    "find messages for a give authId" in new Setup {
      val pushMessage1 = PushMessage(subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)
      await(repository.save(someAuthId, pushMessage1))
      val pushMessage2 = PushMessage(subject = otherSubject, body = otherBody, callbackUrl = otherUrl, responses = otherResponses)
      await(repository.save(someAuthId, pushMessage2))

      await(repository.findByAuthority(someAuthId)).map(_.subject) shouldBe Seq(someSubject, otherSubject)
    }
  }
}
