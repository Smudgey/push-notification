package uk.gov.hmrc.api

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.api.AuthType.AuthTypeNone
import uk.gov.hmrc.api.HttpVerb.Get
import uk.gov.hmrc.api.Status.Prototype

trait GenerateDefinition {
  def generate(): Definition
}

object GenerateDefinition extends GenerateDefinition {

  import Definition.formats

  override def generate(): JsValue = {

    val versions = Seq(
      {
        //1.0
        val ping = EndPoint("/ping", "ping", Get, AuthTypeNone)

        Version("1.0", Prototype, Seq(ping))
      }
    )

    val api = API("Push Notification",
      "Service manage push notification tokens and the notify action",
      "push-notification",
      versions)

    val scopes = Seq(
      Scope("push-notification",
        "push-notification",
        "Global scope for managing and sending push notifications")
    )

    val definition = Definition(scopes, api)

    Json.toJson(definition)
  }
}

