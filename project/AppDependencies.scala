import sbt._

object AppDependencies {
  val bootStrapVersion = "9.13.0"
  val hmrcMongoVersion = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"         % bootStrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                 % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"         % "12.6.0",
    "org.typelevel"     %% "cats-core"                          % "2.10.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootStrapVersion    % Test,
    "org.scalamock"          %% "scalamock"               % "5.2.0"             % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % hmrcMongoVersion    % Test
  )
}