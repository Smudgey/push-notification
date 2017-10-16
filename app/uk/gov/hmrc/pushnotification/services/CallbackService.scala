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

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import org.joda.time.Duration
import play.api.Logger
import uk.gov.hmrc.http.ServiceUnavailableException
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.pushnotification.domain._
import uk.gov.hmrc.pushnotification.repository.{CallbackRepositoryApi, PushMessageCallbackPersist}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[CallbackService])
trait CallbackServiceApi extends BatchProcessor {

  def getUndeliveredCallbacks: Future[Option[CallbackBatch]]

  def updateCallbacks(batchUpdate: CallbackResultBatch): Future[Boolean]
}

@Singleton
class CallbackService @Inject()(repository: CallbackRepositoryApi, lockRepository: LockRepository) extends CallbackServiceApi {
  override val maxConcurrent: Int = 10

  val getDeliveredLockKeeper = new LockKeeper {
    override def repo: LockRepository = lockRepository

    override def lockId: String = "getUndeliveredCallbacks"

    override val forceLockReleaseAfter: Duration = Duration.standardMinutes(2)
  }

  override def getUndeliveredCallbacks: Future[Option[CallbackBatch]] = {
    getDeliveredLockKeeper.tryLock {
      repository.findUndelivered(100).map((batch: Seq[PushMessageCallbackPersist]) =>
        CallbackBatch(batch.map(cb =>
          Callback(cb.callbackUrl, cb.status, Response(cb.messageId, cb.answer), cb.attempts)
        ))
      ).recover {
        case e: Exception =>
          Logger.error(s"Unable to retrieve undelivered callbacks: ${e.getMessage}")
          throw new ServiceUnavailableException(s"Unable to retrieve undelivered callbacks")
      }
    }
  }

  override def updateCallbacks(batchUpdate: CallbackResultBatch): Future[Boolean] = {
    processBatch[CallbackResult](batchUpdate.batch, repository.update)
  }
}