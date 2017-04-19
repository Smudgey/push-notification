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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.http.ServiceUnavailableException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Queued, Sent}
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationRepositoryApi}

import scala.concurrent.Future.{failed, successful}

class NotificationsServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {
  private trait Setup extends MockitoSugar {
    val mockRepository: PushNotificationRepositoryApi = mock[PushNotificationRepositoryApi]

    val service = new NotificationsService(mockRepository)

    val someMessageId = "msg-id-abcd-1234"
    val someAuthId = "bob-id"
    val someEndpoint = "foo:bar:baz"
    val otherEndpoint = "wibble:wubble:wobble"
    val someContent = "a very important message"
    val someNotificationId = "msg-id-1"
    val otherNotificationId = "msg-id-2"

    val somePersisted = NotificationPersist(BSONObjectID.generate, someMessageId, someAuthId, someEndpoint, someContent, None, someNotificationId, Sent, 1)
    val otherPersisted = NotificationPersist(BSONObjectID.generate, someMessageId, someAuthId, otherEndpoint, someContent, None, otherNotificationId, Sent, 2)

    val updates = Map(someNotificationId -> Delivered, otherNotificationId -> Queued)
  }

  private trait Success extends Setup {
    doReturn(successful(Seq(somePersisted, otherPersisted)), Nil: _* ).when(mockRepository).getUnsentNotifications
    doReturn(successful(Right(somePersisted)), Nil: _* ).when(mockRepository).update(ArgumentMatchers.eq(someNotificationId), any[NotificationStatus]())
    doReturn(successful(Left("KABOOM!")), Nil: _* ).when(mockRepository).update(ArgumentMatchers.eq(otherNotificationId), any[NotificationStatus]())
  }

  private trait Failed extends Setup {
    doReturn(failed(new Exception("KAPOW!")), Nil: _* ).when(mockRepository).getUnsentNotifications
    doReturn(failed(new Exception("CRASH!")), Nil: _* ).when(mockRepository).update(any[String](), any[NotificationStatus]())
  }

  "NotificationsService getUnsentNotifications" should {
    "return a list of messages when unsent notifications are available" in new Success {
      val result: Seq[Notification] = await(service.getUnsentNotifications)

      result.size shouldBe 2
      result.head.endpoint shouldBe someEndpoint
      result(1).endpoint shouldBe otherEndpoint
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

      val result: Seq[Boolean] = await(service.updateNotifications(updates))

      result.head shouldBe true
      result(1) shouldBe false
    }

    "throw a service unavailable exception given repository problems" in new Failed {
      val result = intercept[ServiceUnavailableException] {
        await(service.updateNotifications(updates))
      }

      result.getMessage shouldBe "Unable to update notification [msg-id-1 -> delivered]"
    }
  }
}
