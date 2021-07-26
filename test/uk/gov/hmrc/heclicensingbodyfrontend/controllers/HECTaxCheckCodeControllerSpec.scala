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
import play.api.mvc.{Call, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class HECTaxCheckCodeControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  val controller = instanceOf[HECTaxCheckCodeController]

  "HECTaxCheckCodeController" when {

    "handling requests to display the tax check code page" must {

      def performAction(): Future[Result] = controller.hecTaxCheckCode(FakeRequest())

      behave like sessionDataActionBehaviour(performAction)

      "show the page" when {

        "session data is found" in {
          val taxCheckCode = HECTaxCheckCode("ABC DEF 123")
          mockGetSession(
            HECSession(
              UserAnswers.empty.copy(
                taxCheckCode = Some(taxCheckCode)
              )
            )
          )

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("taxCheckCode.title"),
            { doc =>
              val button = doc.select("form")
              button.attr("action") shouldBe routes.HECTaxCheckCodeController.hecTaxCheckCodeSubmit().url

              val link = doc.select("p > .govuk-link")
              link.text shouldBe messageFromMessageKey("taxCheckCode.link")

              val input = doc.select(".govuk-input")
              input.attr("value") shouldBe taxCheckCode.value

            }
          )
        }

      }

    }

    "handling submits on the tax check code page" must {

      def performAction(data: (String, String)*): Future[Result] =
        controller.hecTaxCheckCodeSubmit(FakeRequest().withFormUrlEncodedBody(data: _*))

      behave like sessionDataActionBehaviour(() => performAction())

      val currentSession = HECSession(UserAnswers.empty)

      "show a form error" when {

        "nothing has been submitted" in {
          mockGetSession(currentSession)

          checkFormErrorIsDisplayed(
            performAction(),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.required")
          )
        }

        "the submitted value is too long" in {
          mockGetSession(currentSession)

          checkFormErrorIsDisplayed(
            performAction("taxCheckCode" -> "1234567890"),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.tooLong")
          )
        }

        "the submitted value is too short" in {
          mockGetSession(currentSession)

          checkFormErrorIsDisplayed(
            performAction("taxCheckCode" -> "12345678"),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.tooShort")
          )
        }

        "the submitted value contains characters which are not letters or digits" in {
          mockGetSession(currentSession)

          checkFormErrorIsDisplayed(
            performAction("taxCheckCode" -> "12345678="),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.pattern")
          )
        }

      }

      "show an error page" when {

        "valid data is submitted but there is a problem updating and getting the next page" in {
          val taxCheckCode = HECTaxCheckCode("123ABC789")

          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceUpdateAndNext(
              routes.HECTaxCheckCodeController.hecTaxCheckCode(),
              currentSession,
              currentSession.copy(userAnswers = currentSession.userAnswers.copy(taxCheckCode = Some(taxCheckCode)))
            )(Left(Error(new RuntimeException("Oh no!"))))
          }

          status(performAction("taxCheckCode" -> taxCheckCode.value)) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "continue to the next page" when {

        "valid data is submitted and updating and getting the next page is successful" in {
          val taxCheckCode = HECTaxCheckCode("123ABC789")
          val next         = Call("", "/next")

          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceUpdateAndNext(
              routes.HECTaxCheckCodeController.hecTaxCheckCode(),
              currentSession,
              currentSession.copy(userAnswers = currentSession.userAnswers.copy(taxCheckCode = Some(taxCheckCode)))
            )(Right(next))
          }

          checkIsRedirect(
            performAction("taxCheckCode" -> "  123 Ab c  7 89  "),
            next
          )
        }

      }

    }

  }

}
