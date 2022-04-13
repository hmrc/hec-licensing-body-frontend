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

package uk.gov.hmrc.heclicensingbodyfrontend.models

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked.SubmittedData
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType

sealed trait AuditEvent {

  val auditType: String

  val transactionName: String

}

object AuditEvent {

  final case class TaxCheckCodeChecked private (
    result: String,
    submittedData: SubmittedData,
    tooManyAttempts: Boolean,
    languagePreference: Language,
    failureReason: Option[MatchFailureReason],
    failureReasonDescription: Option[String]
  ) extends AuditEvent {

    val auditType: String = "TaxCheckCodeChecked"

    val transactionName: String = "tax-check-code-checked"

  }

  object TaxCheckCodeChecked {

    def apply(
      result: HECTaxCheckStatus,
      submittedData: SubmittedData,
      tooManyAttempts: Boolean,
      languagePreference: Language,
      failureReason: Option[MatchFailureReason]
    ): TaxCheckCodeChecked =
      TaxCheckCodeChecked(
        taxCheckStatusToString(result),
        submittedData,
        tooManyAttempts,
        languagePreference,
        failureReason,
        failureReason.map(failureReasonToDescription)
      )

    def blocked(
      submittedData: SubmittedData,
      languagePreference: Language
    ): TaxCheckCodeChecked =
      TaxCheckCodeChecked(
        "Blocked",
        submittedData,
        tooManyAttempts = true,
        languagePreference,
        None,
        None
      )

    private def taxCheckStatusToString(taxCheckStatus: HECTaxCheckStatus): String =
      taxCheckStatus match {
        case HECTaxCheckStatus.Match      => "Success"
        case HECTaxCheckStatus.NoMatch(_) => "Failed"
        case HECTaxCheckStatus.Expired    => "Expired"

      }

    private def failureReasonToDescription(failureReason: MatchFailureReason): String = failureReason match {
      case MatchFailureReason.TaxCheckCodeNotMatched           => "Tax Check Code not matched"
      case MatchFailureReason.EntityTypeNotMatched             => "Entity Type (Individual or Company) not matched"
      case MatchFailureReason.DateOfBirthNotMatched            => "Date of Birth not matched"
      case MatchFailureReason.CRNNotMatched                    => "Company Registration Number not matched"
      case MatchFailureReason.LicenceTypeNotMatched            => "Licence Type not matched"
      case MatchFailureReason.LicenceTypeEntityTypeNotMatched  =>
        "Licence Type and Entity Type (Individual or Company) not matched"
      case MatchFailureReason.LicenceTypeDateOfBirthNotMatched => "Licence Type and Date of Birth not matched"
      case MatchFailureReason.LicenceTypeCRNNotMatched         => "Licence Type and Company Registration Number not matched"

    }

    final case class SubmittedData(
      taxCheckCode: HECTaxCheckCode,
      entityType: EntityType,
      licenceType: LicenceType,
      dateOfBirth: Option[DateOfBirth],
      companyRegistrationNumber: Option[CRN]
    )

    implicit val submittedDataWrites: OWrites[SubmittedData] = Json.writes

    implicit val taxCheckCodeCheckedWrites: OWrites[TaxCheckCodeChecked] = Json.writes

  }

}
