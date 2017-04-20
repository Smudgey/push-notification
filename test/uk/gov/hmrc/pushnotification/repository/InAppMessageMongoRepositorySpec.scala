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
import uk.gov.hmrc.pushnotification.domain.Message

import scala.concurrent.ExecutionContext.Implicits.global

class InAppMessageMongoRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val repository: InAppMessageMongoRepository = new InAppMessageMongoRepository(mongo())

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

  "InAppMessageMongoRepository indexes" should {
    "not be able to insert duplicate messageIds" in new Setup {
      val message = Message(messageId = someMessageId, subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)

      val actual: Either[String, MessagePersist] = await(repository.save(someAuthId, message))

      a[DatabaseException] should be thrownBy await(repository.insert(actual.right.get))
    }

    "be able to insert multiple messages with the same authId, subject, body, responses, and callbackUrl" in new Setup {
      val message = Message(messageId = someMessageId, subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)

      val actual: Either[String, MessagePersist] = await(repository.save(someAuthId, message))

      await(repository.insert(actual.right.get.copy(id = BSONObjectID.generate, messageId = UUID.randomUUID().toString)))
    }
  }

  "InAppMessageMongoRepository" should {
    "persist messages that include responses" in new Setup {
      val message = Message(messageId = someMessageId, subject = someSubject, body = someBody, callbackUrl = someUrl, responses = someResponses)

      val result: Either[String, MessagePersist] = await(repository.save(someAuthId, message))

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
      val message = Message(messageId = otherMessageId, subject = otherSubject, body = otherBody, callbackUrl = otherUrl, responses = otherResponses)

      val result: Either[String, MessagePersist] = await(repository.save(otherAuthId, message))

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
  }
}
