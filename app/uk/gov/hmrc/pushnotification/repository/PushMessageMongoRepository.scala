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
import play.api.libs.json.{Format, Json}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONArray, BSONDateTime, BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.PushMessage
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class PushMessagePersist(id: BSONObjectID, authId: String, messageId: String, subject: String, body: String, responses: Map[String, String], callbackUrl: String)

object PushMessagePersist {
  val mongoFormats: Format[PushMessagePersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[PushMessagePersist], Json.writes[PushMessagePersist])
  })
}

@Singleton
class PushMessageMongoRepository @Inject()(mongo: DB)
  extends ReactiveRepository[PushMessagePersist, BSONObjectID]("pushMessage", () => mongo, PushMessagePersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[PushMessagePersist]
    with PushMessageRepositoryApi
    with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("messageId" -> IndexType.Ascending), name = Some("messageIdUnique"), unique = true)),
        collection.indexesManager.ensure(
          Index(Seq("authId" -> IndexType.Ascending), name = Some("authIdNotUnique"), unique = false))
      )
    )
  }

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: PushMessagePersist): Boolean = newRecordId.equals(oldRecord.id)

  override def save(authId: String, message: PushMessage): Future[Either[String, PushMessagePersist]] =
    atomicUpsert(findDocumentByMessageId(message.messageId), insertMessage(authId, message)).map { r =>
    if (r.writeResult.ok) {
      Right(r.updateType.savedValue)
    } else {
      Left(r.writeResult.message)
    }
  }

  override def find(messageId: String, authId:Option[String]): Future[Option[PushMessagePersist]] =
    collection.
      find(
        BSONDocument("$and" -> BSONArray(
          BSONDocument("messageId" -> messageId) ++
          authId.fold(BSONDocument.empty){found => BSONDocument("authId" -> found)}))).
        one[PushMessagePersist](ReadPreference.primaryPreferred)

  def findDocumentByMessageId(messageId: String): BSONDocument = BSONDocument("messageId" -> messageId)

  def insertMessage(authId: String, message: PushMessage): BSONDocument =
    BSONDocument(
      "$setOnInsert" -> BSONDocument("messageId" -> message.messageId),
      "$setOnInsert" -> BSONDocument("authId" -> authId),
      "$setOnInsert" -> BSONDocument("created" -> BSONDateTime(DateTimeUtils.now.getMillis)),

      "$set" -> BSONDocument("subject" -> message.subject),
      "$set" -> BSONDocument("body" -> message.body),
      "$set" -> BSONDocument("callbackUrl" -> message.callbackUrl),
      "$set" -> BSONDocument("responses" -> message.responses.foldLeft(BSONDocument.empty)((d, k) => d ++ BSONDocument(k._1 -> k._2))),
      "$set" -> BSONDocument("updated" -> BSONDateTime(DateTimeUtils.now.getMillis))
    )

  override def findByAuthority(authId: String): Future[Seq[PushMessagePersist]] = {
    collection.find(
      BSONDocument("authId" -> authId)
    ).cursor[PushMessagePersist](ReadPreference.primaryPreferred).collect[Seq](50)
  }
}

@Singleton
class PushMessageMongoRepositoryTest @Inject() (mongo: DB) extends PushMessageMongoRepository(mongo) {

  def removeAllRecords(): Future[Unit] = {
    removeAll().map(_ => ())
  }
}

@ImplementedBy(classOf[PushMessageMongoRepository])
trait PushMessageRepositoryApi {
  def save(authId: String, message: PushMessage): Future[Either[String, PushMessagePersist]]
  def find(messageId: String, authId:Option[String]): Future[Option[PushMessagePersist]]
  def findByAuthority(authId: String): Future[Seq[PushMessagePersist]]
}
