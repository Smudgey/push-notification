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

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.libs.json.{Format, JsNumber, Json}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{Queued, Sent, queued, sent}
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class NotificationPersist(id: BSONObjectID, authId: String, endpoint: String, message: String, messageId: String, status: NotificationStatus)

object NotificationPersist {
  val mongoFormats: Format[NotificationPersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[NotificationPersist], Json.writes[NotificationPersist])
  })
}

@Singleton
class PushNotificationMongoRepository @Inject() (mongo: DB)
  extends ReactiveRepository[NotificationPersist, BSONObjectID]("notification", () => mongo, NotificationPersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[NotificationPersist]
    with PushNotificationRepositoryApi
    with BSONBuilderHelpers {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("messageId" -> IndexType.Ascending), name = Some("messageIdUnique"), unique = true, sparse = true)),
        collection.indexesManager.ensure(
          Index(Seq("authId" -> IndexType.Ascending), name = Some("authIdNotUnique"), unique = false)),
        collection.indexesManager.ensure(
          Index(Seq("endpoint" -> IndexType.Ascending), name = Some("endpointNotUnique"), unique = false)),
        collection.indexesManager.ensure(
          Index(Seq("status" -> IndexType.Ascending), name = Some("statusNotUnique"), unique = false))
      )
    )
  }

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: NotificationPersist): Boolean = newRecordId.equals(oldRecord.id)

  override def save(authId: String, notification: Notification): Future[Either[String, NotificationPersist]] = {
    atomicUpsert(findNotificationByMessageId(notification.messageId), insertNotification(authId, notification)).map { r =>
      if (r.writeResult.ok) {
        Right(r.updateType.savedValue)
      } else {
        Left(r.writeResult.message)
      }
    }
  }

  override def findByStatus(status: NotificationStatus): Future[Seq[NotificationPersist]] =
    collection.
      find(Json.obj("status" -> status.toString)).
      sort(Json.obj("updated" -> JsNumber(1))).
      cursor[NotificationPersist](ReadPreference.primaryPreferred).
      collect[Seq]()

  override def getUnsentNotifications: Future[Seq[NotificationPersist]] = {
    val updated = BSONDateTime(DateTimeUtils.now.getMillis)

    val update: Future[UpdateWriteResult] = collection.update(
      BSONDocument("status" -> queued),
      BSONDocument(
        "$set" -> BSONDocument(
          "updated" -> updated,
          "status" -> sent
        )
      ),
      upsert = false,
      multi = true
    )

    update.flatMap { _ =>
      collection.
        find(BSONDocument("updated" -> updated)).
        sort(Json.obj("updated" -> JsNumber(-1))).
        cursor[NotificationPersist](ReadPreference.primaryPreferred).
        collect[Seq]()
    }
  }

  def findNotificationByMessageId(messageId: Option[String]) = BSONDocument("messageId" -> messageId.getOrElse("-1"))

  def insertNotification(authId: String, notification: Notification): BSONDocument = {
    val messageId = notification.messageId.fold(BSONDocument.empty) { id =>
      BSONDocument("$setOnInsert" -> BSONDocument("messageId" -> id))
    }
    val coreData = BSONDocument(
      "$setOnInsert" -> BSONDocument("authId" -> authId),
      "$setOnInsert" -> BSONDocument("endpoint" -> notification.endpoint),
      "$setOnInsert" -> BSONDocument("message" -> notification.message),
      "$setOnInsert" -> BSONDocument("created" -> BSONDateTime(DateTimeUtils.now.getMillis)),

      "$set" -> BSONDocument("status" -> notification.status.toString),
      "$set" -> BSONDocument("updated" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )
    messageId ++ coreData
  }

  def findUnsent(): BSONDocument =
    BSONDocument("status" -> Queued.toString)

  def setSent(): BSONDocument =
    BSONDocument(
      "$set" -> BSONDocument("status" -> Sent.toString),
      "$set" -> BSONDocument("updated" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )
}

@ImplementedBy(classOf[PushNotificationMongoRepository])
trait PushNotificationRepositoryApi {
  def save(authId: String, notification: Notification): Future[Either[String, NotificationPersist]]

  def findByStatus(status: NotificationStatus): Future[Seq[NotificationPersist]]

  def getUnsentNotifications: Future[Seq[NotificationPersist]]
}
