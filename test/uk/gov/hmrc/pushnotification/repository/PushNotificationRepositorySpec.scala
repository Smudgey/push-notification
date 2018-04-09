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

import java.util.UUID

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.domain.Notification
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, PermanentlyFailed, Queued, Sent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PushNotificationRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val maxRetryAttempts = 5

  val repository: PushNotificationMongoRepository = new PushNotificationMongoRepository(mongo(), maxRetryAttempts)

  trait Setup {
    val someMessageId = Some("msg-some-id")
    val otherMessageId = Some("msg-other-id")
    val someAuthId = "some-auth-id"
    val otherAuthId = "other-auth-id"
    val someEndpoint = "foo:bar"
    val otherEndpoint = "blip:blop"
    val yetAnotherEndpoint = "wibble:wobble"
    val someContent = "Hello world"
    val someOs = "windows"
    val otherOs = "android"
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
      val notification = Notification(messageId = someMessageId, endpoint = someEndpoint, content = "Hello world", os = someOs)

      val actual: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      a[DatabaseException] should be thrownBy await(repository.insert(actual.right.get))
    }

    "be able to insert multiple notifications with the same messageId, authId, endpoint, and status" in new Setup {
      val notification = Notification(messageId = someMessageId, endpoint = someEndpoint, content = "Hello world", os = someOs)

      val actual: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      await(repository.insert(actual.right.get.copy(id = BSONObjectID.generate, notificationId = UUID.randomUUID().toString)))
    }
  }

  "PushNotificationMongoRepository" should {
    "persist notifications that do not include a messageId" in new Setup {
      val notification = Notification(messageId = None, endpoint = someEndpoint, content = someContent, os = someOs)

      val result: Either[String, NotificationPersist] = await(repository.save(someAuthId, notification))

      result match {
        case Right(actual) =>
          actual.authId shouldBe someAuthId
          actual.endpoint shouldBe notification.endpoint
          actual.content shouldBe notification.content
          actual.notificationId shouldBe notification.notificationId.get
          actual.status shouldBe notification.status
          actual.attempts shouldBe 0
          actual.messageId shouldBe None
          actual.os shouldBe someOs
        case Left(e) => fail(e)
      }
    }

      "persist notifications that do include a messageId" in new Setup {
        val notification = Notification(messageId = otherMessageId, endpoint = otherEndpoint, content = otherContent, os = otherOs)

        val result: Either[String, NotificationPersist] = await(repository.save(otherAuthId, notification))

        result match {
          case Right(actual) =>
            actual.authId shouldBe otherAuthId
            actual.endpoint shouldBe notification.endpoint
            actual.content shouldBe notification.content
            actual.notificationId shouldBe notification.notificationId.get
            actual.status shouldBe notification.status
            actual.attempts shouldBe 0
            actual.messageId shouldBe notification.messageId
            actual.os shouldBe otherOs
          case Left(e) => fail(e)
        }

    }

    "find notifications with a given status" in new Setup {
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, os = someOs, status = Delivered)))
      await(repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = someEndpoint, content = otherContent, os = someOs)))
      await(repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = otherEndpoint, content = otherContent, os = someOs)))

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
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, os = someOs)))
      await(repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent, os = someOs)))

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val result: Either[String, NotificationPersist] = await(repository.save(existing.authId, Notification(messageId = someMessageId, endpoint = existing.endpoint, content = otherContent, notificationId = Some(existing.notificationId), status = Delivered, os = someOs)))

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
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, os = someOs)))
      await(repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent, os = someOs)))

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val updated: Either[String, NotificationPersist] = await(repository.update(existing.notificationId, PermanentlyFailed))

      updated match {
        case Right(actual) =>
          actual.notificationId shouldBe existing.notificationId
          actual.status shouldBe PermanentlyFailed
          actual.attempts shouldBe 0
        case Left(e) => fail(e)
      }

      val stillQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      stillQueued.size shouldBe 2
    }

    "not update the status of notifications given an unknown notification id" in new Setup {
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))

      val unknownId = "does-not-exist-notification-id"

      val updated: Either[String, NotificationPersist] = await(repository.update(unknownId, Sent))

      updated match {
        case Right(persisted) =>
          fail(new Exception(s"should not have updated notification for '${persisted.authId}'"))
        case Left(msg) =>
          msg shouldBe s"Cannot find notification with id = $unknownId"
      }
    }

    "find queued notifications (oldest first), set these to 'sent' and increment attempts by 1" in new Setup {
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, os = someOs)))
      await(repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent, os = someOs, status = Delivered)))

      val queued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      queued.size shouldBe 2

      val result: Seq[NotificationPersist] = await(repository.getQueuedNotifications(100))

      result.size shouldBe 2

      val some: NotificationPersist = result.head
      val other: NotificationPersist = result(1)

      some.endpoint shouldBe someEndpoint
      some.attempts shouldBe 1
      other.endpoint shouldBe otherEndpoint
      other.attempts shouldBe 1

      val sent: Seq[NotificationPersist] = await(repository.findByStatus(Sent))
      val delivered: Seq[NotificationPersist] = await(repository.findByStatus(Delivered))
      val stillQueued: Seq[NotificationPersist] = await(repository.getQueuedNotifications(100))

      sent.size shouldBe 2
      delivered.size shouldBe 1
      delivered.head.attempts shouldBe 0
      stillQueued.size shouldBe 0
    }

    "find notifications (oldest first) that are still in the Sent state after a timeout period has expired" in new Setup {
      await(repository.save(otherAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = otherContent, os = someOs, status = Sent)))
      await(repository.save(otherAuthId, Notification(messageId = otherMessageId, endpoint = yetAnotherEndpoint, content = otherContent, os = someOs, status = Sent)))

      val sent: Seq[NotificationPersist] = await(repository.findByStatus(Sent))

      sent.size shouldBe 2

      Thread sleep 100

      val result: Seq[NotificationPersist] = await(repository.getTimedOutNotifications(25, 100))

      result.size shouldBe 2

      result.head.messageId shouldBe someMessageId
      result(1).messageId shouldBe otherMessageId
    }

    "not return notifications that have exceeded the maximum number of attempts" in new Setup {
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, os = someOs)))

      val initiallyQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      initiallyQueued.size shouldBe 2

      for (_ <- 1 to maxRetryAttempts + 1) {
        val someQueued: Seq[NotificationPersist] = await(repository.getQueuedNotifications(100))

        someQueued.forall { n =>
          await(repository.update(n.notificationId, Queued)) match {
            case Right(_) => true
            case _ => false
          }
        } shouldBe true
      }

      val finallyQueued: Seq[NotificationPersist] = await(repository.getQueuedNotifications(100))

      finallyQueued.size shouldBe 0
    }

    "permanently fail notifications that have exceeded the maximum number of attempts" in new Setup {
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))
      await(repository.save(someAuthId, Notification(messageId = someMessageId, endpoint = otherEndpoint, content = someContent, os = someOs)))

      val initiallyQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      initiallyQueued.size shouldBe 2

      for (_ <- 1 to maxRetryAttempts + 1) {
        val someQueued: Seq[NotificationPersist] = await(repository.getQueuedNotifications(100))

        someQueued.forall { n =>
          await(repository.update(n.notificationId, Queued)) match {
            case Right(_) => true
            case _ => false
          }
        } shouldBe true
      }

      val permanentlyFailed: Option[Int] = await(repository.permanentlyFail())

      val remainingFailed: Option[Int] = await(repository.permanentlyFail())

      permanentlyFailed shouldBe Some(2)

      remainingFailed shouldBe None
    }

    "return only max-limit number of notifications when there are more than max-limit queued notifications" in new Setup {
      val someLimit = 10

      await(Future.sequence((1 to someLimit + 1).map(i => repository.save(s"authId-$i",Notification(messageId = someMessageId, endpoint = someEndpoint, content = someContent, os = someOs)))))

      val allSaved: List[NotificationPersist] = await(repository.findAll())

      allSaved.size should be > someLimit

      val result = await(repository.getQueuedNotifications(someLimit))

      result.size shouldBe someLimit
    }
  }
}
