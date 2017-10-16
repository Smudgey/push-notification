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

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class BatchProcessorSpec extends UnitSpec with ScalaFutures {
  private class TestProcessor extends BatchProcessor {
    override val maxConcurrent: Int = 3
  }

  private trait Setup {
    val processor = new TestProcessor

    val someData: Seq[Int] = List.range(0,1000)
  }

  private trait Success extends Setup {
    var expected = 0

    def asserting(i: Int): Future[Either[String, Int]] = {
      i shouldBe expected

      expected = i + 1

      successful(Right(i))
    }
  }

  private trait Failure extends Setup {
    def failing(i: Int): Future[Either[String, Int]] =
      if (i % 789 == 0) {
        successful(Left("a bad thing happened"))
      } else {
        successful(Right(i))
      }
  }

  private trait Catastrophic extends Setup {
    def catastrophic(i: Int): Future[Either[String, Int]] =
      if (i % 789== 0) {
        failed(new Exception("a bad thing happened"))
      } else {
        successful(Right(i))
      }
  }

  "BatchProcessor processBatch" should {
    "process the batch in sequential chunks" in new Success {
      val result: Boolean = await(processor.processBatch[Int](someData, asserting))

      result shouldBe true
    }

    "return false given an issue processing the batch" in new Failure {
      val result: Boolean = await(processor.processBatch[Int](someData, failing))

      result shouldBe false
    }

    "throw an exception given an exception processing the batch" in new Catastrophic {
      val e = intercept[HttpException] {
        await(processor.processBatch[Int](someData, catastrophic))
      }

      e.responseCode shouldBe 500
      e.message shouldBe """processGroup failed for value="0""""
    }
  }
}
