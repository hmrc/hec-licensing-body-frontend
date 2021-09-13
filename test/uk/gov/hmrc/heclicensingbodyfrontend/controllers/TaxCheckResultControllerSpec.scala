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
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.TaxCheckResultControllerSpec.DetailsEnteredRow
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
import collection.JavaConverters._

class TaxCheckResultControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {

  override val overrideBindings: List[GuiceableModule] =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService)
    )

  val controller: TaxCheckResultController = instanceOf[TaxCheckResultController]

  val hecTaxCheckCode: HECTaxCheckCode = HECTaxCheckCode("ABCDEF123")
  val dateOfBirth: DateOfBirth         = DateOfBirth(LocalDate.of(1922, 12, 1))

  val date: LocalDate                = TimeUtils.today().minusYears(20)
  val dateTimeChecked: ZonedDateTime = ZonedDateTime.of(2021, 9, 10, 8, 2, 0, 0, ZoneId.of("Europe/London"))

  val answers: UserAnswers = UserAnswers.empty.copy(
    taxCheckCode = Some(hecTaxCheckCode),
    licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires),
    entityType = Some(Individual),
    dateOfBirth = Some(DateOfBirth(date))
  )

  val matchRequest: HECTaxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(DateOfBirth(date)))

  "TaxCheckResultControllerSpec" when {

    def checkDetailsEnteredRows(doc: Document) = {
      val expectedRows = List(
        DetailsEnteredRow(
          messageFromMessageKey("detailsEntered.taxCheckCodeKey"),
          matchRequest.taxCheckCode.value.grouped(3).mkString(" ")
        ),
        DetailsEnteredRow(
          messageFromMessageKey("detailsEntered.licenceTypeKey"),
          messageFromMessageKey("licenceType.driverOfTaxis")
        ),
        DetailsEnteredRow(
          messageFromMessageKey("detailsEntered.dateOfBirthKey"),
          TimeUtils.govDisplayFormat(date)
        )
      )

      val rows = doc.select(".govuk-summary-list__row").iterator().asScala.toList.map { element =>
        val question = element.select(".govuk-summary-list__key").text()
        val answer   = element.select(".govuk-summary-list__value").text()
        DetailsEnteredRow(question, answer)
      }
      rows shouldBe expectedRows
    }

    "handling request to tax check Valid page " must {

      def performAction(): Future[Result] = controller.taxCheckMatch(FakeRequest())

      "return an InternalServerError" when {

        "a tax check code cannot be found in session " in {

          val session = HECSession(UserAnswers.empty, None)

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

      }

      "display the page" when {

        "tax check code is a match for an applicant and is valid " when {

          def testValidPage(dateTimeChecked: ZonedDateTime, matchRegex: String): Unit = {
            val session = HECSession(answers, Some(Match(matchRequest, dateTimeChecked)))
            inSequence {
              mockGetSession(session)
            }

            checkPageIsDisplayed(
              performAction(),
              messageFromMessageKey("taxCheckValid.title"),
              { doc =>
                doc.select(".govuk-panel__body").text should include regex matchRegex
                checkDetailsEnteredRows(doc)
              }
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

          "a tax check code cannot be found in session " in {

            val session = HECSession(UserAnswers.empty, None)

            inSequence {
              mockGetSession(session)
            }

            status(performAction()) shouldBe INTERNAL_SERVER_ERROR

          }

        }

      }

    }

    "handling request to tax  check expired page" must {

      def performAction(): Future[Result] = controller.taxCheckExpired(FakeRequest())

      "return an InternalServerError" when {

        "a tax check code cannot be found in session " in {

          val session = HECSession(UserAnswers.empty, None)

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }
      }

      "display the page " when {

        "tax check code is a match for an applicant but  expired" when {

          def testExpirePage(dateTimeChecked: ZonedDateTime, matchRegex: String): Unit = {
            val session = HECSession(answers, Some(Expired(matchRequest, dateTimeChecked)))

            inSequence {
              mockGetSession(session)
            }

            checkPageIsDisplayed(
              performAction(),
              messageFromMessageKey("taxCheckExpired.title"),
              { doc =>
                doc.select(".govuk-panel__body").text should include regex matchRegex
                checkDetailsEnteredRows(doc)
              }
            )
          }

          "tax check code is checked in morning" in {
            testExpirePage(dateTimeChecked, "10 September 2021, 8:02am")
          }

          "tax check code is checked at Noon" in {

            val dateTimeNoon = ZonedDateTime.of(2021, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/London"))
            testExpirePage(dateTimeNoon, "10 September 2021, 12:00pm")

          }

          "tax check code is checked afternoon" in {

            val dateTime = ZonedDateTime.of(2021, 9, 10, 17, 16, 0, 0, ZoneId.of("Europe/London"))
            testExpirePage(dateTime, "10 September 2021, 5:16pm")

          }

          "tax check code is checked at midnight" in {

            val dateTime = ZonedDateTime.of(2021, 9, 10, 0, 0, 0, 0, ZoneId.of("Europe/London"))
            testExpirePage(dateTime, "10 September 2021, 12:00am")

          }

        }

      }

    }

    "handling request to tax check Not Match page" must {

      def performAction(): Future[Result] = controller.taxCheckNotMatch(FakeRequest())

      "return an InternalServerError" when {

        "a tax check code cannot be found in session " in {

          val session = HECSession(UserAnswers.empty, None)

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.TaxCheckResultController.taxCheckNotMatch(), session)(
              mockPreviousCall
            )
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }
      }

      "display the page " when {

        "tax check code is not a match in database" in {

          val session = HECSession(answers, Some(NoMatch(matchRequest, dateTimeChecked)))

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.TaxCheckResultController.taxCheckNotMatch(), session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("taxCheckNoMatch.title"),
            checkDetailsEnteredRows
          )

        }

      }

    }

  }
}

object TaxCheckResultControllerSpec {

  final case class DetailsEnteredRow(question: String, answer: String)

}
