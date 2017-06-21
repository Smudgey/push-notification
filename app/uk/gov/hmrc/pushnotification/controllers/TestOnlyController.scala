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

package uk.gov.hmrc.pushnotification.controllers

import javax.inject.{Singleton, Inject}

import play.api.libs.json.Json
import play.api.mvc.Action
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushnotification.repository.{PushNotificationMongoRepositoryTest, NotificationPersist}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject()(notificationRepository: PushNotificationMongoRepositoryTest) extends BaseController with ErrorHandling {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
  implicit val format = Json.format[NotificationPersist]

  def dropNotificationMongo() = Action.async {
    notificationRepository.removeAllRecords().map(_ => Ok)
  }

  def findByInternalId(internalId:String) = Action.async {
    notificationRepository.findByInternalId(internalId).map(res => Ok(Json.toJson(res)))
  }

  def findByEndpoint(token:String, internalId:String) = Action.async {
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    implicit val format = Json.format[NotificationPersist]

    notificationRepository.findByEndpoint(s"default-platform-arn/stubbed/default-platform-arn/$token", internalId).map(res => Ok(Json.toJson(res)))
  }
}

