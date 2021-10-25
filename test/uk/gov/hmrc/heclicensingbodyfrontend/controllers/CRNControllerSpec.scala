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
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType.OperatorOfPrivateHireVehicles
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckStatus, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.util.StringUtils.StringOps
import uk.gov.hmrc.heclicensingbodyfrontend.services.{HECTaxMatchService, JourneyService}
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.util.Locale
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CRNControllerSpec
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

  val controller           = instanceOf[CRNController]
  val hecTaxCheckCode      = HECTaxCheckCode("ABC DEF 123")
  val hecTaxCheckCode2     = HECTaxCheckCode("ABC DEG 123")
  val validCRN             =
    List(CRN("SS12345"), CRN("SS1 23 45"), CRN("SS123456"), CRN("ss123456"), CRN("11123456"), CRN("1112345"))
  val nonAlphaNumCRN       = List(CRN("$£%^&"), CRN("AA1244&"))
  val inValidCRN           =
    List(CRN("AAB3456"), CRN("12345AAA"))
  val dateTimeChecked      = TimeUtils.now()
  val taxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.OperatorOfPrivateHireVehicles, Left(validCRN(0)))

  implicit val appConfig = instanceOf[AppConfig]

  def mockMatchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HECTaxCheckMatchResult]) =
    (taxCheckService
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(taxCheckMatchRequest, *)
      .returning(EitherT.fromEither(result))

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

      val currentSession = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode("XNFFGBDD6"))), None)

      "show a form error" when {

        "nothing has been submitted" in {
          inSequence {
            mockGetSession(currentSession)
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
            mockMatchTaxCheck(taxCheckMatchRequest)(
              Right(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
            )
            mockJourneyServiceUpdateAndNext(routes.CRNController.companyRegistrationNumber(), session, updatedSession)(
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
            initialAttemptMap: Map[HECTaxCheckCode, Int],
            newAttemptMap: Map[HECTaxCheckCode, Int],
            crn: CRN
          ) = {
            val formattedCrn    = CRN(crn.value.removeWhitespace.toUpperCase(Locale.UK))
            val newMatchRequest = taxCheckMatchRequest.copy(verifier = Left(formattedCrn))

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
                  HECTaxCheckMatchResult(
                    newMatchRequest,
                    dateTimeChecked,
                    returnStatus
                  )
                ),
                newAttemptMap
              )
            inSequence {
              mockGetSession(session)
              mockMatchTaxCheck(newMatchRequest)(
                Right(HECTaxCheckMatchResult(newMatchRequest, dateTimeChecked, returnStatus))
              )
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

          "the verification attempt is empty" when {

            "There is a match found, verification attempt remains empty" in {
              validCRN.foreach { crn =>
                withClue(s" For CRN : $crn") {
                  testVerificationAttempt(Match, Map.empty, Map.empty, crn)
                }

              }
            }

            "There is a match found but expired, verification attempt remains empty" in {
              validCRN.foreach { crn =>
                withClue(s" For CRN : $crn") {
                  testVerificationAttempt(Expired, Map.empty, Map.empty, crn)
                }

              }
            }

            "There is a No Match found, verification attempt incremented by 1 for that tax check code " in {
              testVerificationAttempt(NoMatch, Map.empty, Map(hecTaxCheckCode -> 1), CRN("1123456"))

            }

          }

          "the verification attempt is not initially empty" when {

            "two tax check codes in session, both with attempt 2, the one with no match is incremented to 3" in {
              testVerificationAttempt(
                NoMatch,
                Map(hecTaxCheckCode -> 2, hecTaxCheckCode2 -> 2),
                Map(hecTaxCheckCode -> 3, hecTaxCheckCode2 -> 2),
                CRN("1123456")
              )
            }

            "two tax check codes in session, both with attempt 2, the one with match is removed from the verification map" in {
              testVerificationAttempt(
                Match,
                Map(hecTaxCheckCode  -> 2, hecTaxCheckCode2 -> 2),
                Map(hecTaxCheckCode2 -> 2),
                CRN("1123456")
              )
            }

            "two tax check codes in session, both with attempt 2, the one with  Expired is removed from the verification map" in {
              testVerificationAttempt(
                Expired,
                Map(hecTaxCheckCode  -> 2, hecTaxCheckCode2 -> 2),
                Map(hecTaxCheckCode2 -> 2),
                CRN("1123456")
              )
            }

            "tax check code in session has reached the max verification  attempt, got to next page with unaffected session even if it's a Match" in {
              val answers = UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
              )
              val session = HECSession(
                answers,
                None,
                verificationAttempts = Map(hecTaxCheckCode -> appConfig.maxVerificationAttempts)
              )

              val updatedSession = session
              inSequence {
                mockGetSession(session)
                mockJourneyServiceUpdateAndNext(
                  routes.CRNController.companyRegistrationNumber(),
                  session,
                  updatedSession
                )(
                  Right(mockNextCall)
                )
              }

              checkIsRedirect(performAction("crn" -> CRN("1111111111").value), mockNextCall)
            }
          }

        }

      }

    }

  }

}
