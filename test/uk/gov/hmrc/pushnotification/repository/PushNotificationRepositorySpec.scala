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
import uk.gov.hmrc.pushnotification.domain.Notification
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Disabled, Queued, Sent}

import scala.concurrent.ExecutionContext.Implicits.global

class PushNotificationRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val maxRetryAttempts = 5

  val repository: PushNotificationMongoRepository = new PushNotificationMongoRepository(mongo(), maxRetryAttempts)

  trait Setup {
    val someAuthId = "some-auth-id"
    val otherAuthId = "other-auth-id"
    val someEndpoint = "foo:bar"
    val otherEndpoint = "blip:blop"
    val yetAnotherEndpoint = "wibble:wobble"
    val someMessage = "Hello world"
    val otherMessage = "Goodbye"
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PushNotificationMongoRepository indexes" should {
    "not be able to insert duplicate messageIds" in new Setup {
      val notification = Notification(someEndpoint, "Hello world")

      val actual: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      a[DatabaseException] should be thrownBy await(repository.insert(actual.right.get))
    }

    "be able to insert multiple notifications with the same authId, endpoint, and status " in new Setup {
      val notification = Notification(someEndpoint, "Hello world")

      val actual: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      await(repository.insert(actual.right.get.copy(id = BSONObjectID.generate, messageId = UUID.randomUUID().toString)))
    }
  }

  "PushNotificationMongoRepository" should {
    "persist notifications" in new Setup {
      val notification = Notification(someEndpoint, someMessage)

      val result: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      result match {
        case Right(actual) =>
          actual.authId shouldBe someAuthId
          actual.endpoint shouldBe notification.endpoint
          actual.message shouldBe notification.message
          actual.messageId shouldBe notification.messageId.get
          actual.status shouldBe notification.status
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }
    }

    "find messages with a given status" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage, status = Delivered))
        repository.save(otherAuthId, Notification(someEndpoint, otherMessage))
        repository.save(otherAuthId, Notification(otherEndpoint, otherMessage))
      }

      val result: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      result.size shouldBe 3

      result.head.authId shouldBe someAuthId
      result.head.endpoint shouldBe someEndpoint
      result.head.message shouldBe someMessage
      result.head.status shouldBe Queued
      result.head.attempts shouldBe 0

      result(1).authId shouldBe otherAuthId
      result(1).endpoint shouldBe someEndpoint
      result(1).message shouldBe otherMessage
      result(1).status shouldBe Queued
      result(1).attempts shouldBe 0

      result(2).authId shouldBe otherAuthId
      result(2).endpoint shouldBe otherEndpoint
      result(2).message shouldBe otherMessage
      result(2).status shouldBe Queued
      result(2).attempts shouldBe 0
    }

    "update existing notifications given a notification with an existing message id" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage))
        repository.save(otherAuthId, Notification(yetAnotherEndpoint, otherMessage))
      }

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val result: Either[String, NotificationPersist] = await(repository.save(existing.authId, Notification(existing.endpoint, otherMessage, Some(existing.messageId), Delivered)))

      result match {
        case Right(actual) =>
          actual.messageId shouldBe existing.messageId
          actual.message shouldBe otherMessage
          actual.status shouldBe Delivered
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }

      val stillQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      stillQueued.size shouldBe 2
    }

    "update the status of notifications" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage))
        repository.save(otherAuthId, Notification(yetAnotherEndpoint, otherMessage))
      }

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val updated: Either[String, NotificationPersist] = await(repository.update(existing.messageId, Disabled))

      updated match {
        case Right(actual) =>
          actual.messageId shouldBe existing.messageId
          actual.status shouldBe Disabled
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }

      val stillQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      stillQueued.size shouldBe 2
    }

    "not update the status of notifications given an unknown message id" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage))
      }

      val unknownId = "does-not-exist-message-id"

      val updated: Either[String, NotificationPersist] = await(repository.update(unknownId, Sent))

      updated match {
        case Right(persisted) =>
          fail(new Exception(s"should not have updated message for '${persisted.authId}'"))
        case Left(msg) =>
          msg shouldBe s"Cannot find message with id = $unknownId"
      }
    }

    "find unsent notifications, set these to 'sent' and increment attempts by 1" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage))
        repository.save(otherAuthId, Notification(yetAnotherEndpoint, otherMessage, status = Delivered))
      }

      val queued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      queued.size shouldBe 2

      val result: Seq[NotificationPersist] = await(repository.getUnsentNotifications)

      result.size shouldBe 2
      result.head.endpoint shouldBe someEndpoint
      result.head.attempts shouldBe 1
      result(1).endpoint shouldBe otherEndpoint
      result(1).attempts shouldBe 1

      val sent: Seq[NotificationPersist] = await(repository.findByStatus(Sent))
      val delivered: Seq[NotificationPersist] = await(repository.findByStatus(Delivered))

      val stillQueued: Seq[NotificationPersist] = await(repository.getUnsentNotifications)

      sent.size shouldBe 2
      delivered.size shouldBe 1
      delivered.head.attempts shouldBe 0
      stillQueued.size shouldBe 0
    }

    "not return notifications that have exceeded the maximum number of attempts" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage))
      }

      val initiallyQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      initiallyQueued.size shouldBe 2

      for (_ <- 1 to maxRetryAttempts + 1) {
        val someQueued: Seq[NotificationPersist] = await(repository.getUnsentNotifications)
        
        someQueued.forall { n =>
          await(repository.update(n.messageId, Queued)) match {
            case Right(_) => true
            case _ => false
          }
        } shouldBe true
      }

      val finallyQueued: Seq[NotificationPersist] = await(repository.getUnsentNotifications)

      finallyQueued.size shouldBe 0
    }
  }
}
