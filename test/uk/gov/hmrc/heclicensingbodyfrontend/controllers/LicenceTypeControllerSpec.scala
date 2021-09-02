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
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LicenceTypeControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {
  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  val controller = instanceOf[LicenceTypeController]

  val taxCheckCode = HECTaxCheckCode("ABC DEF 123")

  "LicenceTypeController" when {

    "handling requests to the licence type page" must {

      def performAction(): Future[Result] = controller.licenceType(FakeRequest())

      behave like sessionDataActionBehaviour(performAction)

      "show the page" when {

        "session data is found" in {
          val taxCheckCode = HECTaxCheckCode("ABC DEF 123")
          val session      =
            HECSession(
              UserAnswers.empty.copy(
                taxCheckCode = Some(taxCheckCode)
              )
            )

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType(), session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("licenceType.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              val selectedOptions = doc.select(".govuk-radios__input[checked]")
              selectedOptions.isEmpty shouldBe true

              val form = doc.select("form")
              form.attr("action") shouldBe routes.LicenceTypeController.licenceTypeSubmit().url

            }
          )
        }

      }

    }

    "handling submits on the tax check code page" must {

      def performAction(data: (String, String)*): Future[Result] =
        controller.licenceTypeSubmit(FakeRequest().withFormUrlEncodedBody(data: _*))

      behave like sessionDataActionBehaviour(() => performAction())

      val currentSession =
        HECSession(
          UserAnswers.empty.copy(
            taxCheckCode = Some(taxCheckCode)
          )
        )

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType(), currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction(),
            messageFromMessageKey("licenceType.title"),
            messageFromMessageKey("licenceType.error.required")
          )

        }

        "an index is submitted which is too large" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType(), currentSession)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction("licenceType" -> Int.MaxValue.toString),
            messageFromMessageKey("licenceType.title"),
            messageFromMessageKey("licenceType.error.invalid")
          )
        }

        "a value is submitted which is not a number" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType(), currentSession)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction("licenceType" -> "xyz"),
            messageFromMessageKey("licenceType.title"),
            messageFromMessageKey("licenceType.error.invalid")
          )
        }

      }

      "return an internal server error" when {

        "the call to update and next fails" in {
          val answers        = UserAnswers.empty
          val updatedAnswers = UserAnswers.empty.copy(licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires))
          val session        = HECSession(answers)
          val updatedSession = HECSession(updatedAnswers)

          inSequence {
            mockGetSession(session)
            mockJourneyServiceUpdateAndNext(routes.LicenceTypeController.licenceType(), session, updatedSession)(
              Left(Error(new Exception))
            )
          }
          status(performAction("licenceType" -> "0")) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "redirect to the next page" when {

        "valid data is submitted and" when {

          "the user has not previously completed answering questions" in {
            val answers        = UserAnswers.empty.copy(
              taxCheckCode = Some(taxCheckCode),
              licenceType = None
            )
            val updatedAnswers = answers.copy(licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles))
            val session        = HECSession(answers)
            val updatedSession = session.copy(userAnswers = updatedAnswers)

            inSequence {
              mockGetSession(session)
              mockJourneyServiceUpdateAndNext(routes.LicenceTypeController.licenceType(), session, updatedSession)(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("licenceType" -> "1"), mockNextCall)
          }

          "the user has previously completed answering questions" in {
            val answers        = UserAnswers(
              taxCheckCode = Some(taxCheckCode),
              Some(LicenceType.DriverOfTaxisAndPrivateHires)
            )
            val updatedAnswers = answers.copy(licenceType = Some(LicenceType.ScrapMetalMobileCollector))
            val session        = HECSession(answers)
            val updatedSession = session.copy(userAnswers = updatedAnswers)

            inSequence {
              mockGetSession(session)
              mockJourneyServiceUpdateAndNext(routes.LicenceTypeController.licenceType(), session, updatedSession)(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("licenceType" -> "2"), mockNextCall)
          }
        }

      }

    }

  }

}
