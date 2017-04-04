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
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotification.connector.StubApplicationConfiguration
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Delivered, Queued, Sent}
import uk.gov.hmrc.pushnotification.repository.{NotificationPersist, PushNotificationRepositoryApi}

import scala.concurrent.Future.successful

class NotificationsServiceSpec extends UnitSpec with ScalaFutures with WithFakeApplication with StubApplicationConfiguration {
  private trait Setup extends MockitoSugar {
    val mockRepository: PushNotificationRepositoryApi = mock[PushNotificationRepositoryApi]

    val service = new NotificationsService(mockRepository)

    val someAuthId = "bob-id"
    val someEndpoint = "foo:bar:baz"
    val otherEndpoint = "wibble:wubble:wobble"
    val someMessage = "a very important message"
    val someMessageId = "msg-id-1"
    val otherMessageId = "msg-id-2"

    val somePersisted = NotificationPersist(BSONObjectID.generate, someAuthId, someEndpoint, someMessage, someMessageId, Sent)
    val otherPersisted = NotificationPersist(BSONObjectID.generate, someAuthId, otherEndpoint, someMessage, otherMessageId, Sent)

    val updates = Map(someMessageId -> Delivered, otherMessageId -> Queued)

    doReturn(successful(Seq(somePersisted, otherPersisted)), Nil: _* ).when(mockRepository).getUnsentNotifications
    doReturn(successful(Right(somePersisted)), Nil: _* ).when(mockRepository).update(ArgumentMatchers.eq(someMessageId), any[NotificationStatus]())
    doReturn(successful(Left("KABOOM!")), Nil: _* ).when(mockRepository).update(ArgumentMatchers.eq(otherMessageId), any[NotificationStatus]())
  }

  "NotificationsService getUnsentNotifications" should {
    "return a list of message ids given a valid template name and an authority with endpoints" in new Setup {
      val result: Seq[Notification] = await(service.getUnsentNotifications)

      result.size shouldBe 2
      result.head.endpoint shouldBe someEndpoint
      result(1).endpoint shouldBe otherEndpoint
    }
  }

  "NotificationsService updateNotifications" should {
    "update notifications in the repository" in new Setup {

      val result: Seq[Boolean] = await(service.updateNotifications(updates))

      result.head shouldBe true
      result(1) shouldBe false
    }
  }
}
