import play.core.PlayVersion
import sbt._

object AppDependencies {
  val bootStrapVersion = "6.3.0"
  val hmrcMongoVersion = "0.68.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-28" % bootStrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc"         % "3.22.0-play-28",
    "org.typelevel"     %% "cats-core"                  % "2.8.0",
    "ai.x"              %% "play-json-extensions"       % "0.42.0",
    "com.github.kxbmap" %% "configs"                    % "0.6.1"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootStrapVersion    % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.12"            % Test,
    "org.jsoup"               % "jsoup"                   % "1.15.2"            % Test,
    "com.typesafe.play"      %% "play-test"               % PlayVersion.current % Test,
    "org.scalamock"          %% "scalamock"               % "5.2.0"             % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion    % Test,
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2"            % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"             % "test, it"
  )
}
