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

import cats.data.EitherT
import cats.instances.future._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.{Cookie, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.Company
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType.OperatorOfPrivateHireVehicles
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, Error, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckStatus, InconsistentSessionState, Language, MatchFailureReason, TaxCheckVerificationAttempts, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.{AuditService, AuditServiceSupport, HECTaxMatchService, JourneyService, VerificationService}
import uk.gov.hmrc.heclicensingbodyfrontend.util.StringUtils.StringOps
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.ZonedDateTime
import java.util.Locale
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CRNControllerSpec
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

  val controller       = instanceOf[CRNController]
  val hecTaxCheckCode  = HECTaxCheckCode("ABC DEF 123")
  val hecTaxCheckCode2 = HECTaxCheckCode("ABC DEG 123")
  val validCRN         =
    List(CRN("SS12345"), CRN("SS1 23 45"), CRN("SS123456"), CRN("ss123456"), CRN("11123456"), CRN("1112345"))
  val nonAlphaNumCRN   = List(CRN("$£%^&"), CRN("AA1244&"))
  val inValidCRN       =
    List(CRN("AAB3456"), CRN("12345AAA"))
  val dateTimeChecked  = TimeUtils.now()

  val taxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.OperatorOfPrivateHireVehicles, Left(validCRN(0)))

  implicit val appConfig = instanceOf[AppConfig]
  val lockExpiresAt      = ZonedDateTime.now().plusHours(appConfig.verificationAttemptsLockTimeHours)

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
    crn: CRN,
    matchResult: HECTaxCheckMatchResult,
    session: HECSession,
    language: Language
  ) = {
    val taxCheckCode = matchResult.matchRequest.taxCheckCode
    val auditEvent   = TaxCheckCodeChecked(
      matchResult.status,
      TaxCheckCodeChecked.SubmittedData(
        taxCheckCode,
        Company,
        matchResult.matchRequest.licenceType,
        None,
        Some(crn)
      ),
      session.verificationAttempts.get(taxCheckCode).exists(_.lockExpiresAt.nonEmpty),
      language,
      matchResult.status.matchFailureReason
    )
    mockSendAuditEvent(auditEvent)
  }

  def mockSendBlockedTaxCheckAuditEvent(
    crn: CRN,
    taxCheckCode: HECTaxCheckCode,
    licenceType: LicenceType,
    language: Language
  ) = {
    val auditEvent = TaxCheckCodeChecked.blocked(
      TaxCheckCodeChecked.SubmittedData(
        taxCheckCode,
        Company,
        licenceType,
        None,
        Some(crn)
      ),
      language
    )
    mockSendAuditEvent(auditEvent)
  }

  "CRNControllerSpec" when {

    "handling requests to display the company registration number  page" must {

      def performAction(): Future[Result] = controller.companyRegistrationNumber(FakeRequest())

      behave like sessionDataActionBehaviour(performAction)

      "display the page" when {

        "user is about to enter the CRN for the first time " in {
          val session =
            HECSession(
              UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(OperatorOfPrivateHireVehicles)
              ),
              None
            )

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("crn.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              val button = doc.select("form")
              button.attr("action") shouldBe routes.CRNController.companyRegistrationNumberSubmit.url

              val link = doc.select("p > .govuk-link")
              link.text should startWith(messageFromMessageKey("crn.link"))

            }
          )
        }

        "CRN is already in session as user click on back " in {
          val session =
            HECSession(
              UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(OperatorOfPrivateHireVehicles),
                crn = Some(validCRN(0))
              ),
              None
            )

          inSequence {
            mockGetSession(session)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("crn.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              val button = doc.select("form")
              button.attr("action") shouldBe routes.CRNController.companyRegistrationNumberSubmit.url

              val link = doc.select("p > .govuk-link")
              link.text should startWith(messageFromMessageKey("crn.link"))

              val input = doc.select(".govuk-input")
              input.attr("value") shouldBe validCRN(0).value

            }
          )
        }

      }

    }

    "handling submit on the CRN page" must {

      def performAction(data: (String, String)*)(language: Language): Future[Result] =
        controller.companyRegistrationNumberSubmit(
          FakeRequest().withFormUrlEncodedBody(data: _*).withCookies(Cookie("PLAY_LANG", language.code))
        )

      behave like sessionDataActionBehaviour(() => performAction()(Language.English))

      val currentSession = HECSession(
        UserAnswers.empty
          .copy(taxCheckCode = Some(hecTaxCheckCode), licenceType = Some(LicenceType.ScrapMetalDealerSite)),
        None
      )

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction()(Language.English),
            messageFromMessageKey("crn.title"),
            messageFromMessageKey("crn.error.required")
          )
        }

        "the submitted value is too long" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("crn" -> "1234567890")(Language.English),
            messageFromMessageKey("crn.title"),
            messageFromMessageKey("crn.error.crnInvalid")
          )
        }

        "the submitted value is too short" in {
          inSequence {
            mockGetSession(currentSession)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("crn" -> "12345")(Language.English),
            messageFromMessageKey("crn.title"),
            messageFromMessageKey("crn.error.crnInvalid")
          )
        }

        "the submitted value contains characters which are not letters or digits" in {

          nonAlphaNumCRN.foreach { crn =>
            withClue(s"For CRN $crn: ") {
              inSequence {
                mockGetSession(currentSession)
                mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, currentSession)(
                  mockPreviousCall
                )
              }

              checkFormErrorIsDisplayed(
                performAction("crn" -> crn.value)(Language.English),
                messageFromMessageKey("crn.title"),
                messageFromMessageKey("crn.error.nonAlphanumericChars")
              )

            }

          }

        }

        "the submitted value contains alphanumeric characters but in wrong format" in {
          inValidCRN.foreach { crn =>
            withClue(s"For CRN $crn: ") {
              val session = HECSession(
                UserAnswers.empty.copy(
                  taxCheckCode = Some(hecTaxCheckCode),
                  licenceType = Some(OperatorOfPrivateHireVehicles),
                  crn = Some(crn)
                ),
                None
              )
              inSequence {
                mockGetSession(session)
                mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber, session)(
                  mockPreviousCall
                )
              }

              checkFormErrorIsDisplayed(
                performAction("crn" -> crn.value)(Language.English),
                messageFromMessageKey("crn.title"),
                messageFromMessageKey("crn.error.crnInvalid")
              )

            }
          }
        }
      }

      "return a technical error" when {

        "there is an error updating and getting the next endpoint" in {

          val crn                 = validCRN(0)
          val taxCheckMatchResult = HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match)
          val answers             = UserAnswers.empty.copy(
            taxCheckCode = Some(hecTaxCheckCode),
            licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
          )
          val session             = HECSession(answers, None)

          val updatedSession =
            session.copy(
              userAnswers = answers.copy(crn = Some(crn)),
              taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )

          inSequence {
            mockGetSession(session)
            mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
            mockMatchTaxCheck(taxCheckMatchRequest)(
              Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )
            mockVerificationAttempt(taxCheckMatchResult, hecTaxCheckCode, Left(validCRN(0)))(updatedSession)
            mockSendTaxCheckResultAuditEvent(crn, taxCheckMatchResult, updatedSession, Language.Welsh)
            mockJourneyServiceUpdateAndNext(
              routes.CRNController.companyRegistrationNumber,
              session,
              updatedSession
            )(
              Left(Error(""))
            )
          }

          assertThrows[RuntimeException](await(performAction("crn" -> validCRN(0).value)(Language.Welsh)))
        }

        "there is no taxCheckCode in the session" in {

          val answers = UserAnswers.empty.copy(
            taxCheckCode = None,
            licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
          )
          val session = HECSession(answers, None)

          inSequence {
            mockGetSession(session)
          }
          assertThrows[InconsistentSessionState](await(performAction("crn" -> validCRN(0).value)(Language.English)))
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
          assertThrows[InconsistentSessionState](await(performAction("crn" -> validCRN(0).value)(Language.English)))
        }

      }

      "redirect to the next page" when {

        "a valid CRN is submitted and " when {

          def testVerificationAttempt(
            returnStatus: HECTaxCheckStatus,
            initialAttemptMap: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
            newAttemptMap: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
            crn: CRN
          ) = {
            val formattedCrn        = CRN(crn.value.removeWhitespace.toUpperCase(Locale.UK))
            val newMatchRequest     = taxCheckMatchRequest.copy(verifier = Left(formattedCrn))
            val taxCheckMatchResult = HECTaxCheckMatchResult(newMatchRequest, dateTimeChecked, returnStatus)

            val answers = UserAnswers.empty.copy(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
            )
            val session = HECSession(answers, None, verificationAttempts = initialAttemptMap)

            val updatedAnswers = answers.copy(crn = Some(formattedCrn))
            val updatedSession =
              session.copy(
                userAnswers = updatedAnswers,
                taxCheckMatch = Some(
                  taxCheckMatchResult
                ),
                newAttemptMap
              )
            inSequence {
              mockGetSession(session)
              mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
              mockMatchTaxCheck(newMatchRequest)(
                Right(HECTaxCheckMatchResult(newMatchRequest, dateTimeChecked, returnStatus))
              )
              mockVerificationAttempt(taxCheckMatchResult, hecTaxCheckCode, Left(crn))(updatedSession)
              mockSendTaxCheckResultAuditEvent(crn, taxCheckMatchResult, updatedSession, Language.English)
              mockJourneyServiceUpdateAndNext(
                routes.CRNController.companyRegistrationNumber,
                session,
                updatedSession
              )(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("crn" -> crn.value)(Language.English), mockNextCall)
          }

          def testWhenVerificationAttemptIsMax(
            initialAttemptMap: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
            crn: CRN
          ) = {
            val licenceType = LicenceType.OperatorOfPrivateHireVehicles
            val answers     = UserAnswers.empty.copy(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(licenceType),
              crn = Some(crn)
            )
            val session     = HECSession(answers, None, verificationAttempts = initialAttemptMap)

            inSequence {
              mockGetSession(session)
              mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(true)
              mockSendBlockedTaxCheckAuditEvent(
                crn,
                hecTaxCheckCode,
                licenceType,
                Language.English
              )
              mockJourneyServiceUpdateAndNext(
                routes.CRNController.companyRegistrationNumber,
                session,
                session
              )(
                Right(mockNextCall)
              )
            }
            checkIsRedirect(performAction("crn" -> crn.value)(Language.English), mockNextCall)
          }

          "the verification attempt has reached maximum attempt and lock is not expired" when {

            "session remains same irrespective of status" in {
              testWhenVerificationAttemptIsMax(
                Map(
                  hecTaxCheckCode  -> TaxCheckVerificationAttempts(
                    appConfig.maxVerificationAttempts,
                    Some(lockExpiresAt)
                  ),
                  hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                CRN("1123456")
              )
            }
          }

          "the verification attempt has reached maximum attempt and lock has expired" when {

            "verification attempt counter restarts from 1 in case of no match" in {
              testVerificationAttempt(
                NoMatch(MatchFailureReason.EntityTypeNotMatched),
                Map(
                  hecTaxCheckCode  -> TaxCheckVerificationAttempts(3, Some(lockExpiresAt.minusHours(1))),
                  hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                Map(
                  hecTaxCheckCode  -> TaxCheckVerificationAttempts(1, None),
                  hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                CRN("1123456")
              )
            }
          }

          "the verification attempt is less than max attempt" in {

            testVerificationAttempt(
              NoMatch(MatchFailureReason.LicenceTypeCRNNotMatched),
              Map(
                hecTaxCheckCode  -> TaxCheckVerificationAttempts(2, None),
                hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
              ),
              Map(
                hecTaxCheckCode  -> TaxCheckVerificationAttempts(3, Some(lockExpiresAt)),
                hecTaxCheckCode2 -> TaxCheckVerificationAttempts(2, None)
              ),
              CRN("1123456")
            )

          }

        }

      }

    }

  }

}
