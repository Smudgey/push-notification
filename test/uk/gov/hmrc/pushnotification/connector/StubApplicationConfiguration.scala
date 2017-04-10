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

package uk.gov.hmrc.pushnotification.connector

trait StubApplicationConfiguration {

  val config = Map[String, Any](
    "auditing.enabled" -> false,
    "microservice.services.datastream.host" -> "host",
    "microservice.services.datastream.port" -> "1234",
    "microservice.services.datastream.enabled" -> false,
    "microservice.services.service-locator.enabled" -> false,
    "microservice.services.service-locator.host" -> "host",
    "microservice.services.service-locator.port" -> "2345",
    "appName" -> "personal-income",
    "microservice.services.auth.host" -> "host",
    "microservice.services.auth.port" -> "3456",
    "microservice.services.ntc.host" -> "host",
    "microservice.services.ntc.port" -> "4567",
    "router.regex" -> ".*",
    "router.prefix" -> "/sandbox",
    "router.regex" -> "X-MOBILE-USER-ID",
    "mongodb.uri" -> "mongodb://localhost:27017/pushnotification"
  )
}
