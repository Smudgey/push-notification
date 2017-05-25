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

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import org.joda.time.Duration
import play.api.Logger
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.http.ServiceUnavailableException
import uk.gov.hmrc.pushnotification.domain.PushMessageStatus.{Acknowledge, Acknowledged, Answer, Answered, PermanentlyFailed, Timedout, Timeout}
import uk.gov.hmrc.pushnotification.domain.{Callback, CallbackBatch, PushMessageStatus, Response}
import uk.gov.hmrc.pushnotification.repository.{CallbackRepositoryApi, PushMessageCallbackPersist}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[CallbackService])
trait CallbackServiceApi {

  def getUndeliveredCallbacks: Future[Option[CallbackBatch]]

  def updateCallbacks(updates: Map[String, Boolean]): Future[Seq[Boolean]]
}

@Singleton
class CallbackService @Inject()(repository: CallbackRepositoryApi, @Named("clientCallbackMaxRetryAttempts") maxAttempts: Int, lockRepository: LockRepository) extends CallbackServiceApi {
  val completionMap: Map[PushMessageStatus, PushMessageStatus] = Map(Acknowledge -> Acknowledged, Answer -> Answered, Timeout -> Timedout)

  val getDeliveredLockKeeper = new LockKeeper {
    override def repo: LockRepository = lockRepository

    override def lockId: String = "getUndeliveredCallbacks"

    override val forceLockReleaseAfter: Duration = Duration.standardMinutes(2)
  }

  override def getUndeliveredCallbacks: Future[Option[CallbackBatch]] = {
    getDeliveredLockKeeper.tryLock {
      repository.findUndelivered(100).map((batch: Seq[PushMessageCallbackPersist]) =>
        CallbackBatch(batch.map(cb =>
          Callback(cb.callbackUrl, cb.status, Response(cb.messageId, cb.answer), cb.attempt)
        ))
      ).recover {
        case e: Exception =>
          Logger.error(s"Unable to retrieve undelivered callbacks: ${e.getMessage}")
          throw new ServiceUnavailableException(s"Unable to retrieve undelivered callbacks")
      }
    }
  }

  override def updateCallbacks(updates: Map[String, Boolean]): Future[Seq[Boolean]] = {
      Future.sequence(updates.map(s => repository.findLatest(s._1).flatMap {
        case Some(cb) =>
          val status = if (s._2) {
            completionMap.getOrElse(cb.status, PermanentlyFailed)
          } else {
            if (cb.attempt < maxAttempts) {
              cb.status
            } else {
              PermanentlyFailed
            }
          }
          repository.save(cb.messageId, cb.callbackUrl, status, cb.answer, cb.attempt).map {
            case Right(b) => b
            case Left(m) => Logger.error(s"Failed to save callback: $m")
              false
          }.recover {
            case e: Exception =>
              Logger.error(s"Failed to save callback: ${e.getMessage}")
              throw new ServiceUnavailableException(s"failed to save callback: ${e.getMessage}")
          }
        case None => Future(false)
      }).toSeq
      )
  }
}