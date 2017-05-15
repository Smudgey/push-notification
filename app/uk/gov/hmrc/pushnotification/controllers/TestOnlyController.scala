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

import com.google.inject.ImplementedBy
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushnotification.repository.{PushNotificationMongoRepositoryTest, NotificationPersist, PushNotificationRepositoryApi, PushNotificationMongoRepository}
import play.api.Logger
import uk.gov.hmrc.play.http.{UnauthorizedException, HeaderCarrier}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.pushnotification.services.NotificationsServiceApi

import scala.concurrent.{ExecutionContext, Future}




//@ImplementedBy(classOf[TestOnlyController])
//trait TestOnlyControllerI extends BaseController with ErrorHandling {
//}

@Singleton
class TestOnlyController @Inject()(notificationRepository: PushNotificationMongoRepositoryTest) extends BaseController with ErrorHandling {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def dropNotificationMongo() = Action.async {
    notificationRepository.removeAllRecords().map(_ => Ok)
  }

  def findByEndpoint(token:String, internalId:String) = Action.async {

println(" FIND BY ENDPOINT " + token + " internalId " + internalId)
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    implicit val format = Json.format[NotificationPersist]

    notificationRepository.findByEndpoint(s"default-platform-arn/stubbed/default-platform-arn/$token", internalId).map(res => Ok(Json.toJson(res)))
  }
}

