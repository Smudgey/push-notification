import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import play.sbt.PlayImport._

object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "6.11.0"
  private val domainVersion = "5.0.0"
  private val scalaJVersion = "2.3.0"
  private val playHmrcApiVersion = "2.0.0"
  private val playReactiveMongo = "5.2.0"
  private val circuitBreaker = "3.2.0"
  private val scalaTestVersion = "3.0.1"
  private val reactiveMongoTest = "3.0.0"
  private val pegdownVersion = "1.6.0"
  private val wireMockVersion = "2.3.1"
  private val hmrctestVersion = "3.0.0"
  private val cucumberVersion = "1.2.5"
  private val mockitoVersion = "2.7.14"
  private val mongoLockVersion = "5.0.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "reactive-circuit-breaker" % circuitBreaker,
    "uk.gov.hmrc" %% "play-hmrc-api" % playHmrcApiVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactiveMongo,
    "uk.gov.hmrc" %% "mongo-lock" % mongoLockVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }
  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrctestVersion % "test,it",
        "org.scalaj" %% "scalaj-http" % scalaJVersion % "test,it",
        "org.scalatest" %% "scalatest" % scalaTestVersion % "test,it",
        "org.pegdown" % "pegdown" % pegdownVersion % "test,it",
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it",
        "com.github.tomakehurst" % "wiremock" % wireMockVersion % "test,it",
        "info.cukes" %% "cucumber-scala" % cucumberVersion % "test,it",
        "info.cukes" % "cucumber-junit" % cucumberVersion % "test,it",
        "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTest % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "hmrctest" % hmrctestVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.github.tomakehurst" % "wiremock" % wireMockVersion % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
