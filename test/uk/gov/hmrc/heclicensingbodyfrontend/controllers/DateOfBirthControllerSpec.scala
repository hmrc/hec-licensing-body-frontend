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

import cats.data.EitherT
import cats.instances.future._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, Error, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.{HECTaxMatchService, JourneyService}
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DateOfBirthControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport {

  val taxCheckService = mock[HECTaxMatchService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService),
      bind[HECTaxMatchService].toInstance(taxCheckService)
    )

  val controller = instanceOf[DateOfBirthController]

  val hecTaxCheckCode  = HECTaxCheckCode("ABC DEF 123")
  val hecTaxCheckCode2 = HECTaxCheckCode("ABC DEG 123")
  val dateOfBirth      = DateOfBirth(LocalDate.of(1922, 12, 1))

  val date            = TimeUtils.today().minusYears(20)
  val dateTimeChecked = TimeUtils.now()

  val taxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(DateOfBirth(date)))

  implicit val appConfig = instanceOf[AppConfig]

  def mockMatchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HECTaxCheckMatchResult]) =
    (taxCheckService
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(taxCheckMatchRequest, *)
      .returning(EitherT.fromEither(result))

  "DateOfBirthControllerSpec" when {

    "handling requests to date Of birth page" must {

      def performAction(): Future[Result] =
        controller.dateOfBirth(FakeRequest())

      "display the page" when {

        "user is entering date of birth  for the first time " in {
          val session = HECSession(UserAnswers.empty, None)
          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth(), session)(mockPreviousCall)
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("dateOfBirth.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              doc.select("#dateOfBirth-day").attr("value")   shouldBe ""
              doc.select("#dateOfBirth-month").attr("value") shouldBe ""
              doc.select("#dateOfBirth-year").attr("value")  shouldBe ""

              val form = doc.select("form")
              form
                .attr("action") shouldBe routes.DateOfBirthController.dateOfBirthSubmit().url
            }
          )

        }

        "date of birth page is the previous page and when user go back and date of birth is already in session" in {
          val date    = TimeUtils.today().minusYears(23L)
          val session =
            HECSession(
              UserAnswers.empty.copy(dateOfBirth = Some(DateOfBirth(date))),
              None
            )
          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth(), session)(mockPreviousCall)
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("dateOfBirth.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              doc.select("#dateOfBirth-day").attr("value")   shouldBe date.getDayOfMonth.toString
              doc.select("#dateOfBirth-month").attr("value") shouldBe date.getMonthValue.toString
              doc.select("#dateOfBirth-year").attr("value")  shouldBe date.getYear.toString

              val form = doc.select("form")
              form
                .attr("action") shouldBe routes.DateOfBirthController.dateOfBirthSubmit().url
            }
          )

        }

      }

    }

    "handling submit on the date of birth page" must {

      def performAction(data: (String, String)*): Future[Result] =
        controller.dateOfBirthSubmit(FakeRequest().withFormUrlEncodedBody(data: _*))

      def formData(date: LocalDate): List[(String, String)] = List(
        "dateOfBirth-day"   -> date.getDayOfMonth.toString,
        "dateOfBirth-month" -> date.getMonthValue.toString,
        "dateOfBirth-year"  -> date.getYear.toString
      )

      "show a form error" when {

        val session = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode("XNFFGBDD6"))), None)

        "nothing is submitted" in {
          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth(), session)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction(),
            messageFromMessageKey("dateOfBirth.title"),
            messageFromMessageKey("dateOfBirth.error.required")
          )
        }

        "the date entered is invalid" in {
          DateErrorScenarios
            .dateErrorScenarios("dateOfBirth")
            .foreach { scenario =>
              withClue(s"For date error scenario $scenario: ") {
                val data = List(
                  "dateOfBirth-day"   -> scenario.dayInput,
                  "dateOfBirth-month" -> scenario.monthInput,
                  "dateOfBirth-year"  -> scenario.yearInput
                ).collect { case (key, Some(value)) => key -> value }
                inSequence {
                  mockGetSession(session)
                  mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth(), session)(
                    mockPreviousCall
                  )
                }

                checkFormErrorIsDisplayed(
                  performAction(data: _*),
                  messageFromMessageKey("dateOfBirth.title"),
                  messageFromMessageKey(scenario.expectedErrorMessageKey)
                )
              }
            }
        }

        "date entered is in the future" in {

          val date = TimeUtils.today().plusDays(2)
          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth(), session)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction(formData(date): _*),
            messageFromMessageKey("dateOfBirth.title"),
            messageFromMessageKey(
              "dateOfBirth.error.inFuture",
              TimeUtils.govDisplayFormat(TimeUtils.today().plusDays(1))
            )
          )
        }

        "date entered is beyond 1 jan 1900" in {
          val cutoffDate = LocalDate.of(1900, 1, 1)
          val date       = cutoffDate.minusDays(1L)

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth(), session)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction(formData(date): _*),
            messageFromMessageKey("dateOfBirth.title"),
            messageFromMessageKey("dateOfBirth.error.tooFarInPast", TimeUtils.govDisplayFormat(cutoffDate))
          )
        }

      }

      "return an InternalServerError" when {

        "there is an error updating and getting the next endpoint" in {

          val answers = UserAnswers.empty.copy(
            taxCheckCode = Some(hecTaxCheckCode),
            licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
          )
          val session = HECSession(answers, None)

          val updatedSession =
            session.copy(
              userAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date))),
              taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match)),
              verificationAttempts = Map(hecTaxCheckCode.value -> 0)
            )

          inSequence {
            mockGetSession(session)
            mockMatchTaxCheck(taxCheckMatchRequest)(
              Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )
            mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
              Left(Error(""))
            )
          }

          status(performAction(formData(date): _*)) shouldBe INTERNAL_SERVER_ERROR
        }

        "there is no taxCheckCode in the session" in {

          val answers = UserAnswers.empty.copy(
            taxCheckCode = None,
            licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
          )
          val session = HECSession(answers, None)

          inSequence {
            mockGetSession(session)
          }

          status(performAction(formData(date): _*)) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "redirect to the next page" when {

        "a valid date of birth is submitted and " when {

          "the verification attempt is empty" when {

            "There is a match found, new session is updated with 0 attempt for that tax check code" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(answers, None)

              val updatedAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date)))
              val updatedSession =
                session.copy(
                  userAnswers = updatedAnswers,
                  taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 0)
                )

              inSequence {
                mockGetSession(session)
                mockMatchTaxCheck(taxCheckMatchRequest)(
                  Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
                )
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }

            "There is a match found but expired, new session is updated with 0 attempt for that tax check code" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(answers, None)

              val updatedAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date)))
              val updatedSession =
                session.copy(
                  userAnswers = updatedAnswers,
                  taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Expired)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 0)
                )

              inSequence {
                mockGetSession(session)
                mockMatchTaxCheck(taxCheckMatchRequest)(
                  Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Expired))
                )
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }

            "There is no match found , new session increment attempt by 1 for that tax check code" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(answers, None, verificationAttempts = Map(hecTaxCheckCode.value -> 1))

              val updatedAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date)))
              val updatedSession =
                session.copy(
                  userAnswers = updatedAnswers,
                  taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 2)
                )

              inSequence {
                mockGetSession(session)
                mockMatchTaxCheck(taxCheckMatchRequest)(
                  Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch))
                )
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }
          }

          "the verification attempt is not empty" when {

            "two tax check codes in session, both with attempt 2, the one with no match is incremented to 3" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(
                answers,
                None,
                verificationAttempts = Map(hecTaxCheckCode.value -> 2, hecTaxCheckCode2.value -> 2)
              )

              val updatedAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date)))
              val updatedSession =
                session.copy(
                  userAnswers = updatedAnswers,
                  taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 3, hecTaxCheckCode2.value -> 2)
                )

              inSequence {
                mockGetSession(session)
                mockMatchTaxCheck(taxCheckMatchRequest)(
                  Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch))
                )
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }

            "two tax check codes in session, both with attempt 2, the one with  match is decremented to 0" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(
                answers,
                None,
                verificationAttempts = Map(hecTaxCheckCode.value -> 2, hecTaxCheckCode2.value -> 2)
              )

              val updatedAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date)))
              val updatedSession =
                session.copy(
                  userAnswers = updatedAnswers,
                  taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 0, hecTaxCheckCode2.value -> 2)
                )

              inSequence {
                mockGetSession(session)
                mockMatchTaxCheck(taxCheckMatchRequest)(
                  Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
                )
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }

            "two tax check codes in session, both with attempt 2, the one with  Expired is decremented to 0" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(
                answers,
                None,
                verificationAttempts = Map(hecTaxCheckCode.value -> 2, hecTaxCheckCode2.value -> 2)
              )

              val updatedAnswers = answers.copy(dateOfBirth = Some(DateOfBirth(date)))
              val updatedSession =
                session.copy(
                  userAnswers = updatedAnswers,
                  taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Expired)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 0, hecTaxCheckCode2.value -> 2)
                )

              inSequence {
                mockGetSession(session)
                mockMatchTaxCheck(taxCheckMatchRequest)(
                  Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Expired))
                )
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }

            "tax check code in session has reached the max verification  attempt, got to next page with unaffected session even if it's a Match" in {

              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              )
              val session = HECSession(
                answers,
                None,
                verificationAttempts = Map(hecTaxCheckCode.value -> appConfig.maxVerificationAttempts)
              )

              val updatedSession = session

              inSequence {
                mockGetSession(session)
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth(), session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*), mockNextCall)
            }

          }

        }

      }

    }
  }

}
