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

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import play.api.libs.json.{Format, JsNumber, Json}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONArray, BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.NotificationStatus.{PermanentlyFailed, delivered, failed, queued, sent}
import uk.gov.hmrc.pushnotification.domain.{Notification, NotificationResult, NotificationStatus}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class NotificationPersist(id: BSONObjectID, notificationId: String, messageId: Option[String], authId: String, endpoint: String,
                               content: String, os: String, status: NotificationStatus, attempts: Int)

object NotificationPersist {
  val mongoFormats: Format[NotificationPersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[NotificationPersist], Json.writes[NotificationPersist])
  })
}

@Singleton
class PushNotificationMongoRepository @Inject() (mongo: DB, @Named("sendNotificationMaxRetryAttempts") maxAttempts: Int)
  extends ReactiveRepository[NotificationPersist, BSONObjectID]("notification", () => mongo, NotificationPersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[NotificationPersist]
    with PushNotificationRepositoryApi
    with BSONBuilderHelpers {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("messageId" -> IndexType.Ascending), name = Some("messageIdNotUnique"), unique = false)),
        collection.indexesManager.ensure(
          Index(Seq("notificationId" -> IndexType.Ascending), name = Some("notificationIdUnique"), unique = true, sparse = true)),
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
    atomicUpsert(findNotificationByNotificationId(notification.notificationId), insertNotification(authId, notification)).map { r =>
      if (r.writeResult.ok) {
        Right(r.updateType.savedValue)
      } else {
        Left(r.writeResult.message)
      }
    }
  }

  override def update(notificationId: String, status: NotificationStatus): Future[Either[String, NotificationPersist]] = {
    atomicUpdate(findNotificationByNotificationId(Some(notificationId)), updateStatus(status)).map { maybeUpdate =>
      maybeUpdate.map { update =>
        if (update.writeResult.ok) {
          Right(update.updateType.savedValue)
        } else {
          Left(update.writeResult.message)
        }
      }.getOrElse(Left(s"Cannot find notification with id = $notificationId"))
    }
  }

  override def update(result: NotificationResult): Future[Either[String, NotificationPersist]] = update(result.notificationId, result.status)

  override def findByStatus(status: NotificationStatus): Future[Seq[NotificationPersist]] =
    collection.
      find(Json.obj("status" -> status.toString)).
      sort(Json.obj("updated" -> JsNumber(1))).
      cursor[NotificationPersist](ReadPreference.primaryPreferred).
      collect[Seq]()

  override def getQueuedNotifications(maxBatchSize: Int): Future[Seq[NotificationPersist]] = {

    def queuedNotifications = {
      collection.find(BSONDocument(
        "$and" -> BSONArray(
          BSONDocument("status" -> queued),
          BSONDocument("attempts" -> BSONDocument("$lt" -> maxAttempts))
        )
      )).
        sort(Json.obj("created" -> JsNumber(-1))).cursor[NotificationPersist](ReadPreference.primaryPreferred).
        collect[List](maxBatchSize)
    }

    processBatch(queuedNotifications)
  }

  override def getTimedOutNotifications(timeoutMilliseconds: Long, maxBatchSize: Int): Future[Seq[NotificationPersist]] = {

    def timedOutNotifications = {
      collection.find(BSONDocument(
        "$and" -> BSONArray(
          BSONDocument("status" -> sent),
          BSONDocument("attempts" -> BSONDocument("$lt" -> maxAttempts)),
          BSONDocument("updated" -> BSONDocument("$lt" -> BSONDateTime(DateTimeUtils.now.getMillis - timeoutMilliseconds)))
        )
      )).
        sort(Json.obj("created" -> JsNumber(-1))).cursor[NotificationPersist](ReadPreference.primaryPreferred).
        collect[List](maxBatchSize)
    }

    processBatch(timedOutNotifications)
  }

  override def permanentlyFail(): Future[Option[Int]] = {
    def maxAttemptsReached = BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("status" -> BSONDocument("$nin" -> BSONArray(delivered, failed))),
        BSONDocument("attempts" -> BSONDocument("$gte" -> maxAttempts))
      )
    )

    collection.update(maxAttemptsReached, updateStatus(PermanentlyFailed), upsert = false, multi = true)
      .map(r => if (r.nModified > 0) Some(r.nModified) else None)
  }

  def processBatch(batch: Future[List[NotificationPersist]]) : Future[Seq[NotificationPersist]] = {
    def setSent(batch: List[NotificationPersist]) = {
      collection.update(
        BSONDocument("_id" -> BSONDocument("$in" -> batch.foldLeft(BSONArray())((a, p) => a.add(p.id)))),
        BSONDocument(
          "$set" -> BSONDocument(
            "updated" -> BSONDateTime(DateTimeUtils.now.getMillis),
            "status" -> sent
          ),
          "$inc" -> BSONDocument(
            "attempts" -> 1
          )
        ),
        upsert = false,
        multi = true
      )
    }

    def getBatchOrFailed(batch: List[NotificationPersist], updateWriteResult: UpdateWriteResult) = {
      if (updateWriteResult.ok) {
        Future.successful(batch.map(n => n.copy(attempts = n.attempts + 1)))
      } else {
        Future.failed(new ReactiveMongoException {
          override def message: String = "failed to fetch unsent notifications"
        })
      }
    }

    for (
      notifications <- batch;
      updateResult <- setSent(notifications);
      unsentNotifications <- getBatchOrFailed(notifications, updateResult)
    ) yield unsentNotifications
  }

  def findNotificationByNotificationId(notificationId: Option[String]) = BSONDocument("notificationId" -> notificationId.getOrElse("-1"))

  def updateStatus(status: NotificationStatus) = BSONDocument(
    "$set" -> BSONDocument(
      "status" -> status.toString,
      "updated" -> BSONDateTime(DateTimeUtils.now.getMillis)
    )
  )

  def insertNotification(authId: String, notification: Notification): BSONDocument = {
    val notificationId = notification.notificationId.fold(BSONDocument.empty) { id =>
      BSONDocument("$setOnInsert" -> BSONDocument("notificationId" -> id))
    }
    val messageId = notification.messageId.fold(BSONDocument.empty) { id =>
      BSONDocument("$setOnInsert" -> BSONDocument("messageId" -> id))
    }
    val coreData = BSONDocument(
      "$setOnInsert" -> BSONDocument("os" -> notification.os),
      "$setOnInsert" -> BSONDocument("authId" -> authId),
      "$setOnInsert" -> BSONDocument("attempts" -> 0),
      "$setOnInsert" -> BSONDocument("endpoint" -> notification.endpoint),
      "$setOnInsert" -> BSONDocument("created" -> BSONDateTime(DateTimeUtils.now.getMillis)),

      "$set" -> BSONDocument("content" -> notification.content),
      "$set" -> BSONDocument("status" -> notification.status.toString),
      "$set" -> BSONDocument("updated" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )
    notificationId ++ messageId ++ coreData
  }
}

@ImplementedBy(classOf[PushNotificationMongoRepository])
trait PushNotificationRepositoryApi {
  def save(authId: String, notification: Notification): Future[Either[String, NotificationPersist]]

  def update(notificationId: String, status: NotificationStatus): Future[Either[String, NotificationPersist]]

  def update(result: NotificationResult): Future[Either[String, NotificationPersist]]

  def findByStatus(status: NotificationStatus): Future[Seq[NotificationPersist]]

  def getQueuedNotifications(maxRows: Int): Future[Seq[NotificationPersist]]

  def getTimedOutNotifications(timeoutMilliseconds: Long, maxRows: Int): Future[Seq[NotificationPersist]]

  def permanentlyFail(): Future[Option[Int]]
}

@Singleton
class PushNotificationMongoRepositoryTest @Inject() (mongo: DB, @Named("sendNotificationMaxRetryAttempts") maxAttempts: Int) extends PushNotificationMongoRepository(mongo, maxAttempts) {

  def removeAllRecords(): Future[Unit] = {
    removeAll().map(_ => ())
  }

  def findByEndpoint(endpoint: String, authId:String): Future[Option[NotificationPersist]] = {
    collection.
      find(BSONDocument("authId" -> authId) ++ BSONDocument("endpoint" -> endpoint))
      .one[NotificationPersist](ReadPreference.primaryPreferred)
  }
}
