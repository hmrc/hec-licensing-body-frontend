import sbt._

object AppDependencies {
  val bootStrapVersion = "10.5.0"
  val hmrcMongoVersion = "2.11.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"         % bootStrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                 % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"         % "12.26.0",
    "org.typelevel"     %% "cats-core"                          % "2.13.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootStrapVersion    % Test,
    "org.scalamock"          %% "scalamock"               % "7.5.0"             % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % hmrcMongoVersion    % Test
  )

  val itDependencies: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootStrapVersion % Test
  )
}
