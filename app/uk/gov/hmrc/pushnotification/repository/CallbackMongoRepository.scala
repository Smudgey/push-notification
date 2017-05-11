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
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONArray, BSONDateTime, BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Answer, Timeout}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class PushMessageCallbackPersist(id: BSONObjectID, messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String] = None, attempt: Int = 0)

object PushMessageCallbackPersist {
  implicit val oidFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val reads: Reads[PushMessageCallbackPersist] = (
    (JsPath \ "_id").read[BSONObjectID] and
      (JsPath \ "messageId").read[String] and
      (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "status").read[PushMessageStatus](PushMessageStatus.readsFromRepository) and
      (JsPath \ "answer").readNullable[String] and
      (JsPath \ "attempt").read[Int]
    ) (PushMessageCallbackPersist.apply _)
  val mongoFormats: Format[PushMessageCallbackPersist] = ReactiveMongoFormats.mongoEntity({
    Format(reads, Json.writes[PushMessageCallbackPersist])
  })
}

@Singleton
class CallbackMongoRepository @Inject()(mongo: DB)
  extends ReactiveRepository[PushMessageCallbackPersist, BSONObjectID]("callback", () => mongo, PushMessageCallbackPersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[PushMessageCallbackPersist]
    with CallbackRepositoryApi
    with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("messageId" -> IndexType.Ascending, "status" -> IndexType.Ascending, "attempt" -> IndexType.Ascending), name = Some("messageIdAndStatusAndAttemptUnique"), unique = true))
      )
    )
  }

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: PushMessageCallbackPersist): Boolean = newRecordId.equals(oldRecord.id)

  override def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String], attempt: Int = 0): Future[Either[String, Boolean]] =
    atomicUpsert(findCallbackByMessageIdAndStatusAndAttempt(messageId, status, attempt), insertCallback(messageId, callbackUrl, status, answer, attempt)).
      map { r =>
        if (r.writeResult.ok) {
          Right(!r.writeResult.updatedExisting)
        } else {
          Left(r.writeResult.message)
        }
      }

  override def findLatest(messageId: String): Future[Option[PushMessageCallbackPersist]] =
    collection.
      find(Json.obj("messageId" -> messageId)).
      sort(Json.obj("status" -> -1)).
      one[PushMessageCallbackPersist](ReadPreference.primaryPreferred)

  override def findUndelivered: Future[Seq[PushMessageCallbackPersist]] = {
    val processed: BSONDateTime = BSONDateTime(DateTimeUtils.now.getMillis)

    val update: Future[UpdateWriteResult] = collection.update(
      undelivered(),
      setProcessed(processed),
      upsert = false,
      multi = true
    )

    update.flatMap { _ =>
      collection.
        find(BSONDocument("processed" -> processed)).
        sort(Json.obj("processed" -> JsNumber(-1))).
        cursor[PushMessageCallbackPersist](ReadPreference.primaryPreferred).
        collect[Seq]()
    }
  }

  def findCallbackByMessageIdAndStatusAndAttempt(messageId: String, status: PushMessageStatus, attempt: Int): BSONDocument =
    BSONDocument("$and" -> BSONArray(
      BSONDocument("messageId" -> messageId),
      BSONDocument("status" -> PushMessageStatus.ordinal(status)),
      BSONDocument("attempt" -> attempt)
    ))

  def insertCallback(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String], attempt: Int): BSONDocument = {
    val callback = BSONDocument(
      "$setOnInsert" -> BSONDocument("messageId" -> messageId),
      "$setOnInsert" -> BSONDocument("callbackUrl" -> callbackUrl),
      "$setOnInsert" -> BSONDocument("attempt" -> attempt),
      "$setOnInsert" -> BSONDocument("status" -> PushMessageStatus.ordinal(status)),
      "$setOnInsert" -> BSONDocument("created" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )

    val response = answer.fold(BSONDocument.empty) { ans =>
      BSONDocument("$setOnInsert" -> BSONDocument("answer" -> ans))
    }
    callback ++ response
  }

  def undelivered(): BSONDocument =
    BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("status" ->
          BSONDocument("$in" -> BSONArray(
            PushMessageStatus.ordinal(Acknowledge),
            PushMessageStatus.ordinal(Answer),
            PushMessageStatus.ordinal(Timeout)))),
        BSONDocument("processed" -> BSONDocument("$exists" -> false))
      )
    )

  def setProcessed(processed: BSONDateTime): BSONDocument =
    BSONDocument(
      "$set" -> BSONDocument(
        "processed" -> processed
      ),
      "$inc" -> BSONDocument(
        "attempt" -> 1
      )
    )
}


@ImplementedBy(classOf[CallbackMongoRepository])
trait CallbackRepositoryApi {
  def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String], attempt: Int = 0): Future[Either[String, Boolean]]

  def findLatest(messageId: String): Future[Option[PushMessageCallbackPersist]]

  def findUndelivered: Future[Seq[PushMessageCallbackPersist]]
}