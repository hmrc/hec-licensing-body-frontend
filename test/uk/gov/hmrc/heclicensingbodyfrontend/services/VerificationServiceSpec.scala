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

import java.time.{LocalDate, ZoneId, ZonedDateTime}

class VerificationServiceSpec extends ControllerSpec {

  val dateTimeChecked = TimeUtils.now()

  val hecTaxCode1 = HECTaxCheckCode("123 456 ABC")
  val hecTaxCode2 = HECTaxCheckCode("123 456 ABD")
  val hecTaxCode3 = HECTaxCheckCode("123 456 ABG")

  val date        = LocalDate.now().minusYears(30)
  val dobVerifier = Right(DateOfBirth(date))
  val crnVerifier = Left(CRN("112345"))

  val mockTimeProvider = mock[TimeProvider]

  override val overrideBindings =
    List[GuiceableModule](
      bind[TimeProvider].toInstance(mockTimeProvider)
    )
  implicit val appConfig        = instanceOf[AppConfig]

  val verificationService = new VerificationServiceImpl(mockTimeProvider)

  val zonedDateTimeNow = ZonedDateTime.of(2021, 10, 9, 12, 30, 0, 0, ZoneId.of("Europe/London"))
  val lockExpiresAt    = zonedDateTimeNow.plusHours(appConfig.verificationAttemptsLockTimeHours)

  def requestWithSessionData(s: HECSession): RequestWithSessionData[_] =
    RequestWithSessionData(FakeRequest(), s, Language.English)

  def mockTimeProviderNow(now: ZonedDateTime) =
    (mockTimeProvider.now _).expects().returning(now)

  "VerificationServiceSpec" when {

    "max verification reached " when {

      def createSession(
        sessionTaxCheckCode: HECTaxCheckCode,
        verificationAttempts: Map[HECTaxCheckCode, TaxCheckVerificationAttempts]
      ) =
        HECSession(
          UserAnswers.empty.copy(
            taxCheckCode = Some(sessionTaxCheckCode),
            licenceType = Some(LicenceType.OperatorOfPrivateHireVehicles)
          ),
          None,
          verificationAttempts
        )

      "return true" when {

        "verification attempt for a tax check code in session === max attempts and the lock expire time > current time" in {

          val session =
            createSession(
              hecTaxCode1,
              Map(
                hecTaxCode1 -> TaxCheckVerificationAttempts(appConfig.maxVerificationAttempts, Some(lockExpiresAt)),
                hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
              )
            )

          inSequence {
            mockTimeProviderNow(zonedDateTimeNow)
          }
          val result = verificationService.maxVerificationAttemptReached(hecTaxCode1)(session)
          result shouldBe true
        }

        "verification attempt for a tax check code in session > max attempts and the lock expire time > current time" in {

          val session = createSession(
            hecTaxCode1,
            Map(
              hecTaxCode1 -> TaxCheckVerificationAttempts(appConfig.maxVerificationAttempts + 1, Some(lockExpiresAt)),
              hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
            )
          )

          inSequence {
            mockTimeProviderNow(zonedDateTimeNow)
          }
          val result = verificationService.maxVerificationAttemptReached(hecTaxCode1)(session)
          result shouldBe true
        }

      }

      "return false" when {

        "tax check code in session  is not in verification attempt map" in {

          val session =
            createSession(
              hecTaxCode3,
              Map(
                hecTaxCode1 -> TaxCheckVerificationAttempts(appConfig.maxVerificationAttempts, Some(lockExpiresAt)),
                hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
              )
            )

          val result = verificationService.maxVerificationAttemptReached(hecTaxCode3)(session)
          result shouldBe false
        }

        "verification attempt for a tax check code in session < max attempts" in {

          val session =
            createSession(
              hecTaxCode1,
              Map(
                hecTaxCode1 -> TaxCheckVerificationAttempts(appConfig.maxVerificationAttempts - 1, None),
                hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
              )
            )

          val result = verificationService.maxVerificationAttemptReached(hecTaxCode1)(session)
          result shouldBe false
        }

        "verification attempt for a tax check code in session == max attempt and lock expire time < current time " in {
          val session =
            createSession(
              hecTaxCode1,
              Map(
                hecTaxCode1 -> TaxCheckVerificationAttempts(
                  appConfig.maxVerificationAttempts,
                  Some(zonedDateTimeNow.minusHours(1))
                ),
                hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
              )
            )

          inSequence {
            mockTimeProviderNow(zonedDateTimeNow)
          }
          val result = verificationService.maxVerificationAttemptReached(hecTaxCode1)(session)
          result shouldBe false
        }

      }
    }

    "update verification attempt count" when {

      def createSession(
        taxCheckCode: HECTaxCheckCode,
        verificationAttempts: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
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
        oldVerificationAttempt: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
        newVerificationAttempt: Map[HECTaxCheckCode, TaxCheckVerificationAttempts]
      ) = {
        val taxCheckMatch               = getTaxCheckMatch(verifier, status)
        val session                     = createSession(hecTaxCode1, oldVerificationAttempt, Some(taxCheckMatch))
        inSequence {
          mockTimeProviderNow(zonedDateTimeNow)
        }
        val result                      = verificationService.updateVerificationAttemptCount(taxCheckMatch, hecTaxCode1, verifier)(session)
        val updatedAnswers: UserAnswers = verifier match {
          case Left(crn)  => session.userAnswers.copy(crn = Some(crn))
          case Right(dob) => session.userAnswers.copy(dateOfBirth = Some(dob))
        }
        val expectedSession             =
          session.copy(userAnswers = updatedAnswers, verificationAttempts = newVerificationAttempt)
        result shouldBe expectedSession

      }

      def testVerificationAttemptLowerThanMaxAttempt(
        verifier: Either[CRN, DateOfBirth],
        status: HECTaxCheckStatus,
        oldVerificationAttempt: Map[HECTaxCheckCode, TaxCheckVerificationAttempts],
        newVerificationAttempt: Map[HECTaxCheckCode, TaxCheckVerificationAttempts]
      ) = {
        val taxCheckMatch = getTaxCheckMatch(verifier, status)
        val session       = createSession(hecTaxCode1, oldVerificationAttempt, Some(taxCheckMatch))

        val result                      = verificationService.updateVerificationAttemptCount(taxCheckMatch, hecTaxCode1, verifier)(session)
        val updatedAnswers: UserAnswers = verifier match {
          case Left(crn)  => session.userAnswers.copy(crn = Some(crn))
          case Right(dob) => session.userAnswers.copy(dateOfBirth = Some(dob))
        }
        val expectedSession             =
          session.copy(userAnswers = updatedAnswers, verificationAttempts = newVerificationAttempt)
        result shouldBe expectedSession

      }

      "verification attempt map is empty" when {

        "session has date Of birth" when {

          "Tax Check No Match found, add new map in the session with value 1" in {
            testVerificationAttemptLowerThanMaxAttempt(
              dobVerifier,
              NoMatch,
              Map.empty,
              Map(hecTaxCode1 -> TaxCheckVerificationAttempts(1, None))
            )
          }

          "Tax Check Match found, no change in attempt verification map" in {
            testVerificationAttemptLowerThanMaxAttempt(dobVerifier, Match, Map.empty, Map.empty)
          }

          "Tax Check Match found but expired, no change in attempt verification map" in {
            testVerificationAttemptLowerThanMaxAttempt(dobVerifier, Expired, Map.empty, Map.empty)
          }
        }

        "session has crn " when {

          "Tax Check No Match found, new map in the session with value 1" in {
            testVerificationAttemptLowerThanMaxAttempt(
              crnVerifier,
              NoMatch,
              Map.empty,
              Map(hecTaxCode1 -> TaxCheckVerificationAttempts(1, None))
            )
          }

          "Tax Check Match found, no change in attempt verification map" in {
            testVerificationAttemptLowerThanMaxAttempt(crnVerifier, Match, Map.empty, Map.empty)
          }

          "Tax Check Match found but expired, no change in attempt verification map" in {
            testVerificationAttemptLowerThanMaxAttempt(crnVerifier, Expired, Map.empty, Map.empty)
          }

        }
      }

      "verification attempt map is not empty and value is less than max attempt" when {

        "session has date Of birth" when {

          "Tax Check No Match found" when {

            "difference between the max attempt and the tax check code attempt is more than 1, increment only the count " in {
              testVerificationAttemptLowerThanMaxAttempt(
                dobVerifier,
                NoMatch,
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(1, None),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(2, None),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                )
              )
            }

            "the verification attempt is just one step away from the max attempt, both count and lock expire time is updated " in {
              testVerificationAttempt(
                dobVerifier,
                NoMatch,
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(2, None),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(
                    3,
                    Some(lockExpiresAt)
                  ),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                )
              )
            }

          }

          "Tax Check Match found, remove that tax check code from map" in {
            testVerificationAttemptLowerThanMaxAttempt(
              dobVerifier,
              Match,
              Map(
                hecTaxCode1   -> TaxCheckVerificationAttempts(1, None),
                hecTaxCode2   -> TaxCheckVerificationAttempts(2, None)
              ),
              Map(hecTaxCode2 -> TaxCheckVerificationAttempts(2, None))
            )
          }

          "Tax Check Match found but expired, remove that tax check code from map" in {
            testVerificationAttemptLowerThanMaxAttempt(
              dobVerifier,
              Expired,
              Map(
                hecTaxCode1   -> TaxCheckVerificationAttempts(1, None),
                hecTaxCode2   -> TaxCheckVerificationAttempts(2, None)
              ),
              Map(hecTaxCode2 -> TaxCheckVerificationAttempts(2, None))
            )
          }
        }

        "session has crn " when {

          "Tax Check No Match found" when {

            "difference between the max attempt and the tax check code attempt is more than 1, increment only the count" in {
              testVerificationAttemptLowerThanMaxAttempt(
                crnVerifier,
                NoMatch,
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(1, None),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(2, None),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                )
              )
            }

            "difference between the max attempt and the tax check code attempt is 1, increment both the count and lock expire time" in {
              testVerificationAttempt(
                crnVerifier,
                NoMatch,
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(2, None),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                ),
                Map(
                  hecTaxCode1 -> TaxCheckVerificationAttempts(3, Some(lockExpiresAt)),
                  hecTaxCode2 -> TaxCheckVerificationAttempts(2, None)
                )
              )
            }

          }

          "Tax Check Match found, remove that tax check from the map" in {
            testVerificationAttemptLowerThanMaxAttempt(
              crnVerifier,
              Match,
              Map(
                hecTaxCode1   -> TaxCheckVerificationAttempts(1, None),
                hecTaxCode2   -> TaxCheckVerificationAttempts(2, None)
              ),
              Map(hecTaxCode2 -> TaxCheckVerificationAttempts(2, None))
            )
          }

          "Tax Check Match found but expired, remove that tax check from the map" in {
            testVerificationAttemptLowerThanMaxAttempt(
              crnVerifier,
              Expired,
              Map(
                hecTaxCode1   -> TaxCheckVerificationAttempts(1, None),
                hecTaxCode2   -> TaxCheckVerificationAttempts(2, None)
              ),
              Map(hecTaxCode2 -> TaxCheckVerificationAttempts(2, None))
            )
          }

        }
      }

    }

  }

}
