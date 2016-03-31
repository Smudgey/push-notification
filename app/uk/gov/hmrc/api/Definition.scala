package uk.gov.hmrc.api

import play.api.libs.json.Json
import uk.gov.hmrc.api.ThrottlingTier.Unlimited


trait ThrottlingTier
object ThrottlingTier {
  case object Unlimited extends ThrottlingTier {
    override def toString: String = "UNLIMITED"
  }
}

trait Status
object Status {
  case object Prototype extends Status {
    override def toString: String = "PROTOTYPE"
  }
  case object Published extends Status {
    override def toString: String = "PUBLISHED"
  }
  case object Deprecated extends Status{
    override def toString: String = "DEPRECATED"
  }
  case object Retired extends Status {
    override def toString: String = "RETIRED"
  }
}

trait AuthType
object AuthType {
  case object AuthTypeNone extends AuthType {
    override def toString: String = "NONE"
  }
  case object Application extends AuthType {
    override def toString: String = "APPLICATION"
  }
  case object User extends AuthType {
    override def toString: String = "USER"
  }
}

trait HttpVerb

object HttpVerb {
  case object Get extends HttpVerb {
    override def toString: String = "GET"
  }
  case object Post extends HttpVerb {
    override def toString: String = "POST"
  }
  case object Put extends HttpVerb {
    override def toString: String = "PUT"
  }
  case object Head extends HttpVerb {
    override def toString: String = "HEAD"
  }
  case object Options extends HttpVerb {
    override def toString: String = "OPTIONS"
  }
  case object Delete extends HttpVerb {
    override def toString: String = "DELETE"
  }
}

case class EndPoint(uriPattern : String, endpointName : String, method : HttpVerb, authType : AuthType, throttlingTier : Option[ThrottlingTier] = Some(Unlimited), scope : Option[String] = None)

case class Version(version : String, status : Status, endpoints : Seq[EndPoint])

case class API(name : String, description : String, context : String, versions : Seq[Version])

case class Scope(key : String, name : String, description : String)

case class Definition(scopes : Seq[Scope], api : API)

object Definition {

  implicit val formats = {

    implicit val throttlingTier = Json.format[ThrottlingTier]
    implicit val authType = Json.format[AuthType]
    implicit val httpVerb = Json.format[HttpVerb]

    implicit val endPoint = Json.format[EndPoint]
    implicit val version = Json.format[Version]
    implicit val api = Json.format[API]
    implicit val scope = Json.format[Scope]

    Json.format[Definition]
  }
}