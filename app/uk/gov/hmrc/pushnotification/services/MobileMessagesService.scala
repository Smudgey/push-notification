/*
 * Copyright 2016 HM Revenue & Customs
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

import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.pushnotification.config.MicroserviceAuditConnector
import uk.gov.hmrc.pushnotification.connector._
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait pushnotificationService {
  def ping()(implicit hc:HeaderCarrier): Future[Boolean]
}

trait LivepushnotificationService extends pushnotificationService {
  def authConnector: AuthConnector

  def audit(service:String, details:Map[String, String])(implicit hc:HeaderCarrier) = {
    def auditResponse(): Unit = {
      MicroserviceAuditConnector.sendEvent(
        DataEvent("mobile-messages", "ServiceResponseSent",
          tags = Map("transactionName" -> service),
          detail = details))
    }
  }

  def withAudit[T](service: String, details: Map[String, String])(func:Future[T])(implicit hc:HeaderCarrier) = {
    audit(service, details) // No need to wait!
    func
  }

  def ping()(implicit hc:HeaderCarrier): Future[Boolean]

}

object SandboxpushnotificationService extends pushnotificationService with FileResource {

  def ping()(implicit hc:HeaderCarrier): Future[Boolean] = Future.successful(true)

}

object LivepushnotificationService extends LivepushnotificationService {
  override val authConnector: AuthConnector = AuthConnector

  def ping()(implicit hc:HeaderCarrier): Future[Boolean] = Future.successful(true)
}
