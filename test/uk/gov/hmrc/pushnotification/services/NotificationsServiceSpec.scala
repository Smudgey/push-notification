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

package uk.gov.hmrc.pushnotification.services

import org.joda.time.Duration
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.http._
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Queued, Sent}
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationResult}
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationRepositoryApi}

import scala.concurrent.Future.{failed, successful}

class NotificationsServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {

  private trait Setup extends MockitoSugar {
    val notificationRepository: PushNotificationRepositoryApi = mock[PushNotificationRepositoryApi]
    val lockRepository: LockRepository = mock[LockRepository]

    val service = new NotificationsService(notificationRepository, lockRepository, 100, 60000L)

    val someMessageId = "msg-id-abcd-1234"
    val someAuthId = "bob-id"
    val someEndpoint = "foo:bar:baz"
    val otherEndpoint = "wibble:wubble:wobble"
    val someContent = "a very important message"
    val someOs = "windows"
    val someNotificationId = "msg-id-1"
    val otherNotificationId = "msg-id-2"

    val somePersisted = NotificationPersist(BSONObjectID.generate, someNotificationId, Some(someMessageId), someAuthId, someEndpoint, someContent, someOs, Sent, 1)
    val otherPersisted = NotificationPersist(BSONObjectID.generate, otherNotificationId, Some(someMessageId), someAuthId, otherEndpoint, someContent, someOs, Sent, 2)

    val updates = Map(someNotificationId -> Delivered, otherNotificationId -> Queued)
  }

  private trait LockOK extends Setup {
    when(lockRepository.lock(any[String](), any[String](), any[Duration]())).thenReturn(successful(true))
    when(lockRepository.releaseLock(any[String](), any[String]())).thenReturn(successful({}))
  }

  private trait Success extends LockOK {
    when(notificationRepository.getQueuedNotifications(any[Int]())).thenReturn(successful(Seq(somePersisted, otherPersisted)))
    when(notificationRepository.permanentlyFail()).thenReturn(successful(Some(5)))
    when(notificationRepository.getTimedOutNotifications(any[Long](), any[Int]())).thenReturn(successful(Seq(otherPersisted, somePersisted)))
    when(notificationRepository.update(ArgumentMatchers.eq(NotificationResult(someNotificationId, Delivered)))).thenReturn(successful(Right(somePersisted)))
    when(notificationRepository.update(ArgumentMatchers.eq(NotificationResult(otherNotificationId, Queued)))).thenReturn(successful(Left("KABOOM!")))
  }

  private trait Failed extends LockOK {
    when(notificationRepository.permanentlyFail()).thenReturn(successful(None))
    when(notificationRepository.getQueuedNotifications(any[Int]())).thenReturn(failed(new Exception("KAPOW!")))
    when(notificationRepository.getTimedOutNotifications(any[Long](), any[Int]())).thenReturn(failed(new Exception("SPLAT!")))
    when(notificationRepository.update(any[NotificationResult]())).thenReturn(failed(new Exception("CRASH!")))
  }

  "NotificationsService getQueuedNotifications" should {
    "return a list of messages when queued notifications are available" in new Success {
      val result: Option[Seq[Notification]] = await(service.getQueuedNotifications)

      val actualNotifications: Seq[Notification] = result.getOrElse(fail("should have found notifications"))

      actualNotifications.size shouldBe 2
      actualNotifications.head.endpoint shouldBe someEndpoint
      actualNotifications(1).endpoint shouldBe otherEndpoint
    }

    "throw a service unavailable exception given an issue with the repository" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.getQueuedNotifications)
      }

      result.getMessage shouldBe "Unable to retrieve queued notifications"
    }
  }

  "NotificationsService getTimedOutNotifications" should {
    "return a list of messages when timed out notifications are available" in new Success {
      val result: Option[Seq[Notification]] = await(service.getTimedOutNotifications)

      val actualNotifications: Seq[Notification] = result.getOrElse(fail("should have found notifications"))

      actualNotifications.size shouldBe 2
      actualNotifications.head.endpoint shouldBe otherEndpoint
      actualNotifications(1).endpoint shouldBe someEndpoint
    }

    "throw a service unavailable exception given an issue with the repository" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.getTimedOutNotifications)
      }

      result.getMessage shouldBe "Unable to retrieve notifications"
    }
  }

  "NotificationsService updateNotifications" should {
    "update notifications in the repository" in new Success {

      // TODO: should capture notificationRepository.update() arguments
      val actualUpdates: Boolean = await(service.updateNotifications(updates))

      actualUpdates shouldBe false
    }

    "throw a service unavailable exception given repository problems" in new Failed {
      val result = intercept[HttpException] {
        await(service.updateNotifications(updates))
      }

      result.getMessage shouldBe "processGroup failed for value=\"NotificationResult(msg-id-1,delivered)\""
    }
  }
}
