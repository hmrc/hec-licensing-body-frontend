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

import org.jsoup.nodes.Document
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.TaxCheckResultControllerSpec.DetailsEnteredRow
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.{Company, Individual}
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models._
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

  val crn = CRN("SS123456")

  val answers: UserAnswers = UserAnswers.empty.copy(
    taxCheckCode = Some(hecTaxCheckCode),
    licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires),
    entityType = Some(Individual),
    dateOfBirth = Some(DateOfBirth(date))
  )

  val companyAnswers: UserAnswers = UserAnswers.empty.copy(
    taxCheckCode = Some(hecTaxCheckCode),
    licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles),
    entityType = Some(Company),
    dateOfBirth = None,
    crn = Some(crn)
  )

  val matchRequest: HECTaxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(DateOfBirth(date)))

  val companyMatchRequest: HECTaxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.OperatorOfPrivateHireVehicles, Left(crn))

  "TaxCheckResultControllerSpec" when {

    def checkDetailsEnteredRows(doc: Document, request: HECTaxCheckMatchRequest) = {
      val expectedRows: List[DetailsEnteredRow] = List(
        DetailsEnteredRow(
          messageFromMessageKey("detailsEntered.taxCheckCodeKey"),
          matchRequest.taxCheckCode.value.grouped(3).mkString(" ")
        )
      )
      val eRows: List[DetailsEnteredRow]        = request.verifier match {
        case Left(_) =>
          List(
            DetailsEnteredRow(
              messageFromMessageKey("detailsEntered.licenceTypeKey"),
              messageFromMessageKey("licenceType.operatorOfPrivateHireVehicles")
            ),
            DetailsEnteredRow(
              messageFromMessageKey("detailsEntered.crnKey"),
              crn.value
            )
          )

        case Right(_) =>
          List(
            DetailsEnteredRow(
              messageFromMessageKey("detailsEntered.licenceTypeKey"),
              messageFromMessageKey("licenceType.driverOfTaxis")
            ),
            DetailsEnteredRow(
              messageFromMessageKey("detailsEntered.dateOfBirthKey"),
              TimeUtils.govDisplayFormat(date)
            )
          )

      }

      val rows = doc.select(".govuk-summary-list__row").iterator().asScala.toList.map { element =>
        val question = element.select(".govuk-summary-list__key").text()
        val answer   = element.select(".govuk-summary-list__value").text()
        DetailsEnteredRow(question, answer)
      }
      rows shouldBe (expectedRows ++ eRows)
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

        "tax check code is No Match in the session for Match page " in {

          val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, NoMatch)))

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

        "tax check code is expired in the session for the Match page " in {

          val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Expired)))

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

      }

      "display the page" when {

        "tax check code is a match and valid " when {

          "applicant is Individual" when {

            def testValidPageForIndividual(dateTimeChecked: ZonedDateTime, matchRegex: String): Unit = {
              val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Match)))
              inSequence {
                mockGetSession(session)
              }

              checkPageIsDisplayed(
                performAction(),
                messageFromMessageKey("taxCheckValid.title"),
                { doc =>
                  doc.select(".govuk-panel__body").text should include regex matchRegex
                  checkDetailsEnteredRows(doc, matchRequest)
                }
              )
            }

            "tax check code is checked in morning" in {
              testValidPageForIndividual(dateTimeChecked, "10 September 2021, 8:02am")
            }

            "tax check code is checked at Noon" in {

              val dateTimeNoon = ZonedDateTime.of(2021, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/London"))
              testValidPageForIndividual(dateTimeNoon, "10 September 2021, 12:00pm")

            }

            "tax check code is checked afternoon" in {
              val dateTime = ZonedDateTime.of(2021, 9, 10, 17, 16, 0, 0, ZoneId.of("Europe/London"))
              testValidPageForIndividual(dateTime, "10 September 2021, 5:16pm")

            }

            "tax check code is checked at midnight" in {

              val dateTime = ZonedDateTime.of(2021, 9, 10, 0, 0, 0, 0, ZoneId.of("Europe/London"))
              testValidPageForIndividual(dateTime, "10 September 2021, 12:00am")

            }

          }

          "applicant is Company" when {
            def testValidPageForCompany(dateTimeChecked: ZonedDateTime, matchRegex: String): Unit = {
              val session =
                HECSession(companyAnswers, Some(HECTaxCheckMatchResult(companyMatchRequest, dateTimeChecked, Match)))
              inSequence {
                mockGetSession(session)
              }

              checkPageIsDisplayed(
                performAction(),
                messageFromMessageKey("taxCheckValid.title"),
                { doc =>
                  doc.select(".govuk-panel__body").text should include regex matchRegex
                  checkDetailsEnteredRows(doc, companyMatchRequest)
                }
              )
            }

            "tax check code is checked in morning" in {
              testValidPageForCompany(dateTimeChecked, "10 September 2021, 8:02am")
            }

            "tax check code is checked at Noon" in {

              val dateTimeNoon = ZonedDateTime.of(2021, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/London"))
              testValidPageForCompany(dateTimeNoon, "10 September 2021, 12:00pm")

            }

            "tax check code is checked afternoon" in {
              val dateTime = ZonedDateTime.of(2021, 9, 10, 17, 16, 0, 0, ZoneId.of("Europe/London"))
              testValidPageForCompany(dateTime, "10 September 2021, 5:16pm")

            }

            "tax check code is checked at midnight" in {

              val dateTime = ZonedDateTime.of(2021, 9, 10, 0, 0, 0, 0, ZoneId.of("Europe/London"))
              testValidPageForCompany(dateTime, "10 September 2021, 12:00am")

            }

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

        "tax check code cannot be found in session " in {

          val session = HECSession(UserAnswers.empty, None)

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

        "tax check code is Match in the session for Expired page " in {

          val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Match)))

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

        "tax check code is No Match in the session for the Expired page " in {

          val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Match)))

          inSequence {
            mockGetSession(session)
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }
      }

      "display the page " when {

        "tax check code is a match but expired" when {

          "applicant is an Individual" when {

            def testExpirePageForIndividual(dateTimeChecked: ZonedDateTime, matchRegex: String): Unit = {
              val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Expired)))

              inSequence {
                mockGetSession(session)
              }

              checkPageIsDisplayed(
                performAction(),
                messageFromMessageKey("taxCheckExpired.title"),
                { doc =>
                  doc.select(".govuk-panel__body").text should include regex matchRegex
                  checkDetailsEnteredRows(doc, matchRequest)
                }
              )
            }

            "tax check code is checked in morning" in {
              testExpirePageForIndividual(dateTimeChecked, "10 September 2021, 8:02am")
            }

            "tax check code is checked at Noon" in {

              val dateTimeNoon = ZonedDateTime.of(2021, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/London"))
              testExpirePageForIndividual(dateTimeNoon, "10 September 2021, 12:00pm")

            }

            "tax check code is checked afternoon" in {

              val dateTime = ZonedDateTime.of(2021, 9, 10, 17, 16, 0, 0, ZoneId.of("Europe/London"))
              testExpirePageForIndividual(dateTime, "10 September 2021, 5:16pm")

            }

            "tax check code is checked at midnight" in {

              val dateTime = ZonedDateTime.of(2021, 9, 10, 0, 0, 0, 0, ZoneId.of("Europe/London"))
              testExpirePageForIndividual(dateTime, "10 September 2021, 12:00am")

            }

          }

          "applicant is a Company" when {

            def testExpirePageForCompany(dateTimeChecked: ZonedDateTime, matchRegex: String): Unit = {
              val session =
                HECSession(companyAnswers, Some(HECTaxCheckMatchResult(companyMatchRequest, dateTimeChecked, Expired)))

              inSequence {
                mockGetSession(session)
              }

              checkPageIsDisplayed(
                performAction(),
                messageFromMessageKey("taxCheckExpired.title"),
                { doc =>
                  doc.select(".govuk-panel__body").text should include regex matchRegex
                  checkDetailsEnteredRows(doc, companyMatchRequest)
                }
              )
            }

            "tax check code is checked in morning" in {
              testExpirePageForCompany(dateTimeChecked, "10 September 2021, 8:02am")
            }

            "tax check code is checked at Noon" in {

              val dateTimeNoon = ZonedDateTime.of(2021, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/London"))
              testExpirePageForCompany(dateTimeNoon, "10 September 2021, 12:00pm")

            }

            "tax check code is checked afternoon" in {

              val dateTime = ZonedDateTime.of(2021, 9, 10, 17, 16, 0, 0, ZoneId.of("Europe/London"))
              testExpirePageForCompany(dateTime, "10 September 2021, 5:16pm")

            }

            "tax check code is checked at midnight" in {

              val dateTime = ZonedDateTime.of(2021, 9, 10, 0, 0, 0, 0, ZoneId.of("Europe/London"))
              testExpirePageForCompany(dateTime, "10 September 2021, 12:00am")

            }

          }

        }

      }

    }

    "handling request to tax check Not Match page" must {

      def performAction(): Future[Result] = controller.taxCheckNotMatch(FakeRequest())

      "return an InternalServerError" when {

        "tax check code cannot be found in session " in {

          val session = HECSession(UserAnswers.empty, None)

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.TaxCheckResultController.taxCheckNotMatch(), session)(
              mockPreviousCall
            )
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

        "tax check code is Match in the session for No Match page " in {

          val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Match)))

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.TaxCheckResultController.taxCheckNotMatch(), session)(
              mockPreviousCall
            )
          }

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR

        }

        "tax check code is Expired in the session for No Match page " in {

          val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, Expired)))

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

        "tax check code is not a match in database" when {

          "applicant is an Individual" in {
            val session = HECSession(answers, Some(HECTaxCheckMatchResult(matchRequest, dateTimeChecked, NoMatch)))

            inSequence {
              mockGetSession(session)
              mockJourneyServiceGetPrevious(routes.TaxCheckResultController.taxCheckNotMatch(), session)(
                mockPreviousCall
              )
            }

            checkPageIsDisplayed(
              performAction(),
              messageFromMessageKey("taxCheckNoMatch.title"),
              (doc => checkDetailsEnteredRows(doc, matchRequest))
            )
          }

          "applicant is a Company" in {
            val session =
              HECSession(companyAnswers, Some(HECTaxCheckMatchResult(companyMatchRequest, dateTimeChecked, NoMatch)))

            inSequence {
              mockGetSession(session)
              mockJourneyServiceGetPrevious(routes.TaxCheckResultController.taxCheckNotMatch(), session)(
                mockPreviousCall
              )
            }

            checkPageIsDisplayed(
              performAction(),
              messageFromMessageKey("taxCheckNoMatch.title"),
              (doc => checkDetailsEnteredRows(doc, companyMatchRequest))
            )
          }

        }

      }

    }

  }
}

object TaxCheckResultControllerSpec {

  final case class DetailsEnteredRow(question: String, answer: String)

}
