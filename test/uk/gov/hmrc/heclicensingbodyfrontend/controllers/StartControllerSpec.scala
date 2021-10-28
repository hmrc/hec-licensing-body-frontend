/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Call
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

  val controller: StartController = instanceOf[StartController]

  "StartController" when {

    "handling requests to start" must {

      def performAction() = controller.start(FakeRequest())

      val hecTaxCheckCode = HECTaxCheckCode("XNFFGBDD6")

      val currentSession =
        HECSession(
          UserAnswers.empty.copy(taxCheckCode = Some(hecTaxCheckCode)),
          None,
          Map(hecTaxCheckCode -> TaxCheckVerificationAttempts(2, None))
        )

      val newSession = HECSession(UserAnswers.empty, None)

      "show an error page" when {

        "there is an error getting an existing  session" in {

          mockGetSession(Left(Error("")))
          status(performAction()) shouldBe INTERNAL_SERVER_ERROR
        }

        "there is an error storing a new session" in {

          mockGetSession(Right(None))
          mockStoreSession(newSession)(Left(Error("")))

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR
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

          checkIsRedirect(performAction(), firstPage)
        }

        "a new session has been created with verification attempts of previous session " in {
          val firstPage = Call("", "/first")

          inSequence {

            mockGetSession(currentSession)
            mockStoreSession(newSession.copy(verificationAttempts = currentSession.verificationAttempts))(Right(()))
            mockFirstPge(firstPage)
          }

          checkIsRedirect(performAction(), firstPage)
        }

      }

    }

  }

}
