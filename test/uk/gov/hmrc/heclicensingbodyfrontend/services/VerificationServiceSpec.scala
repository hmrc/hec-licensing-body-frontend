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

package uk.gov.hmrc.heclicensingbodyfrontend.services

import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test.FakeRequest
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.ControllerSpec
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.{Expired, Match, NoMatch}
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models._
import uk.gov.hmrc.heclicensingbodyfrontend.util.{TimeProvider, TimeUtils}

import java.time.{LocalDate, ZonedDateTime}

class VerificationServiceSpec extends ControllerSpec {

  implicit val appConfig = instanceOf[AppConfig]
  val dateTimeChecked    = TimeUtils.now()

  val hecTaxCode1 = HECTaxCheckCode("123 456 ABC")
  val hecTaxCode2 = HECTaxCheckCode("123 456 ABD")
  val hecTaxCode3 = HECTaxCheckCode("123 456 ABG")

  val date        = LocalDate.now().minusYears(30)
  val dobVerifier = Right(DateOfBirth(date))
  val crnVerifier = Left(CRN("112345"))

  val timeProvider = mock[TimeProvider]

  override val overrideBindings =
    List[GuiceableModule](
      bind[TimeProvider].toInstance(timeProvider)
    )

  val verificationService = new VerificationServiceImpl(timeProvider)

  val lockExpiresAt = ZonedDateTime.now.plusHours(appConfig.lockHours)

  def requestWithSessionData(s: HECSession): RequestWithSessionData[_] = RequestWithSessionData(FakeRequest(), s)

  "VerificationServiceSpec" when {

    "max verification reached " when {

      def createSession(sessionTaxCheckCode: HECTaxCheckCode, verificationAttempts: Map[HECTaxCheckCode, Attempts]) =
        HECSession(
          UserAnswers.empty.copy(
            taxCheckCode = Some(sessionTaxCheckCode),
            licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
          ),
          None,
          verificationAttempts
        )

      "return true" when {

        "tax check code in session is equal to max attempts" in {

          implicit val request: RequestWithSessionData[_] = requestWithSessionData(
            createSession(
              hecTaxCode1,
              Map(
                hecTaxCode1 -> Attempts(appConfig.maxVerificationAttempts, Some(lockExpiresAt)),
                hecTaxCode2 -> Attempts(2, None)
              )
            )
          )
          val result                                      = verificationService.maxVerificationAttemptReached(hecTaxCode1)
          result shouldBe true
        }

        "tax check code in session is more than max attempts" in {

          implicit val request: RequestWithSessionData[_] = requestWithSessionData(
            createSession(
              hecTaxCode1,
              Map(
                hecTaxCode1 -> Attempts(appConfig.maxVerificationAttempts + 1, Some(lockExpiresAt)),
                hecTaxCode2 -> Attempts(2, None)
              )
            )
          )
          val result                                      = verificationService.maxVerificationAttemptReached(hecTaxCode1)
          result shouldBe true
        }

      }

      "return false" when {

        "tax check code is not in verification attempt map" in {

          implicit val request: RequestWithSessionData[_] = requestWithSessionData(
            createSession(
              hecTaxCode3,
              Map(
                hecTaxCode1 -> Attempts(appConfig.maxVerificationAttempts, Some(lockExpiresAt)),
                hecTaxCode2 -> Attempts(2, None)
              )
            )
          )
          val result                                      = verificationService.maxVerificationAttemptReached(hecTaxCode3)
          result shouldBe false
        }

        "tax check code in session is less than max attempts" in {

          implicit val request: RequestWithSessionData[_] = requestWithSessionData(
            createSession(
              hecTaxCode1,
              Map(
                hecTaxCode1 -> Attempts(appConfig.maxVerificationAttempts - 1, None),
                hecTaxCode2 -> Attempts(2, None)
              )
            )
          )
          val result                                      = verificationService.maxVerificationAttemptReached(hecTaxCode1)
          result shouldBe false
        }

      }
    }

    "update verification attempt count" when {

      def createSession(
        taxCheckCode: HECTaxCheckCode,
        verificationAttempts: Map[HECTaxCheckCode, Attempts],
        taxCheckMatch: Option[HECTaxCheckMatchResult]
      )                                                                                   = HECSession(
        UserAnswers.empty.copy(
          taxCheckCode = Some(taxCheckCode),
          licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
        ),
        taxCheckMatch,
        verificationAttempts
      )
      def getTaxCheckMatch(verifier: Either[CRN, DateOfBirth], status: HECTaxCheckStatus) = HECTaxCheckMatchResult(
        HECTaxCheckMatchRequest(hecTaxCode1, LicenceType.OperatorOfPrivateHireVehicles, verifier),
        dateTimeChecked,
        status
      )

      def testVerificationAttempt(
        verifier: Either[CRN, DateOfBirth],
        status: HECTaxCheckStatus,
        oldVerificationAttempt: Map[HECTaxCheckCode, Attempts],
        newVerificationAttempt: Map[HECTaxCheckCode, Attempts]
      ) = {
        val taxCheckMatch                               = getTaxCheckMatch(verifier, status)
        val session                                     = createSession(hecTaxCode1, oldVerificationAttempt, Some(taxCheckMatch))
        implicit val request: RequestWithSessionData[_] =
          requestWithSessionData(session)
        val result                                      = verificationService.updateVerificationAttemptCount(taxCheckMatch, hecTaxCode1, verifier)
        val updatedAnswers: UserAnswers                 = verifier match {
          case Left(crn)  => session.userAnswers.copy(crn = Some(crn))
          case Right(dob) => session.userAnswers.copy(dateOfBirth = Some(dob))
        }
        val expectedSession                             =
          session.copy(userAnswers = updatedAnswers, verificationAttempts = newVerificationAttempt)
        result shouldBe expectedSession

      }

      "verification attempt map is empty" when {

        "session has date Of birth" when {

          "Tax Check No Match found, add new map in the session with value 1" in {
            testVerificationAttempt(dobVerifier, NoMatch, Map.empty, Map(hecTaxCode1 -> Attempts(1, None)))
          }

          "Tax Check Match found, no change in attempt verification map" in {
            testVerificationAttempt(dobVerifier, Match, Map.empty, Map.empty)
          }

          "Tax Check Match found but expired, no change in attempt verification map" in {
            testVerificationAttempt(dobVerifier, Expired, Map.empty, Map.empty)
          }
        }

        "session has crn " when {

          "Tax Check No Match found, new map in the session with value 1" in {
            testVerificationAttempt(crnVerifier, NoMatch, Map.empty, Map(hecTaxCode1 -> Attempts(1, None)))
          }

          "Tax Check Match found, no change in attempt verification map" in {
            testVerificationAttempt(crnVerifier, Match, Map.empty, Map.empty)
          }

          "Tax Check Match found but expired, no change in attempt verification map" in {
            testVerificationAttempt(crnVerifier, Expired, Map.empty, Map.empty)
          }

        }
      }

      "verification attempt map is not empty and value is less than max attempt" when {

        "session has date Of birth" when {

          "Tax Check No Match found, increment value for the no match tax check code" in {
            testVerificationAttempt(
              dobVerifier,
              NoMatch,
              Map(hecTaxCode1 -> Attempts(1, None), hecTaxCode2 -> Attempts(2, None)),
              Map(hecTaxCode1 -> Attempts(2, None), hecTaxCode2 -> Attempts(2, None))
            )
          }

          "Tax Check Match found, remove that tax check code from map" in {
            testVerificationAttempt(
              dobVerifier,
              Match,
              Map(hecTaxCode1 -> Attempts(1, None), hecTaxCode2 -> Attempts(2, None)),
              Map(hecTaxCode2 -> Attempts(2, None))
            )
          }

          "Tax Check Match found but expired, remove that tax check code from map" in {
            testVerificationAttempt(
              dobVerifier,
              Expired,
              Map(hecTaxCode1 -> Attempts(1, None), hecTaxCode2 -> Attempts(2, None)),
              Map(hecTaxCode2 -> Attempts(2, None))
            )
          }
        }

        "session has crn " when {

          "Tax Check No Match found, increment the attempt value in map" in {
            testVerificationAttempt(
              crnVerifier,
              NoMatch,
              Map(hecTaxCode1 -> Attempts(1, None), hecTaxCode2 -> Attempts(2, None)),
              Map(hecTaxCode1 -> Attempts(2, None), hecTaxCode2 -> Attempts(2, None))
            )
          }

          "Tax Check Match found, remove that tax check from the map" in {
            testVerificationAttempt(
              crnVerifier,
              Match,
              Map(hecTaxCode1 -> Attempts(1, None), hecTaxCode2 -> Attempts(2, None)),
              Map(hecTaxCode2 -> Attempts(2, None))
            )
          }

          "Tax Check Match found but expired, remove that tax check from the map" in {
            testVerificationAttempt(
              crnVerifier,
              Expired,
              Map(hecTaxCode1 -> Attempts(1, None), hecTaxCode2 -> Attempts(2, None)),
              Map(hecTaxCode2 -> Attempts(2, None))
            )
          }

        }
      }

    }

  }

}
