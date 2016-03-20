import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object MicroServiceBuild extends Build with MicroService {

  override val appName = "push-notification"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.PlayImport._
  import play.core.PlayVersion

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % "4.2.1",
    "uk.gov.hmrc" %% "play-health" % "1.1.0",
    "uk.gov.hmrc" %% "play-config" % "2.0.1",
    "uk.gov.hmrc" %% "play-json-logger" % "2.1.1",
    "uk.gov.hmrc" %% "play-authorisation" % "3.1.0"
  )

  abstract class TestDependencies(val scope: String) {
    val test: Seq[ModuleID] = Seq(
      "uk.gov.hmrc" %% "hmrctest" % "1.4.0" % scope,
      "org.scalatest" %% "scalatest" % "2.2.6" % scope,
      "org.pegdown" % "pegdown" % "1.6.0" % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it") {
    override val test: Seq[ModuleID] = Seq(
      "uk.gov.hmrc" %% "hmrctest" % "1.4.0" % scope,
      "org.scalatest" %% "scalatest" % "2.2.6" % scope,
      "org.pegdown" % "pegdown" % "1.6.0" % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
      "org.scalatestplus" %% "play" % "1.2.0" % scope,
      "com.github.tomakehurst" % "wiremock" % "1.52" % scope
    )
  }

  def apply() = compile ++ Test.test ++ IntegrationTest.test

}

