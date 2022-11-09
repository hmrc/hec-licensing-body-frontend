import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import wartremover.Wart
import wartremover.WartRemover.autoImport.wartremoverErrors

val appName = "hec-licensing-body-frontend"

val silencerVersion = "1.7.9"

lazy val wartremoverSettings =
  Seq(
    (Compile / compile / wartremoverErrors) ++= Warts.allBut(
      Wart.DefaultArguments,
      Wart.ImplicitConversion,
      Wart.ImplicitParameter,
      Wart.Nothing,
      Wart.Overloading,
      Wart.ToString
    ),
    wartremover.WartRemover.autoImport.wartremoverExcluded ++=
      (Compile / routes).value ++
        (baseDirectory.value ** "*.sc").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala"),
    (Test / compile / wartremoverErrors) --= Seq(
      Wart.Any,
      Wart.GlobalExecutionContext,
      Wart.NonUnitStatements,
      Wart.Null,
      Wart.PublicInference
    )
  )

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*(config|testonly|views).*;.*(BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    play.sbt.PlayScala,
    SbtAutoBuildPlugin,
    SbtGitVersioning,
    SbtDistributablesPlugin
  )
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full))
  .settings(addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full))
  .settings(
    majorVersion := 1,
    scalaVersion := "2.12.16",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    Assets / pipelineStages := Seq(gzip),
    // ***************
    // Use the silencer plugin to suppress warnings
    scalacOptions += "-P:silencer:pathFilters=routes",
    Test / scalacOptions --= Seq("-Ywarn-value-discard"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    // ***************
    Compile / doc / sources := Seq.empty
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(routesImport := Seq.empty)
  .settings(TwirlKeys.templateImports := Seq.empty)
  .settings(wartremoverSettings: _*)
  .settings(scoverageSettings: _*)
  .settings(scalafmtOnCompile := true)
  .settings(PlayKeys.playDefaultPort := 10107)
