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
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.EntityTypeController.entityTypes
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EntityTypeControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {
  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  val controller = instanceOf[EntityTypeController]

  val taxCheckCode = HECTaxCheckCode("ABC DEF 123")
  val licenceType  = LicenceType.DriverOfTaxisAndPrivateHires

  "EntityTypeController" when {

    "handling requests to the entity type page" must {

      def performAction(): Future[Result] = controller.entityType(FakeRequest())

      behave like sessionDataActionBehaviour(performAction)

      "show the page" when {

        "session data is found" in {
          val session = HECSession(
            UserAnswers.empty.copy(
              taxCheckCode = Some(taxCheckCode),
              licenceType = Some(licenceType)
            )
          )

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.EntityTypeController.entityType(), session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("entityType.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              val selectedOptions = doc.select(".govuk-radios__input[checked]")
              selectedOptions.isEmpty shouldBe true

              val form = doc.select("form")
              form.attr("action") shouldBe routes.EntityTypeController.entityTypeSubmit().url

            }
          )
        }

      }

    }

    "handling submit on the entity type page" must {

      def performAction(data: (String, String)*): Future[Result] =
        controller.entityTypeSubmit(FakeRequest().withFormUrlEncodedBody(data: _*))

      behave like sessionDataActionBehaviour(() => performAction())

      val currentSession = HECSession(
        UserAnswers.empty.copy(
          taxCheckCode = Some(taxCheckCode),
          licenceType = Some(licenceType)
        )
      )

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.EntityTypeController.entityType(), currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction(),
            messageFromMessageKey("entityType.title"),
            messageFromMessageKey("entityType.error.required")
          )

        }

        "an invalid index is submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.EntityTypeController.entityType(), currentSession)(mockPreviousCall)
          }
          val invalidIndex = entityTypes.length + 1
          checkFormErrorIsDisplayed(
            performAction("entityType" -> invalidIndex.toString),
            messageFromMessageKey("entityType.title"),
            messageFromMessageKey("entityType.error.invalid")
          )
        }

        "a non-numeric value is submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.EntityTypeController.entityType(), currentSession)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction("entityType" -> "xyz"),
            messageFromMessageKey("entityType.title"),
            messageFromMessageKey("entityType.error.invalid")
          )
        }

      }

      "return an internal server error" when {

        "the call to update and next fails" in {
          val answers        = UserAnswers.empty
          val updatedAnswers = UserAnswers.empty.copy(entityType = Some(EntityType.Individual))
          val session        = HECSession(answers)
          val updatedSession = HECSession(updatedAnswers)

          inSequence {
            mockGetSession(session)
            mockJourneyServiceUpdateAndNext(routes.EntityTypeController.entityType(), session, updatedSession)(
              Left(Error(new Exception))
            )
          }
          status(performAction("entityType" -> "0")) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "redirect to the next page" when {

        "valid data is submitted and" when {

          "the user has not previously completed answering questions" in {
            val answers        = UserAnswers.empty.copy(
              taxCheckCode = Some(taxCheckCode),
              licenceType = Some(licenceType),
              entityType = None
            )
            val updatedAnswers = answers.copy(entityType = Some(EntityType.Company))
            val session        = HECSession(answers)
            val updatedSession = session.copy(userAnswers = updatedAnswers)

            inSequence {
              mockGetSession(session)
              mockJourneyServiceUpdateAndNext(routes.EntityTypeController.entityType(), session, updatedSession)(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("entityType" -> "1"), mockNextCall)
          }

          "the user had already answered the question" in {

            val answers        = UserAnswers(
              taxCheckCode = Some(taxCheckCode),
              licenceType = Some(licenceType),
              entityType = Some(EntityType.Individual)
            )
            val updatedAnswers = answers.copy(entityType = Some(EntityType.Company))
            val session        = HECSession(answers)
            val updatedSession = session.copy(userAnswers = updatedAnswers)

            inSequence {
              mockGetSession(session)
              mockJourneyServiceUpdateAndNext(routes.EntityTypeController.entityType(), session, updatedSession)(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("entityType" -> "1"), mockNextCall)
          }
        }

      }

    }

  }

}
