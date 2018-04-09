import TestPhases.oneForkedJvmPerTest
import play.sbt.routes.RoutesKeys._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, _}
import uk.gov.hmrc.SbtGitInfo.gitInfo
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._


name:= "push-notification"

lazy val root = (project in file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)

routesImport ++= Seq("uk.gov.hmrc.domain._", "uk.gov.hmrc.pushnotification.binder.Binders._")
publishingSettings
unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
defaultSettings()

scalaVersion := "2.11.11"
crossScalaVersions := Seq("2.11.11")

PlayKeys.playDefaultPort := 8246

libraryDependencies ++= AppDependencies()
retrieveManaged := true
evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
routesGenerator := StaticRoutesGenerator

Keys.fork in IntegrationTest := false
unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base => Seq(base / "it")).value
addTestReportOption(IntegrationTest, "int-test-reports")
testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value)
parallelExecution in IntegrationTest := false

resolvers ++= Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.jcenterRepo
)

resourceGenerators in Compile += Def.task {
  val commitMfFile = target.value / "metadata.mf"
  val gitMetaData = gitInfo.map{s => {
    val value = if (s._1 == "Git-Describe") s._2.substring(1) else s._2
    val key = s._1.replaceAll("-", "_")

    s"$key='$value'\n"
  }}.mkString
  IO.write(commitMfFile, gitMetaData)
  Seq(commitMfFile)
}

val metadataMfTask = taskKey[File]("metadata-mf")
metadataMfTask := target.value / "metadata.mf"
artifact in (Compile, metadataMfTask) ~= { (art:Artifact) =>
  art.copy("metadata", "mf", "mf")
}
addArtifact(artifact in (Compile, metadataMfTask), metadataMfTask in Compile)

