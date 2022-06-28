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

import org.jsoup.nodes.Document
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
import scala.collection.JavaConverters._

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

        def checkLicenceTypeOptions(doc: Document, expectedLabelsWithHintText: List[(String, Option[String])]) = {
          val radios = doc.select(".govuk-radios__item")

          val labelsWithHintText = radios.iterator().asScala.toList.map { element =>
            val label    = element.select(".govuk-label").text()
            val hintText = Option(element.select(".govuk-hint").text).filter(_.nonEmpty)
            label -> hintText
          }

          labelsWithHintText shouldBe expectedLabelsWithHintText
        }

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
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType, session)(
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

              checkLicenceTypeOptions(
                doc,
                List(
                  "licenceType.driverOfTaxis"                 -> Some("licenceType.driverOfTaxis.hint"),
                  "licenceType.operatorOfPrivateHireVehicles" -> Some("licenceType.operatorOfPrivateHireVehicles.hint"),
                  "licenceType.bookingOffice"                 -> Some("licenceType.bookingOffice.hint"),
                  "licenceType.scrapMetalCollector"           -> Some("licenceType.scrapMetalCollector.hint"),
                  "licenceType.scrapMetalDealer"              -> Some("licenceType.scrapMetalDealer.hint")
                ).map { case (label, hint) => messageFromMessageKey(label) -> hint.map(messageFromMessageKey(_)) }
              )

              val form = doc.select("form")
              form.attr("action") shouldBe routes.LicenceTypeController.licenceTypeSubmit.url

            }
          )
        }

      }

    }

    "handling submits on the Licence Type page" must {

      def performAction(data: (String, String)*): Future[Result] =
        controller.licenceTypeSubmit(FakeRequest().withFormUrlEncodedBody(data: _*))

      behave like sessionDataActionBehaviour(() => performAction())

      val currentSession =
        HECSession(
          UserAnswers.empty.copy(
            taxCheckCode = Some(taxCheckCode)
          ),
          None
        )

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType, currentSession)(
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
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType, currentSession)(mockPreviousCall)
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
            mockJourneyServiceGetPrevious(routes.LicenceTypeController.licenceType, currentSession)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction("licenceType" -> "xyz"),
            messageFromMessageKey("licenceType.title"),
            messageFromMessageKey("licenceType.error.invalid")
          )
        }

      }

      "return a technical error" when {

        "the call to update and next fails" in {
          val answers        = UserAnswers.empty
          val updatedAnswers = UserAnswers.empty.copy(licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires))
          val session        = HECSession(answers, None)
          val updatedSession = HECSession(updatedAnswers, None)

          inSequence {
            mockGetSession(session)
            mockJourneyServiceUpdateAndNext(routes.LicenceTypeController.licenceType, session, updatedSession)(
              Left(Error(new Exception))
            )
          }
          assertThrows[RuntimeException](await(performAction("licenceType" -> "0")))

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
            val session        = HECSession(answers, None)
            val updatedSession = session.copy(userAnswers = updatedAnswers)

            inSequence {
              mockGetSession(session)
              mockJourneyServiceUpdateAndNext(routes.LicenceTypeController.licenceType, session, updatedSession)(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("licenceType" -> "1"), mockNextCall)
          }

          "the user has previously completed answering questions" in {
            val answers        = UserAnswers(
              taxCheckCode = Some(taxCheckCode),
              Some(LicenceType.DriverOfTaxisAndPrivateHires),
              None,
              None,
              None
            )
            val updatedAnswers = answers.copy(licenceType = Some(LicenceType.BookingOffice))
            val session        = HECSession(answers, None)
            val updatedSession = session.copy(userAnswers = updatedAnswers)

            inSequence {
              mockGetSession(session)
              mockJourneyServiceUpdateAndNext(routes.LicenceTypeController.licenceType, session, updatedSession)(
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
