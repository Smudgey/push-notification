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
import uk.gov.hmrc.mongo.{DatabaseUpdate, MongoSpecSupport, Saved, Updated}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotification.domain.Notification
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Queued}

import scala.concurrent.ExecutionContext.Implicits.global

class PushNotificationRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with LoneElement with Eventually {

  val repository: PushNotificationMongoRepository = new PushNotificationMongoRepository

  trait Setup {
    val someAuthId = "some-auth-id"
    val otherAuthId = "other-auth-id"
    val someEndpoint = "foo:bar"
    val otherEndpoint = "blip:blop"
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
      val notification = Notification(someEndpoint, "Hello world", status = Queued)

      val actual: DatabaseUpdate[NotificationPersist] = await(repository.save(someAuthId, notification))

      a[DatabaseException] should be thrownBy await(repository.insert(actual.updateType.savedValue))
    }

    "be able to insert multiple notifications with the same authId, endpoint, and status " in new Setup {
      val notification = Notification(someEndpoint, "Hello world", status = Queued)

      val actual: DatabaseUpdate[NotificationPersist] = await(repository.save(someAuthId, notification))

      await(repository.insert(actual.updateType.savedValue.copy(id = BSONObjectID.generate, messageId = UUID.randomUUID().toString)))
    }
  }

  "PushNotificationMongoRepository" should {
    "persist notifications" in new Setup {
      val notification = Notification(someEndpoint, someMessage, status = Queued)

      val result: DatabaseUpdate[NotificationPersist] = await(repository.save(someAuthId, notification))

      result.updateType shouldBe an[Saved[_]]
      result.updateType.savedValue.authId shouldBe someAuthId
      result.updateType.savedValue.endpoint shouldBe notification.endpoint
      result.updateType.savedValue.message shouldBe notification.message
      result.updateType.savedValue.messageId shouldBe notification.messageId.get
      result.updateType.savedValue.status shouldBe notification.status
    }

    "find messages with a given status" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage, status = Queued))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage, status = Delivered))
        repository.save(otherAuthId, Notification(someEndpoint, otherMessage, status = Queued))
        repository.save(otherAuthId, Notification(otherEndpoint, otherMessage, status = Queued))
      }

      val result: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      result.size shouldBe 3

      result.head.authId shouldBe someAuthId
      result.head.endpoint shouldBe someEndpoint
      result.head.message shouldBe someMessage
      result.head.status shouldBe Queued

      result(1).authId shouldBe otherAuthId
      result(1).endpoint shouldBe someEndpoint
      result(1).message shouldBe otherMessage
      result(1).status shouldBe Queued

      result(2).authId shouldBe otherAuthId
      result(2).endpoint shouldBe otherEndpoint
      result(2).message shouldBe otherMessage
      result(2).status shouldBe Queued
    }

    "update the status of existing notifications" in new Setup {
      await {
        repository.save(someAuthId, Notification(someEndpoint, someMessage, status = Queued))
        repository.save(someAuthId, Notification(otherEndpoint, someMessage, status = Queued))
        repository.save(otherAuthId, Notification(someEndpoint, otherMessage, status = Queued))
      }

      val saved: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      saved.size shouldBe 3

      val existing: NotificationPersist = saved(1)

      val result: DatabaseUpdate[NotificationPersist] = await(repository.save(existing.authId, Notification(existing.endpoint, existing.message, Some(existing.messageId),Delivered)))

      result.updateType shouldBe an[Updated[_]]
      result.updateType.savedValue.messageId shouldBe existing.messageId
      result.updateType.savedValue.status shouldBe Delivered

      val stillQueued: Seq[NotificationPersist] = await(repository.findByStatus(Queued))

      stillQueued.size shouldBe 2
    }
  }
}
