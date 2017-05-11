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

package uk.gov.hmrc.pushnotification.config

import javax.inject.{Inject, Provider}

import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.lock.{LockMongoRepository, LockRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HttpGet

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {
    bind(classOf[HttpGet]).to(classOf[WSHttp])
    bind(classOf[AuditConnector]).to(classOf[MicroserviceAuditConnector])
    bind(classOf[DB]).toProvider(classOf[MongoDbProvider])
    bind(classOf[LockRepository]).toProvider(classOf[LockRepositoryProvider])

    bind(classOf[String]).annotatedWith(named("pushRegistrationUrl")).toInstance(baseUrl("push-registration"))
    bind(classOf[String]).annotatedWith(named("authUrl")).toInstance(baseUrl("auth"))
    bind(classOf[Int]).annotatedWith(named("sendNotificationMaxRetryAttempts")).toInstance(configuration.getInt("sendNotificationMaxRetryAttempts").getOrElse(3))
    bind(classOf[Int]).annotatedWith(named("clientCallbackMaxRetryAttempts")).toInstance(configuration.getInt("clientCallbackMaxRetryAttempts").getOrElse(3))
    bind(classOf[Int]).annotatedWith(named("unsentNotificationsMaxBatchSize")).toInstance(configuration.getInt("unsentNotificationsMaxBatchSize").getOrElse(100))

    bind(classOf[ConfidenceLevel]).toInstance(ConfidenceLevel.fromInt(configuration.getInt("controllers.confidenceLevel")
      .getOrElse(throw new RuntimeException("The service has not been configured with a confidence level"))))
  }
}

class MongoDbProvider @Inject() (reactiveMongoComponent: ReactiveMongoComponent) extends Provider[DB] {
  def get = reactiveMongoComponent.mongoConnector.db()
}

class LockRepositoryProvider @Inject() (mongo: DB) extends Provider[LockRepository] {
  def get = LockMongoRepository(() => mongo)
}