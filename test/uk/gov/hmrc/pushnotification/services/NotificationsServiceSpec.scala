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
import uk.gov.hmrc.play.http.ServiceUnavailableException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Queued, Sent}
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationRepositoryApi}

import scala.concurrent.Future.{failed, successful}

class NotificationsServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {
  private trait Setup extends MockitoSugar {
    val notificationRepository: PushNotificationRepositoryApi = mock[PushNotificationRepositoryApi]
    val lockRepository: LockRepository = mock[LockRepository]

    val service = new NotificationsService(notificationRepository, lockRepository, 100)

    val someMessageId = "msg-id-abcd-1234"
    val someAuthId = "bob-id"
    val someEndpoint = "foo:bar:baz"
    val otherEndpoint = "wibble:wubble:wobble"
    val someContent = "a very important message"
    val someOs = "windows"
    val someNotificationId = "msg-id-1"
    val otherNotificationId = "msg-id-2"

    val somePersisted = NotificationPersist(BSONObjectID.generate, someNotificationId, Some(someMessageId), someAuthId, someEndpoint, someContent,someOs , Sent, 1)
    val otherPersisted = NotificationPersist(BSONObjectID.generate, otherNotificationId, Some(someMessageId), someAuthId, otherEndpoint, someContent, someOs, Sent, 2)

    val updates = Map(someNotificationId -> Delivered, otherNotificationId -> Queued)
  }

  private trait LockOK extends Setup {
    doReturn(successful(true), Nil: _* ).when(lockRepository).lock(any[String](), any[String](), any[Duration]())
    doReturn(successful({}), Nil: _* ).when(lockRepository).releaseLock(any[String](), any[String]())
  }

  private trait Success extends LockOK {
    doReturn(successful(Seq(somePersisted, otherPersisted)), Nil: _* ).when(notificationRepository).getUnsentNotifications(any[Int]())
    doReturn(successful(Right(somePersisted)), Nil: _* ).when(notificationRepository).update(ArgumentMatchers.eq(someNotificationId), any[NotificationStatus]())
    doReturn(successful(Left("KABOOM!")), Nil: _* ).when(notificationRepository).update(ArgumentMatchers.eq(otherNotificationId), any[NotificationStatus]())
  }

  private trait Failed extends LockOK {
    doReturn(failed(new Exception("KAPOW!")), Nil: _* ).when(notificationRepository).getUnsentNotifications(any[Int]())
    doReturn(failed(new Exception("CRASH!")), Nil: _* ).when(notificationRepository).update(any[String](), any[NotificationStatus]())
  }

  "NotificationsService getUnsentNotifications" should {
    "return a list of messages when unsent notifications are available" in new Success {
      val result: Option[Seq[Notification]] = await(service.getUnsentNotifications)

      val actualNotifications: Seq[Notification] = result.getOrElse(fail("should have found notifications"))

      actualNotifications.size shouldBe 2
      actualNotifications.head.endpoint shouldBe someEndpoint
      actualNotifications(1).endpoint shouldBe otherEndpoint
    }

    "throw a service unavailable exception given an issue with the repository" in new Failed {
      val result = intercept[ServiceUnavailableException]{
        await(service.getUnsentNotifications)
      }

      result.getMessage shouldBe "Unable to retrieve unsent notifications"
    }
  }

  "NotificationsService updateNotifications" should {
    "update notifications in the repository" in new Success {

      val actualUpdates: Seq[Boolean] = await(service.updateNotifications(updates))

      actualUpdates.head shouldBe true
      actualUpdates(1) shouldBe false
    }

    "throw a service unavailable exception given repository problems" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.updateNotifications(updates))
      }

      result.getMessage shouldBe "Unable to update notification [msg-id-1 -> delivered]"
    }
  }
}
