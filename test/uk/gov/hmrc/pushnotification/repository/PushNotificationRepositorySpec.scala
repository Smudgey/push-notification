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
    val someMessageId = "msg-some-id"
    val otherMessageId = "msg-other-id"
    val someAuthId = "some-auth-id"
    val otherAuthId = "other-auth-id"
    val someEndpoint = "foo:bar"
    val otherEndpoint = "blip:blop"
    val yetAnotherEndpoint = "wibble:wobble"
    val someContent = "Hello world"
    val otherContent = "Goodbye"
    val someUrl = Some("http://snarkle.internal/foo/bar")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "PushNotificationMongoRepository indexes" should {
    "not be able to insert duplicate notificationIds" in new Setup {
      val notification = Notification(messageId = someMessageId, endpoint = someEndpoint, content = "Hello world")

      val actual: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      a[DatabaseException] should be thrownBy await(repository.insert(actual.right.get))
    }

    "be able to insert multiple notifications with the same messageId, authId, endpoint, status, and callbackUrl" in new Setup {
      val notification = Notification(messageId = someMessageId, endpoint = someEndpoint, content = "Hello world", callbackUrl = someUrl)

      val actual: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      await(repository.insert(actual.right.get.copy(id = BSONObjectID.generate, notificationId = UUID.randomUUID().toString)))
    }
  }

  "PushNotificationMongoRepository" should {
    "persist notifications" in new Setup {
      val notification = Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent)

      val result: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      result match {
        case Right(actual) =>
          actual.authId shouldBe someAuthId
          actual.endpoint shouldBe notification.endpoint
          actual.content shouldBe notification.content
          actual.notificationId shouldBe notification.notificationId.get
          actual.status shouldBe notification.status
          actual.callbackUrl shouldBe notification.callbackUrl
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }
    }

    "find notifications with a given status" in new Setup {
      await {
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent))
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, status = Delivered))
        repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = someEndpoint, content = otherContent))
        repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = otherEndpoint, content = otherContent))
      }

      val result: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      result.size shouldBe 3

      result.head.authId shouldBe someAuthId
      result.head.endpoint shouldBe someEndpoint
      result.head.content shouldBe someContent
      result.head.status shouldBe Queued
      result.head.attempts shouldBe 0

      result(1).authId shouldBe otherAuthId
      result(1).endpoint shouldBe someEndpoint
      result(1).content shouldBe otherContent
      result(1).status shouldBe Queued
      result(1).attempts shouldBe 0

      result(2).authId shouldBe otherAuthId
      result(2).endpoint shouldBe otherEndpoint
      result(2).content shouldBe otherContent
      result(2).status shouldBe Queued
      result(2).attempts shouldBe 0
    }

    "update existing notifications given a notification with an existing notification id" in new Setup {
      await {
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent))
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent))
        repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent))
      }

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val result: Either[String, NotificationPersist] = await(repository.save(existing.authId, Notification(messageId = someMessageId, endpoint = existing.endpoint, content = otherContent, notificationId = Some(existing.notificationId), status = Delivered)))

      result match {
        case Right(actual) =>
          actual.notificationId shouldBe existing.notificationId
          actual.content shouldBe otherContent
          actual.status shouldBe Delivered
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }

      val stillQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      stillQueued.size shouldBe 2
    }

    "update the status of notifications" in new Setup {
      await {
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent))
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent))
        repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent, callbackUrl = someUrl))
      }

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val updated: Either[String, NotificationPersist] = await(repository.update(existing.notificationId, Disabled))

      updated match {
        case Right(actual) =>
          actual.notificationId shouldBe existing.notificationId
          actual.status shouldBe Disabled
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }

      val stillQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      stillQueued.size shouldBe 2
    }

    "not update the status of notifications given an unknown notification id" in new Setup {
      await {
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent))
      }

      val unknownId = "does-not-exist-notification-id"

      val updated: Either[String, NotificationPersist] = await(repository.update(unknownId, Sent))

      updated match {
        case Right(persisted) =>
          fail(new Exception(s"should not have updated notification for '${persisted.authId}'"))
        case Left(msg) =>
          msg shouldBe s"Cannot find notification with id = $unknownId"
      }
    }

    "find queued notifications, set these to 'sent' and increment attempts by 1" in new Setup {
      await {
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent))
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent))
        repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent, status = Delivered))
      }

      val queued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      queued.size shouldBe 2

      val result: Seq[NotificationPersist] = await(repository.getUnsentNotifications)

      result.size shouldBe 2

      val some: NotificationPersist = result.filter(_.endpoint == someEndpoint).head
      val other: NotificationPersist = result.filter(_.endpoint == otherEndpoint).head

      some.attempts shouldBe 1
      other.attempts shouldBe 1

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
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent))
        repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent))
      }

      val initiallyQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      initiallyQueued.size shouldBe 2

      for (_ <- 1 to maxRetryAttempts + 1) {
        val someQueued: Seq[NotificationPersist] = await(repository.getUnsentNotifications)
        
        someQueued.forall { n =>
          await(repository.update(n.notificationId, Queued)) match {
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
