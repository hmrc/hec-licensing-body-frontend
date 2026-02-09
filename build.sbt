import scoverage.ScoverageKeys
import play.sbt.PlayScala

val appName = "hec-licensing-body-frontend"

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / majorVersion := 1

lazy val it = project
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= AppDependencies.itDependencies
  )

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageEnabled := true,
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*(config|testonly|views).*;.*(BuildInfo|Routes).*;",
    ScoverageKeys.coverageExcludedFiles :=  "<empty>;Reverse.*;.*/util.Logging;.*/util.TimeProvider;.*/util.HttpResponseOps;.*/models.ids.CRN;",
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(ScoverageSbtPlugin)
  .enablePlugins(SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    majorVersion := 1,
    scalaVersion := "3.3.6",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always),
    Assets / pipelineStages := Seq(gzip),
    scalacOptions := Seq(
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=unused.import&src=html/.*:s",
      "-feature",
      "-deprecation"
    ),
    // ***************
    Compile / doc / sources := Seq.empty,
    scalafmtOnCompile := true
  )
  .settings(scoverageSettings: _*)




