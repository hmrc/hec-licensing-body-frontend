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

import cats.implicits.catsSyntaxEq
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.NoMatch
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, HECSession, HECTaxCheckCode, HECTaxCheckMatchResult}
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging

@ImplementedBy(classOf[VerificationServiceImpl])
trait VerificationService {

  def updateVerificationAttemptCount(
    matchResult: HECTaxCheckMatchResult,
    taxCheckCode: HECTaxCheckCode,
    verifier: Either[CRN, DateOfBirth]
  )(implicit requestWithSessionData: RequestWithSessionData[_]): HECSession

  def maxVerificationAttemptReached(hecTaxCheckCode: HECTaxCheckCode)(implicit
    requestWithSessionData: RequestWithSessionData[_]
  ): Boolean

}

class VerificationServiceImpl @Inject() ()(implicit appConfig: AppConfig) extends VerificationService with Logging {

  def maxVerificationAttemptReached(
    hecTaxCheckCode: HECTaxCheckCode
  )(implicit requestWithSessionData: RequestWithSessionData[_]): Boolean =
    requestWithSessionData.sessionData.verificationAttempts
      .get(hecTaxCheckCode)
      .exists(_ >= appConfig.maxVerificationAttempts)

  override def updateVerificationAttemptCount(
    taxMatch: HECTaxCheckMatchResult,
    taxCheckCode: HECTaxCheckCode,
    verifier: Either[CRN, DateOfBirth]
  )(implicit request: RequestWithSessionData[_]): HECSession = {
    val updatedAnswers = verifier match {
      case Left(crn)  => request.sessionData.userAnswers.copy(crn = Some(crn))
      case Right(dob) => request.sessionData.userAnswers.copy(dateOfBirth = Some(dob))
    }

    val currentVerificationAttemptMap = request.sessionData.verificationAttempts
    val currentVerificationAttempt    = currentVerificationAttemptMap.getOrElse(taxCheckCode, 0)
    val verificationAttempts          = if (taxMatch.status === NoMatch) {
      currentVerificationAttemptMap + (taxCheckCode -> (currentVerificationAttempt + 1))
    } else {
      currentVerificationAttemptMap - taxCheckCode
    }
    request.sessionData.copy(
      userAnswers = updatedAnswers,
      Some(taxMatch),
      verificationAttempts
    )
  }

}
