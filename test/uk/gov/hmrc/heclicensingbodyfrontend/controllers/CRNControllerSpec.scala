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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType.OperatorOfPrivateHireVehicles
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Attempts, DateOfBirth, Error, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckStatus, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.{HECTaxMatchService, JourneyService, VerificationService}
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
    with JourneyServiceSupport {

  val taxCheckService     = mock[HECTaxMatchService]
  val verificationService = mock[VerificationService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore),
      bind[JourneyService].toInstance(mockJourneyService),
      bind[HECTaxMatchService].toInstance(taxCheckService),
      bind[VerificationService].toInstance(verificationService)
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
  val lockExpiresAt      = ZonedDateTime.now().plusHours(appConfig.lockHours)

  def mockMatchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HECTaxCheckMatchResult]) =
    (taxCheckService
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(taxCheckMatchRequest, *)
      .returning(EitherT.fromEither(result))

  def mockIsMaxVerificationAttemptReached(hectaxCheckCode: HECTaxCheckCode)(result: Boolean) =
    (verificationService
      .maxVerificationAttemptReached(_: HECTaxCheckCode)(_: RequestWithSessionData[_]))
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
      )(_: RequestWithSessionData[_]))
      .expects(matchResult, taxCheckCode, verifier, *)
      .returning(result)

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
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("crn.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              val button = doc.select("form")
              button.attr("action") shouldBe routes.CRNController.companyRegistrationNumberSubmit().url

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
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), session)(
              mockPreviousCall
            )
          }

          checkPageIsDisplayed(
            performAction(),
            messageFromMessageKey("crn.title"),
            { doc =>
              doc.select("#back").attr("href") shouldBe mockPreviousCall.url

              val button = doc.select("form")
              button.attr("action") shouldBe routes.CRNController.companyRegistrationNumberSubmit().url

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

      def performAction(data: (String, String)*): Future[Result] =
        controller.companyRegistrationNumberSubmit(FakeRequest().withFormUrlEncodedBody(data: _*))

      behave like sessionDataActionBehaviour(() => performAction())

      val currentSession = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(hecTaxCheckCode)), None)

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
            mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction(),
            messageFromMessageKey("crn.title"),
            messageFromMessageKey("crn.error.required")
          )
        }

        "the submitted value is too long" in {
          inSequence {
            mockGetSession(currentSession)
            mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("crn" -> "1234567890"),
            messageFromMessageKey("crn.title"),
            messageFromMessageKey("crn.error.crnInvalid")
          )
        }

        "the submitted value is too short" in {
          inSequence {
            mockGetSession(currentSession)
            mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
            mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), currentSession)(
              mockPreviousCall
            )
          }

          checkFormErrorIsDisplayed(
            performAction("crn" -> "12345"),
            messageFromMessageKey("crn.title"),
            messageFromMessageKey("crn.error.crnInvalid")
          )
        }

        "the submitted value contains characters which are not letters or digits" in {

          nonAlphaNumCRN.foreach { crn =>
            withClue(s"For CRN $crn: ") {
              inSequence {
                mockGetSession(currentSession)
                mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
                mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), currentSession)(
                  mockPreviousCall
                )
              }

              checkFormErrorIsDisplayed(
                performAction("crn" -> crn.value),
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
                mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
                mockJourneyServiceGetPrevious(routes.CRNController.companyRegistrationNumber(), session)(
                  mockPreviousCall
                )
              }

              checkFormErrorIsDisplayed(
                performAction("crn" -> crn.value),
                messageFromMessageKey("crn.title"),
                messageFromMessageKey("crn.error.crnInvalid")
              )

            }
          }
        }
      }

      "return an InternalServerError" when {

        "there is an error updating and getting the next endpoint" in {

          val answers = UserAnswers.empty.copy(
            taxCheckCode = Some(hecTaxCheckCode),
            licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
          )
          val session = HECSession(answers, None)

          val updatedSession =
            session.copy(
              userAnswers = answers.copy(crn = Some(validCRN(0))),
              taxCheckMatch = Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )

          inSequence {
            mockGetSession(session)
            mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(false)
            mockMatchTaxCheck(taxCheckMatchRequest)(
              Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )
            mockVerificationAttempt(
              HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match),
              hecTaxCheckCode,
              Left(validCRN(0))
            )(updatedSession)
            mockJourneyServiceUpdateAndNext(
              routes.CRNController.companyRegistrationNumber(),
              session,
              updatedSession
            )(
              Left(Error(""))
            )
          }

          status(performAction("crn" -> validCRN(0).value)) shouldBe INTERNAL_SERVER_ERROR
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

          status(performAction("crn" -> validCRN(0).value)) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "redirect to the next page" when {

        "a valid CRN is submitted and " when {

          def testVerificationAttempt(
            returnStatus: HECTaxCheckStatus,
            initialAttemptMap: Map[HECTaxCheckCode, Attempts],
            newAttemptMap: Map[HECTaxCheckCode, Attempts],
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

              mockJourneyServiceUpdateAndNext(
                routes.CRNController.companyRegistrationNumber(),
                session,
                updatedSession
              )(
                Right(mockNextCall)
              )
            }

            checkIsRedirect(performAction("crn" -> crn.value), mockNextCall)
          }

          def testWhenVerificationAttemptIsMax(
            initialAttemptMap: Map[HECTaxCheckCode, Attempts],
            crn: CRN
          ) = {
            val answers = UserAnswers.empty.copy(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles),
              crn = Some(crn)
            )
            val session = HECSession(answers, None, verificationAttempts = initialAttemptMap)

            inSequence {
              mockGetSession(session)
              mockIsMaxVerificationAttemptReached(hecTaxCheckCode)(true)
              mockJourneyServiceUpdateAndNext(
                routes.CRNController.companyRegistrationNumber(),
                session,
                session
              )(
                Right(mockNextCall)
              )
            }
            checkIsRedirect(performAction("crn" -> crn.value), mockNextCall)
          }

          "the verification attempt has reached maximum attempt" when {

            "session remains same irrespective of status" in {
              testWhenVerificationAttemptIsMax(
                Map(
                  hecTaxCheckCode  -> Attempts(appConfig.maxVerificationAttempts, Some(lockExpiresAt)),
                  hecTaxCheckCode2 -> Attempts(2, None)
                ),
                CRN("1123456")
              )
            }
          }

          "the verification attempt is less than max attempt" in {

            testVerificationAttempt(
              NoMatch,
              Map(hecTaxCheckCode -> Attempts(2, None), hecTaxCheckCode2                -> Attempts(2, None)),
              Map(hecTaxCheckCode -> Attempts(3, Some(lockExpiresAt)), hecTaxCheckCode2 -> Attempts(2, None)),
              CRN("1123456")
            )

          }

        }

      }

    }

  }

}
