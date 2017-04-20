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
import play.api.libs.json.{Format, Json}
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.pushnotification.domain.Message
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class MessagePersist(id: BSONObjectID, authId: String, messageId: String, subject: String, body: String, responses: Map[String, String], callbackUrl: String)

object MessagePersist {
  val mongoFormats: Format[MessagePersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[MessagePersist], Json.writes[MessagePersist])
  })
}

@Singleton
class InAppMessageMongoRepository @Inject() (mongo: DB)
  extends ReactiveRepository[MessagePersist, BSONObjectID]("inAppMessage", () => mongo, MessagePersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[MessagePersist]
    with InAppMessageRepositoryApi
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

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: MessagePersist): Boolean = newRecordId.equals(oldRecord.id)

  override def save(authId: String, message: Message): Future[Either[String, MessagePersist]] =
    atomicUpsert(findMessageByMessageId(message.messageId), insertMessage(authId, message)).map { r =>
    if (r.writeResult.ok) {
      Right(r.updateType.savedValue)
    } else {
      Left(r.writeResult.message)
    }
  }

  def findMessageByMessageId(messageId: String): BSONDocument = BSONDocument("messageId" -> messageId)

  def insertMessage(authId: String, message: Message): BSONDocument =
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
}

@ImplementedBy(classOf[InAppMessageMongoRepository])
trait InAppMessageRepositoryApi {
  def save(authId: String, message: Message): Future[Either[String, MessagePersist]]
}
