/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.heclicensingbodyfrontend.controllers

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.i18n.Lang
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.{Cookie, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.models.{HECSession, HECTaxCheckCode, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class HECTaxCheckCodeControllerCYSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {
  override implicit val lang: Lang = Lang("cy")

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  val controller = instanceOf[HECTaxCheckCodeController]

  val appConfig = instanceOf[AppConfig]

  override def additionalConfig: Configuration = super.additionalConfig.withFallback(
    Configuration(
      ConfigFactory.parseString(
        s"""
           | features.welsh-language-support = true
           | play.i18n.langs = ["en", "cy"]
           |""".stripMargin
      )
    )
  )

  "HECTaxCheckCodeController" when {

    "handling requests to display the tax check code page and welsh toggle is enabled" must {

      def performAction(): Future[Result] =
        controller.hecTaxCheckCode(FakeRequest().withCookies(Cookie("PLAY_LANG", "cy")))

      behave like sessionDataActionBehaviour(performAction)

      "show the page" when {

        "session data is found" in {
          val taxCheckCode = HECTaxCheckCode("ABC DEF 123")
          val session      =
            HECSession(
              UserAnswers.empty.copy(
                taxCheckCode = Some(taxCheckCode)
              ),
              None
            )

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.HECTaxCheckCodeController.hecTaxCheckCode, session)(
              routes.StartController.start
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("taxCheckCode.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe appConfig.licencingBodyStartUrl

              val button = doc.select("form")
              button.attr("action") shouldBe routes.HECTaxCheckCodeController.hecTaxCheckCodeSubmit.url

              val link = doc.select("p > .govuk-link")
              link.text           should include(messageFromMessageKey("taxCheckCode.link"))
              link.attr("href") shouldBe appConfig.taxCheckGuidanceUrl

              val input = doc.select(".govuk-input")
              input.attr("value")                          shouldBe taxCheckCode.value
              doc.select(".hmrc-language-select__list").text should include regex "English"
              doc.select(".hmrc-language-select__list").text should include regex "Cymraeg"

            }
          )
        }

      }

    }
  }
}
