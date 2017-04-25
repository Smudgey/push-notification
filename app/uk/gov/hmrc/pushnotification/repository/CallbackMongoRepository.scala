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
import play.api.libs.json.{Format, JsPath, Json, Reads}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONArray, BSONDateTime, BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class PushMessageCallbackPersist(id: BSONObjectID, messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String] = None)

object PushMessageCallbackPersist {
  implicit val oidFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val reads: Reads[PushMessageCallbackPersist] = (
    (JsPath \ "_id").read[BSONObjectID] and
      (JsPath \ "messageId").read[String] and
      (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "status").read[PushMessageStatus](PushMessageStatus.readsFromRepository) and
      (JsPath \ "answer").readNullable[String]
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
          Index(Seq("messageId" -> IndexType.Ascending, "status" -> IndexType.Ascending), name = Some("messageIdAndStatusUnique"), unique = true))
      )
    )
  }

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: PushMessageCallbackPersist): Boolean = newRecordId.equals(oldRecord.id)

  override def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String]): Future[Either[String, Boolean]] =
    atomicUpsert(findCallbackByMessageIdAndStatus(messageId, status), insertCallback(messageId, callbackUrl, status, answer)).
      map { r =>
        if (r.writeResult.ok) {
          Right(!r.writeResult.updatedExisting)
        } else {
          Left(r.writeResult.message)
        }
      }

  def findCallbackByMessageIdAndStatus(messageId: String, status: PushMessageStatus): BSONDocument =
    BSONDocument("$and" -> BSONArray(BSONDocument("messageId" -> messageId), BSONDocument("status" -> PushMessageStatus.ordinal(status))))

  def insertCallback(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String]): BSONDocument = {
    val callback = BSONDocument(
      "$setOnInsert" -> BSONDocument("messageId" -> messageId),
      "$setOnInsert" -> BSONDocument("callbackUrl" -> callbackUrl),
      "$setOnInsert" -> BSONDocument("status" -> PushMessageStatus.ordinal(status)),
      "$setOnInsert" -> BSONDocument("created" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )

    val response = answer.fold(BSONDocument.empty) { ans =>
      BSONDocument("$setOnInsert" -> BSONDocument("answer" -> ans))
    }
    callback ++ response
  }

  override def findLatest(messageId: String): Future[Option[PushMessageCallbackPersist]] =
    collection.
      find(Json.obj("messageId" -> messageId)).
      sort(Json.obj("status" -> -1)).
      one[PushMessageCallbackPersist](ReadPreference.primaryPreferred)
}


@ImplementedBy(classOf[CallbackMongoRepository])
trait CallbackRepositoryApi {
  def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String]): Future[Either[String, Boolean]]

  def findLatest(messageId: String): Future[Option[PushMessageCallbackPersist]]
}