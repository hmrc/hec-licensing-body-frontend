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
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.{Call, Cookie}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, TaxCheckVerificationAttempts, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService

import scala.concurrent.ExecutionContext.Implicits.global

class StartControllerSpec extends ControllerSpec with SessionSupport with JourneyServiceSupport {

  override val overrideBindings: List[GuiceableModule] =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  override def additionalConfig =
    Configuration(
      ConfigFactory.parseString(
        """
          |play.i18n.langs = ["en", "cy", "fr"]
          |""".stripMargin
      )
    )

  val controller: StartController = instanceOf[StartController]

  def fakeRequestWithLang(languageCode: String) = FakeRequest().withCookies(Cookie("PLAY_LANG", languageCode))

  "StartController" when {

    "handling requests to start" must {

      def performAction(languageCode: String) =
        controller.start(fakeRequestWithLang(languageCode))

      val hecTaxCheckCode = HECTaxCheckCode("XNFFGBDD6")

      val currentSession =
        HECSession(
          UserAnswers.empty.copy(taxCheckCode = Some(hecTaxCheckCode)),
          None,
          Map(hecTaxCheckCode -> TaxCheckVerificationAttempts(2, None)),
          isScotNIPrivateBeta = Some(false)
        )

      val newSession = HECSession(UserAnswers.empty, None, Map.empty, isScotNIPrivateBeta = Some(false))

      "show an error page" when {

        "there is an error getting an existing  session" in {
          mockGetSession(Left(Error("")))

          assertThrows[RuntimeException](await(performAction("en")))
        }

        "there is an error storing a new session" in {
          inSequence {
            mockGetSession(Right(None))
            mockStoreSession(newSession)(Left(Error("")))
          }

          assertThrows[RuntimeException](await(performAction("en")))

        }

        "the language is not recognised" in {
          inSequence {
            mockGetSession(Right(None))
            mockStoreSession(newSession)(Right(()))
          }

          assertThrows[RuntimeException](await(performAction("fr")))
        }

      }

      "redirect to the first page of the journey" when {

        "a new session has been created with empty verification attempts, if no previous session found" in {
          val firstPage = Call("", "/first")

          inSequence {
            mockGetSession(Right(None))
            mockStoreSession(newSession)(Right(()))
            mockFirstPge(firstPage)
          }

          checkIsRedirect(performAction("en"), firstPage)
        }

        "a new session has been created with verification attempts of previous session " in {
          val firstPage = Call("", "/first")

          inSequence {
            mockGetSession(currentSession.copy(isScotNIPrivateBeta = None))
            mockStoreSession(
              newSession.copy(verificationAttempts = currentSession.verificationAttempts, isScotNIPrivateBeta = None)
            )(Right(()))
            mockFirstPge(firstPage)
          }

          checkIsRedirect(performAction("cy"), firstPage)
        }

      }

    }

    "handling requests to the scotNI private beta start endpoint" must {

      "write the isScotNIPrivateBeta flag to true if no session exists yet" in {
        val firstPage = Call("", "/first")

        inSequence {
          mockGetSession(Right(None))
          mockStoreSession(HECSession(UserAnswers.empty, None, Map.empty, isScotNIPrivateBeta = Some(true)))(Right(()))
          mockFirstPge(firstPage)
        }

        checkIsRedirect(controller.scotNIPrivateBetaStart(fakeRequestWithLang("en")), firstPage)
      }

    }

  }

}
