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

import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
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

  val appConfig = instanceOf[AppConfig]

  "HECTaxCheckCodeController" when {

    "handling requests to display the tax check code page" must {

      def performAction(): Future[Result] = controller.hecTaxCheckCode(FakeRequest())

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
              input.attr("value") shouldBe taxCheckCode.value

            }
          )
        }

      }

    }

    "handling submits on the tax check code page" must {

      def performAction(data: (String, String)*): Future[Result] =
        controller.hecTaxCheckCodeSubmit(FakeRequest().withMethod(POST).withFormUrlEncodedBody(data: _*))

      behave like sessionDataActionBehaviour(() => performAction())

      val currentSession = HECSession(UserAnswers.empty, None)

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.HECTaxCheckCodeController.hecTaxCheckCode, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction(),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.required")
          )
        }

        "the submitted value is too long" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.HECTaxCheckCodeController.hecTaxCheckCode, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("taxCheckCode" -> "1234567890"),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.tooLong")
          )
        }

        "the submitted value is too short" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.HECTaxCheckCodeController.hecTaxCheckCode, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("taxCheckCode" -> "12345678"),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.tooShort")
          )
        }

        "the submitted value contains characters which are not letters or digits" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.HECTaxCheckCodeController.hecTaxCheckCode, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("taxCheckCode" -> "12345678="),
            messageFromMessageKey("taxCheckCode.title"),
            messageFromMessageKey("taxCheckCode.error.nonAlphanumericChars")
          )
        }

        "th submitted value contains all alphanumeric characters but some of them are invalid" in {
          List('I', 'O', 'S', 'U', 'V', 'W', '0', '1', '5').foreach { invalidChar =>
            withClue(s"For char '$invalidChar': '") {
              val value = s"ABCABCAB$invalidChar"

              inSequence {
                mockGetSession(currentSession)
                mockJourneyServiceGetPrevious(routes.HECTaxCheckCodeController.hecTaxCheckCode, currentSession)(
                  mockPreviousCall
                )
              }

              checkFormErrorIsDisplayed(
                performAction("taxCheckCode" -> value),
                messageFromMessageKey("taxCheckCode.title"),
                messageFromMessageKey("taxCheckCode.error.invalidAlphanumericChars")
              )

            }
          }
        }

      }

      "show technical error page" when {

        "valid data is submitted but there is a problem updating and getting the next page" in {
          val taxCheckCode = HECTaxCheckCode("223ABC789")

          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceUpdateAndNext(
              routes.HECTaxCheckCodeController.hecTaxCheckCode,
              currentSession,
              currentSession.copy(userAnswers = currentSession.userAnswers.copy(taxCheckCode = Some(taxCheckCode)))
            )(Left(Error(new RuntimeException("Oh no!"))))
          }
          assertThrows[RuntimeException](await(performAction("taxCheckCode" -> taxCheckCode.value)))

        }

      }

      "continue to the next page" when {

        "valid data is submitted and updating and getting the next page is successful" in {
          val taxCheckCode = HECTaxCheckCode("223ABC789")

          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceUpdateAndNext(
              routes.HECTaxCheckCodeController.hecTaxCheckCode,
              currentSession,
              currentSession.copy(userAnswers = currentSession.userAnswers.copy(taxCheckCode = Some(taxCheckCode)))
            )(Right(mockNextCall))
          }

          checkIsRedirect(
            performAction("taxCheckCode" -> "  223 Ab c  7 89  "),
            mockNextCall
          )
        }

      }

    }

  }

}
