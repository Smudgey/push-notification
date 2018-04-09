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

package uk.gov.hmrc.pushnotification.connector

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PushRegistrationConnector])
trait PushRegistrationConnectorApi {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  def http: CoreGet

  def serviceUrl: String

  def url(path: String) = s"$serviceUrl$path"

  def endpointsForAuthId(id: String)(implicit r: HttpReads[Map[String, String]], ec: ExecutionContext): Future[Map[String, String]] = {
    http.GET[Map[String, String]](url = url(s"/push/endpoint/$id")).recover {
      case ex: HttpException if ex.responseCode == 404 => throw new NotFoundException(s"no endpoints found for '$id'")
    }
  }
}

@Singleton
class PushRegistrationConnector @Inject()(@Named("pushRegistrationUrl") val serviceUrl: String,
                                          val http: CoreGet) extends PushRegistrationConnectorApi