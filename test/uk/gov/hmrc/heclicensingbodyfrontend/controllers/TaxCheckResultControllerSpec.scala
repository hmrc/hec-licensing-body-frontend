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
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.Individual
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckMatchResult.{Expired, Match, NoMatch}
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxCheckResultControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  val controller = instanceOf[TaxCheckResultController]

  val hecTaxCheckCode = HECTaxCheckCode("ABC DEF 123")
  val dateOfBirth     = DateOfBirth(LocalDate.of(1922, 12, 1))

  val date: LocalDate                = TimeUtils.today().minusYears(20)
  val dateTimeChecked: ZonedDateTime = ZonedDateTime.of(2021, 9, 10, 8, 2, 0, 0, ZoneId.of("Europe/London"))

  val answers = UserAnswers.empty.copy(
    taxCheckCode = Some(hecTaxCheckCode),
    licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires),
    entityType = Some(Individual),
    dateOfBirth = Some(DateOfBirth(date))
  )

  val matchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(DateOfBirth(date)))

  "TaxCheckResultControllerSpec" when {

    "handling request to tax check result page " must {

      def performAction(): Future[Result] = controller.taxCheckMatch(FakeRequest())

      "return an InternalServerError" when {

        "a tax check code cannot be found in session" in {
          val session = HECSession(UserAnswers.empty, None)

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR
        }

        "current page is tax check valid page " when {

          "No match data in session " in {
            val session = HECSession(UserAnswers.empty, Some(NoMatch(matchRequest, dateTimeChecked)))
            inSequence {
              mockGetSession(session)
            }

            status(performAction()) shouldBe INTERNAL_SERVER_ERROR
          }

          "Expired data in session " in {
            val session = HECSession(UserAnswers.empty, Some(Expired(matchRequest, dateTimeChecked)))
            inSequence {
              mockGetSession(session)
            }

            status(performAction()) shouldBe INTERNAL_SERVER_ERROR
          }

        }

      }

      "display the page" when {

        "tax check code has been validated for the user " when {

          def testValidPage(dateTimeChecked: ZonedDateTime, matchRegex: String) = {
            val session = HECSession(answers, Some(Match(matchRequest, dateTimeChecked)))

            inSequence {
              mockGetSession(session)
            }

            checkPageIsDisplayed(
              performAction(),
              messageFromMessageKey("taxCheckValid.title"),
              doc => doc.select(".govuk-panel__body").text should include regex matchRegex
            )
          }

          "tax check code is checked in morning" in {
            testValidPage(dateTimeChecked, "10 September 2021, 8:02am")
          }

          "tax check code is checked at Noon" in {

            val dateTimeNoon = ZonedDateTime.of(2021, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/London"))
            testValidPage(dateTimeNoon, "10 September 2021, 12:00pm")

          }

          "tax check code is checked afternoon" in {

            val dateTime = ZonedDateTime.of(2021, 9, 10, 17, 16, 0, 0, ZoneId.of("Europe/London"))
            testValidPage(dateTime, "10 September 2021, 5:16pm")

          }

          "tax check code is checked at midnight" in {

            val dateTime = ZonedDateTime.of(2021, 9, 10, 0, 0, 0, 0, ZoneId.of("Europe/London"))
            testValidPage(dateTime, "10 September 2021, 12:00am")

          }

        }

      }

    }

  }
}
