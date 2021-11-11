import play.core.PlayVersion
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-28" % "5.16.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "0.56.0",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"         % "1.25.0-play-28",
    "org.typelevel"     %% "cats-core"                  % "2.6.1",
    "ai.x"              %% "play-json-extensions"       % "0.42.0",
    "com.github.kxbmap" %% "configs"                    % "0.4.4"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28" % "5.16.0"            % Test,
    "org.scalatest"          %% "scalatest"              % "3.2.10"            % Test,
    "org.jsoup"               % "jsoup"                  % "1.14.2"            % Test,
    "com.typesafe.play"      %% "play-test"              % PlayVersion.current % Test,
    "org.scalamock"          %% "scalamock"              % "4.2.0"             % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test"     % "5.0.0-play-28"     % Test,
    "com.vladsch.flexmark"    % "flexmark-all"           % "0.62.2"            % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"     % "5.1.0"             % "test, it"
  )
}
