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
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
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
  extends ReactiveRepository[PushMessagePersist, BSONObjectID]("callback", () => mongo, PushMessagePersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[PushMessageCallbackPersist]
    with CallbackMongoRepositoryApi
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

  override def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String]): Future[WriteResult] = {
    val callback = BSONDocument(
      "messageId" -> messageId,
      "callbackUrl" -> callbackUrl,
      "status" -> PushMessageStatus.ordinal(status),
      "created" -> BSONDateTime(DateTimeUtils.now.getMillis)
    )
    val response = answer.fold(BSONDocument.empty) { a =>
      BSONDocument("answer" -> a)
    }

    collection.insert(callback ++ response)
  }

  override def findLatest(messageId: String): Future[Option[PushMessageCallbackPersist]] =
    collection.
      find(Json.obj("messageId" -> messageId)).
      sort(Json.obj("status" -> -1)).
      one[PushMessageCallbackPersist](ReadPreference.primaryPreferred)
}


@ImplementedBy(classOf[CallbackMongoRepository])
trait CallbackMongoRepositoryApi {
  def save(messageId: String, callbackUrl: String, status: PushMessageStatus, answer: Option[String]): Future[WriteResult]

  def findLatest(messageId: String): Future[Option[PushMessageCallbackPersist]]
}