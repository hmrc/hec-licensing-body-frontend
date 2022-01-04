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

import cats.implicits.catsSyntaxEq
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.NoMatch
import uk.gov.hmrc.heclicensingbodyfrontend.models._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.util.{Logging, TimeProvider}

@ImplementedBy(classOf[VerificationServiceImpl])
trait VerificationService {

  def updateVerificationAttemptCount(
    matchResult: HECTaxCheckMatchResult,
    taxCheckCode: HECTaxCheckCode,
    verifier: Either[CRN, DateOfBirth]
  )(session: HECSession): HECSession

  def maxVerificationAttemptReached(hecTaxCheckCode: HECTaxCheckCode)(session: HECSession): Boolean

}

class VerificationServiceImpl @Inject() (timeProvider: TimeProvider)(implicit appConfig: AppConfig)
    extends VerificationService
    with Logging {

  private def getCurrentAttemptsByTaxCheckCode(taxCheckCode: HECTaxCheckCode)(
    session: HECSession
  ): TaxCheckVerificationAttempts =
    session.verificationAttempts.getOrElse(taxCheckCode, TaxCheckVerificationAttempts(0, None))

  def maxVerificationAttemptReached(
    hecTaxCheckCode: HECTaxCheckCode
  )(session: HECSession): Boolean =
    session.verificationAttempts
      .get(hecTaxCheckCode)
      .exists(verificationAttempt =>
        verificationAttempt.count >= appConfig.maxVerificationAttempts && verificationAttempt.lockExpiresAt
          .exists(_.isAfter(timeProvider.now))
      )

  override def updateVerificationAttemptCount(
    taxMatch: HECTaxCheckMatchResult,
    taxCheckCode: HECTaxCheckCode,
    verifier: Either[CRN, DateOfBirth]
  )(session: HECSession): HECSession = {
    val updatedAnswers = verifier match {
      case Left(crn)  => session.userAnswers.copy(crn = Some(crn))
      case Right(dob) => session.userAnswers.copy(dateOfBirth = Some(dob))
    }

    lazy val currentVerificationAttemptMap = session.verificationAttempts
    lazy val currentAttempt                = getCurrentAttemptsByTaxCheckCode(taxCheckCode)(session)
    lazy val penultimateAttempt            = currentAttempt.count === (appConfig.maxVerificationAttempts - 1)
    lazy val maxAttempt                    = currentAttempt.count === appConfig.maxVerificationAttempts

    lazy val updateAttemptCountAndLockExpiresTime =
      currentVerificationAttemptMap + (taxCheckCode -> TaxCheckVerificationAttempts(
        currentAttempt.count + 1,
        Some(timeProvider.now.plusHours(appConfig.verificationAttemptsLockTimeHours))
      ))

    lazy val resetAttemptCount = currentVerificationAttemptMap + (taxCheckCode -> TaxCheckVerificationAttempts(1, None))

    lazy val incrementAttemptCountOnly = currentVerificationAttemptMap + (taxCheckCode -> TaxCheckVerificationAttempts(
      currentAttempt.count + 1,
      None
    ))
    lazy val removeAttemptFromSession  = currentVerificationAttemptMap - taxCheckCode

    val verificationAttempts = if (taxMatch.status === NoMatch) {
      //case when user verification attempt == max attempt
      //lock expire time should get added
      if (penultimateAttempt) updateAttemptCountAndLockExpiresTime
      //case when user is already blocked but lock has expired,
      // then user verification attempt count should be reset to 1 and lock time should get cleared
      else if (maxAttempt)
        resetAttemptCount
      else incrementAttemptCountOnly
    } else {
      removeAttemptFromSession
    }
    session.copy(
      userAnswers = updatedAnswers,
      Some(taxMatch),
      verificationAttempts
    )
  }

}
