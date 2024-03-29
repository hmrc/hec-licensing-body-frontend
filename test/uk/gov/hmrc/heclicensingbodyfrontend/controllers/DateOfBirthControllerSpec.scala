/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.mvc.{Cookie, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.Individual
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, Error, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckStatus, InconsistentSessionState, Language, MatchFailureReason, TaxCheckVerificationAttempts, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.{AuditService, AuditServiceSupport, HECTaxMatchService, JourneyService, VerificationService}
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DateOfBirthControllerSpec
    extends ControllerSpec
    with SessionSupport
    with SessionDataActionBehaviour
    with JourneyServiceSupport
    with AuditServiceSupport {

  val taxCheckService     = mock[HECTaxMatchService]
  val verificationService = mock[VerificationService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService),
      bind[HECTaxMatchService].toInstance(taxCheckService),
      bind[VerificationService].toInstance(verificationService),
      bind[AuditService].toInstance(mockAuditService)
    )

  val controller = instanceOf[DateOfBirthController]

  val hecTaxCheckCode  = HECTaxCheckCode("ABC DEF 123")
  val hecTaxCheckCode2 = HECTaxCheckCode("ABC DEG 123")
  val dateOfBirth      = DateOfBirth(LocalDate.of(1922, 12, 1))

  val date            = TimeUtils.today().minusYears(20)
  val dateTimeChecked = TimeUtils.now()

  val taxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(DateOfBirth(date)))

  implicit val appConfig: AppConfig = instanceOf[AppConfig]
  val lockExpiresAt                 = ZonedDateTime.now().plusHours(appConfig.verificationAttemptsLockTimeHours)

  def mockMatchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HECTaxCheckMatchResult]) =
    (taxCheckService
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(taxCheckMatchRequest, *)
      .returning(EitherT.fromEither(result))

  def mockIsMaxVerificationAttemptReached(hectaxCheckCode: HECTaxCheckCode)(result: Boolean) =
    (verificationService
      .maxVerificationAttemptReached(_: HECTaxCheckCode)(_: HECSession))
      .expects(hectaxCheckCode, *)
      .returning(result)

  def mockVerificationAttempt(
    matchResult: HECTaxCheckMatchResult,
    taxCheckCode: HECTaxCheckCode,
    verifier: Either[CRN, DateOfBirth]
  )(result: HECSession): Unit =
    (verificationService
      .updateVerificationAttemptCount(
        _: HECTaxCheckMatchResult,
        _: HECTaxCheckCode,
        _: Either[CRN, DateOfBirth]
      )(_: HECSession))
      .expects(matchResult, taxCheckCode, verifier, *)
      .returning(result)

  def mockSendTaxCheckResultAuditEvent(
    dateOfBirth: DateOfBirth,
    matchResult: HECTaxCheckMatchResult,
    session: HECSession,
    language: Language
  ) = {
    val taxCheckCode = matchResult.matchRequest.taxCheckCode
    val auditEvent   = TaxCheckCodeChecked(
      matchResult.status,
      TaxCheckCodeChecked.SubmittedData(
        taxCheckCode,
        Individual,
        matchResult.matchRequest.licenceType,
        Some(dateOfBirth),
        None
      ),
      session.verificationAttempts.get(taxCheckCode).exists(_.lockExpiresAt.nonEmpty),
      language,
      matchResult.status.matchFailureReason
    )

    mockSendAuditEvent(auditEvent)
  }

  def mockSendBlockedTaxCheckAuditEvent(
    dob: DateOfBirth,
    taxCheckCode: HECTaxCheckCode,
    licenceType: LicenceType,
    language: Language
  ) = {
    val auditEvent = TaxCheckCodeChecked.blocked(
      TaxCheckCodeChecked.SubmittedData(
        taxCheckCode,
        Individual,
        licenceType,
        Some(dob),
        None
      ),
      language
    )
    mockSendAuditEvent(auditEvent)
  }

  "DateOfBirthControllerSpec" when {

    "handling requests to date Of birth page" must {

      def performAction(): Future[Result] =
        controller.dateOfBirth(FakeRequest())

      "display the page" when {

        "user is entering date of birth  for the first time " in {
          val session = HECSession(UserAnswers.empty, None)
          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth, session)(mockPreviousCall)
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
                .attr("action") shouldBe routes.DateOfBirthController.dateOfBirthSubmit.url
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
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth, session)(mockPreviousCall)
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("dateOfBirth.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              doc.select(".govuk-hint").text() shouldBe messageFromMessageKey("dateOfBirth.hint")
              val dateOfBirthLabels = doc.select(".govuk-date-input__label")
              dateOfBirthLabels.select("[for=\"dateOfBirth-day\"]").text()   shouldBe "Day"
              dateOfBirthLabels.select("[for=\"dateOfBirth-month\"]").text() shouldBe "Month"
              dateOfBirthLabels.select("[for=\"dateOfBirth-year\"]").text()  shouldBe "Year"

              doc.select("#dateOfBirth-day").attr("value")   shouldBe date.getDayOfMonth.toString
              doc.select("#dateOfBirth-month").attr("value") shouldBe date.getMonthValue.toString
              doc.select("#dateOfBirth-year").attr("value")  shouldBe date.getYear.toString

              val form = doc.select("form")
              form
                .attr("action") shouldBe routes.DateOfBirthController.dateOfBirthSubmit.url
            }
          )

        }

      }

    }

    "handling submit on the date of birth page" must {

      def performAction(data: (String, String)*)(language: Language): Future[Result] =
        controller.dateOfBirthSubmit(
          FakeRequest()
            .withMethod(POST)
            .withFormUrlEncodedBody(data: _*)
            .withCookies(Cookie("PLAY_LANG", language.code))
        )

      def formData(date: LocalDate): List[(String, String)] = List(
        "dateOfBirth-day"   -> date.getDayOfMonth.toString,
        "dateOfBirth-month" -> date.getMonthValue.toString,
        "dateOfBirth-year"  -> date.getYear.toString
      )

      "show a form error" when {

        val session = HECSession(
          UserAnswers.empty
            .copy(taxCheckCode = Some(hecTaxCheckCode), licenceType = Some(LicenceType.ScrapMetalMobileCollector)),
          None
        )

        "nothing is submitted" in {
          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth, session)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction()(Language.English),
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
                  mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth, session)(
                    mockPreviousCall
                  )
                }

                checkFormErrorIsDisplayed(
                  performAction(data: _*)(Language.English),
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
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth, session)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction(formData(date): _*)(Language.English),
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
            mockJourneyServiceGetPrevious(routes.DateOfBirthController.dateOfBirth, session)(mockPreviousCall)
          }

          checkFormErrorIsDisplayed(
            performAction(formData(date): _*)(Language.English),
            messageFromMessageKey("dateOfBirth.title"),
            messageFromMessageKey("dateOfBirth.error.tooFarInPast", TimeUtils.govDisplayFormat(cutoffDate))
          )
        }

      }

      "return a technical error" when {

        "there is an error updating and getting the next endpoint" in {
          val dob                 = DateOfBirth(date)
          val taxCheckMatchResult =
            HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match)

          val answers = UserAnswers.empty.copy(
            taxCheckCode = Some(hecTaxCheckCode),
            licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
          )
          val session = HECSession(answers, None)

          val updatedSession =
            session.copy(
              userAnswers = answers.copy(dateOfBirth = Some(dob)),
              taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )

          inSequence {
            mockGetSession(session)
            mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
            mockMatchTaxCheck(taxCheckMatchRequest)(
              Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )
            mockVerificationAttempt(taxCheckMatchResult, hecTaxCheckCode, Right(dob))(updatedSession)
            mockSendTaxCheckResultAuditEvent(dob, taxCheckMatchResult, updatedSession, Language.Welsh)
            mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth, session, updatedSession)(
              Left(Error(""))
            )
          }

          assertThrows[RuntimeException](await(performAction(formData(date): _*)(Language.Welsh)))
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
          assertThrows[InconsistentSessionState](await(performAction(formData(date): _*)(Language.English)))

        }

        "there is no licence type in the session" in {

          val answers = UserAnswers.empty.copy(
            taxCheckCode = Some(hecTaxCheckCode),
            licenceType = None
          )
          val session = HECSession(answers, None)

          inSequence {
            mockGetSession(session)
          }
          assertThrows[InconsistentSessionState](await(performAction(formData(date): _*)(Language.English)))

        }

      }

      "redirect to the next page" when {

        "a valid date of birth is submitted and " when {

          def testVerificationAttempt(
            returnStatus: HECTaxCheckStatus,
            initialAttemptMap: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
            newAttemptMap: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
            dateOfBirth: DateOfBirth
          ) = {
            val answers             = UserAnswers.empty.copy(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
            )
            val session             = HECSession(answers, None, initialAttemptMap)
            val newMatchRequest     = taxCheckMatchRequest.copy(verifier = Right(dateOfBirth))
            val taxCheckMatchResult = HECTaxCheckMatchResult(newMatchRequest, dateTimeChecked, returnStatus)

            val updatedAnswers = answers.copy(dateOfBirth = Some(dateOfBirth))
            val updatedSession =
              session.copy(
                userAnswers = updatedAnswers,
                taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, returnStatus)),
                newAttemptMap
              )

            inSequence {
              mockGetSession(session)
              mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
              mockMatchTaxCheck(taxCheckMatchRequest)(
                Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, returnStatus))
              )
              mockVerificationAttempt(taxCheckMatchResult, hecTaxCheckCode, Right(dateOfBirth))(updatedSession)
              mockSendTaxCheckResultAuditEvent(dateOfBirth, taxCheckMatchResult, updatedSession, Language.English)
              mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth, session, updatedSession)(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction(formData(date): _*)(Language.English), mockNextCall)

          }

          "the verification attempt has reached maximum attempt and lock is not expired" when {

            "session remains same irrespective of status" in {
              val licenceType = LicenceType.DriverOfTaxisAndPrivateHires
              val answers     = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(licenceType),
                dateOfBirth = Some(DateOfBirth(date))
              )
              val session     = HECSession(
                answers,
                None,
                verificationAttempts = Map(
                  hecTaxCheckCode -> TaxCheckVerificationAttempts(
                    appConfig.maxVerificationAttempts,
                    Some(lockExpiresAt)
                  )
                )
              )

              val updatedSession = session

              inSequence {
                mockGetSession(session)
                mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(true)
                mockSendBlockedTaxCheckAuditEvent(DateOfBirth(date), hecTaxCheckCode, licenceType, Language.English)
                mockJourneyServiceUpdateAndNext(routes.DateOfBirthController.dateOfBirth, session, updatedSession)(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction(formData(date): _*)(Language.English), mockNextCall)
            }

          }

          "the verification attempt has reached maximum attempt and lock has expired" when {

            "verification attempt counter restarts from 1 in case of no match" in {
              testVerificationAttempt(
                NoMatch(MatchFailureReason.DateOfBirthNotMatched),
                Map(
                  hecTaxCheckCode  -> TaxCheckVerificationAttempts(3, Some(lockExpiresAt.minusHours(1))),
                  hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                Map(
                  hecTaxCheckCode  -> TaxCheckVerificationAttempts(1, None),
                  hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                DateOfBirth(date)
              )
            }
          }
          "the verification attempt is less than max attempt" in {

            testVerificationAttempt(
              Match,
              Map(
                hecTaxCheckCode    -> TaxCheckVerificationAttempts(2, None),
                hecTaxCheckCode2   -> TaxCheckVerificationAttempts(2, None)
              ),
              Map(hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)),
              DateOfBirth(date)
            )

          }

        }

      }

    }

  }

}
