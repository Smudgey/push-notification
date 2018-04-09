/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.Logger
import uk.gov.hmrc.http.HttpException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait BatchProcessor {
  val maxConcurrent: Int

  def processBatch[T](batch: Seq[T], update: (T) => Future[Either[String, Any]]): Future[Boolean] = {
    val batchResultsItr: Iterator[Future[Boolean]] = for (
      group: Seq[T] <- batch.grouped(maxConcurrent);
      results: Future[Boolean] <- processGroup(group, update)
    ) yield results

    Future.sequence(batchResultsItr.toSeq)
      .map { (v: Seq[Boolean]) => v.foldLeft(true)(_ && _) }
  }

  private def processGroup[T](group: Seq[T], update: (T) => Future[Either[String, Any]]): Seq[Future[Boolean]] = {
    group.map { t =>
      update(t).map {
        case Right(_) =>
          true
        case Left(msg) =>
          Logger.warn(s"""processGroup failed for value="$t": $msg""")
          false
      }.recover {
        case e: Exception =>
          Logger.error(s"""processGroup failed for value="$t": ${e.getMessage}""")
          throw new HttpException(message = s"""processGroup failed for value="$t"""", responseCode = 500)
      }
    }
  }
}
