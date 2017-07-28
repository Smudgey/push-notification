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
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONArray, BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.{Callback, CallbackResult, PushMessageStatus, Response}
import uk.gov.hmrc.pushnotification.repository.ProcessingStatus.{queued, sent}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class PushMessageCallbackPersist(id: BSONObjectID, messageId: String, callbackUrl: String, status: PushMessageStatus,
                                      answer: Option[String] = None, processingStatus: ProcessingStatus, attempts: Int)

object PushMessageCallbackPersist {
  implicit val oidFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val reads: Reads[PushMessageCallbackPersist] = (
    (JsPath \ "_id").read[BSONObjectID] and
      (JsPath \ "messageId").read[String] and
      (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "status").read[PushMessageStatus](PushMessageStatus.readsFromRepository) and
      (JsPath \ "answer").readNullable[String] and
      (JsPath \ "processingStatus").read[ProcessingStatus] and
      (JsPath \ "attempts").read[Int]
    ) (PushMessageCallbackPersist.apply _)
  val mongoFormats: Format[PushMessageCallbackPersist] = ReactiveMongoFormats.mongoEntity({
    Format(reads, Json.writes[PushMessageCallbackPersist])
  })
}

@Singleton
class CallbackMongoRepository @Inject()(mongo: DB, @Named("clientCallbackMaxRetryAttempts") maxAttempts: Int)
  extends ReactiveRepository[PushMessageCallbackPersist, BSONObjectID]("callback", () => mongo, PushMessageCallbackPersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[PushMessageCallbackPersist]
    with CallbackRepositoryApi
    with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("messageId" -> IndexType.Ascending, "status" -> IndexType.Ascending, "attempt" -> IndexType.Ascending), name = Some("messageIdAndStatusAndAttemptUnique"), unique = true)),
        collection.indexesManager.ensure(
          Index(Seq("created" -> IndexType.Ascending), name = Some("createdNotUnique"), unique = false))
      )
    )
  }

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: PushMessageCallbackPersist): Boolean = newRecordId.equals(oldRecord.id)

  override def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String], attempt: Int = 0): Future[Either[String, Boolean]] =
    atomicUpsert(findByMessageIdAndStatus(messageId, status), insertCallback(Callback(callbackUrl, status, Response(messageId, answer), attempt))).
      map { r =>
        if (r.writeResult.ok) {
          Right(!r.writeResult.updatedExisting)
        } else {
          Left(r.writeResult.message)
        }
      }

  override def findLatest(messageIds: List[String]): Future[List[PushMessageCallbackPersist]] =
    collection.find(Json.obj("messageId" -> Json.obj("$in" -> messageIds)))
      .sort(Json.obj("status" -> -1))
      .cursor[PushMessageCallbackPersist](ReadPreference.primaryPreferred).collect[List]()

  override def findByStatus(messageId: String, status: PushMessageStatus): Future[Option[PushMessageCallbackPersist]] =
    collection.
      find(
        BSONDocument("$and" -> BSONArray(
          BSONDocument("messageId" -> messageId),
          BSONDocument("status" -> PushMessageStatus.ordinal(status))
        ))).
      one[PushMessageCallbackPersist](ReadPreference.primaryPreferred)

  override def findUndelivered(maxBatchSize: Int): Future[Seq[PushMessageCallbackPersist]] = {
    def undeliveredCallbacks = {
      collection.find(
        BSONDocument(
          "$and" -> BSONArray(
            BSONDocument("processingStatus" -> queued),
            BSONDocument("attempts" -> BSONDocument("$lt" -> maxAttempts))
          )
        )).
        sort(Json.obj("created" -> JsNumber(1))).cursor[PushMessageCallbackPersist](ReadPreference.primaryPreferred).
        collect[List](maxBatchSize)
    }

    processBatch(undeliveredCallbacks)
  }

  override def update(result: CallbackResult): Future[Either[String, PushMessageCallbackPersist]] = {
    val processingStatus = if (result.success) {
      ProcessingStatus.Delivered
    } else {
      ProcessingStatus.Queued
    }

    atomicUpdate(findByMessageIdAndStatus(result.messageId, result.status), updateStatus(processingStatus)).map { maybeUpdate =>
      maybeUpdate.map { update =>
        if (update.writeResult.ok) {
          Right(update.updateType.savedValue)
        } else {
          Left(update.writeResult.message)
        }
      }.getOrElse(Left(s"Cannot find callback with message-id = ${result.messageId} and status = ${result.status}"))
    }
  }

  def processBatch(batch: Future[List[PushMessageCallbackPersist]]): Future[Seq[PushMessageCallbackPersist]] = {
    def setProcessed(batch: List[PushMessageCallbackPersist]) = {
      collection.update(
        BSONDocument("_id" -> BSONDocument("$in" -> batch.foldLeft(BSONArray())((a, p) => a.add(p.id)))),
        BSONDocument(
          "$set" -> BSONDocument(
            "updated" -> BSONDateTime(DateTimeUtils.now.getMillis),
            "processingStatus" -> sent
          ),
          "$inc" -> BSONDocument(
            "attempts" -> 1
          )
        ),
        upsert = false,
        multi = true
      )
    }

    def getBatchOrFailed(batch: List[PushMessageCallbackPersist], updateWriteResult: UpdateWriteResult) = {
      if (updateWriteResult.ok) {
        Future.successful(batch.map(n => n.copy(attempts = n.attempts + 1)))
      } else {
        Future.failed(new ReactiveMongoException {
          override def message: String = "failed to fetch callbacks"
        })
      }
    }

    for (
      callbacks <- batch;
      updateResult <- setProcessed(callbacks);
      unsentNotifications <- getBatchOrFailed(callbacks, updateResult)
    ) yield unsentNotifications
  }

    def findByMessageIdAndStatus(messageId: String, status: PushMessageStatus): BSONDocument =
      BSONDocument("$and" -> BSONArray(
        BSONDocument("messageId" -> messageId),
        BSONDocument("status" -> PushMessageStatus.ordinal(status))
      ))

  def updateStatus(processingStatus: ProcessingStatus) =
    BSONDocument("$set" -> BSONDocument(
      "updated" -> BSONDateTime(DateTimeUtils.now.getMillis),
      "processingStatus" -> processingStatus.toString
    ))

  def insertCallback(callback: Callback): BSONDocument = {
    val coreData = BSONDocument(
      "$setOnInsert" -> BSONDocument("messageId" -> callback.response.messageId),
      "$setOnInsert" -> BSONDocument("callbackUrl" -> callback.callbackUrl),
      "$setOnInsert" -> BSONDocument("status" -> PushMessageStatus.ordinal(callback.status)),
      "$setOnInsert" -> BSONDocument("attempts" -> callback.attempt),
      "$setOnInsert" -> BSONDocument("processingStatus" ->  queued),
      "$setOnInsert" -> BSONDocument("created" -> BSONDateTime(DateTimeUtils.now.getMillis)),

      "$set" -> BSONDocument("updated" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )

    val response = callback.response.answer.fold(BSONDocument.empty) { ans =>
      BSONDocument("$setOnInsert" -> BSONDocument("answer" -> ans))
    }
    coreData ++ response
  }
}


@Singleton
class CallbackMongoRepositoryTest @Inject() (mongo: DB, @Named("sendNotificationMaxRetryAttempts") maxAttempts: Int) extends CallbackMongoRepository(mongo, maxAttempts) {

  def removeAllRecords(): Future[Unit] = {
    removeAll().map(_ => ())
  }
}

@ImplementedBy(classOf[CallbackMongoRepository])
trait CallbackRepositoryApi {
  def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String], attempt: Int = 0): Future[Either[String, Boolean]]

  def update(result: CallbackResult): Future[Either[String, PushMessageCallbackPersist]]

  def findLatest(messageIds: List[String]): Future[List[PushMessageCallbackPersist]]

  def findByStatus(messageId: String, status: PushMessageStatus): Future[Option[PushMessageCallbackPersist]]

  def findUndelivered(maxRows: Int): Future[Seq[PushMessageCallbackPersist]]
}